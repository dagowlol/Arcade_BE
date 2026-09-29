package com.exe.arcade_be.service;

import com.exe.arcade_be.dto.*;
import com.exe.arcade_be.entity.Challenge;
import com.exe.arcade_be.entity.ChallengeSession;
import com.exe.arcade_be.entity.Food;
import com.exe.arcade_be.enums.Level;
import com.exe.arcade_be.enums.SessionStatus;
import com.exe.arcade_be.repository.ChallengeRepository;
import com.exe.arcade_be.repository.ChallengeSessionRepository;
import com.exe.arcade_be.repository.FoodRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodChallengeService {

    private final FoodRepository foodRepository;
    private final ChallengeSessionRepository sessionRepository;
    private final ChallengeRepository challengeRepository;
    private final SpeechValidationService speechValidationService;
    private final ObjectMapper objectMapper;

    // Level 3 (HARD) 4-tier ladder sentence templates.
    private static final String[] L3_SINGLE_TEMPLATES = {
            "I want %s, please.", "I'd like %s, please.", "Can I have %s, please?", "Could I get %s, please?"
    };

    private static final String[] L3_TWO_HINT_TEMPLATES = {
            "Can I have %s and %s, please?", "I'd like %s and %s, please.",
            "I want %s and %s, please.", "Could I get %s and %s, please?"
    };

    private static final String[] L3_TWO_MEMORY_TEMPLATES = {
            "I need %s and %s, please!", "Can I get %s and %s, please?",
            "I'd like %s and %s, please!", "Give me %s and %s, please!"
    };

    private static final String[] L3_THREE_MEMORY_TEMPLATES = {
            "Give me %s, %s, and %s!", "I need %s, %s, and %s, please!",
            "Can I get %s, %s, and %s, please?", "I want %s, %s, and %s, please!"
    };

    // ---------- Level 2 (MEDIUM): 3 rotating puzzle types ----------
    private static final String[] L2_CHALLENGE_TYPES = { "WORD_SCRAMBLE", "FILL_BLANK", "EXTRA_WORD" };

    /**
     * Tier 1: short, everyday sentences. Every sentence carries a verb so it can be
     * blanked.
     */
    private static final String[] L2_SENTENCES_TIER1 = {
            "I want %s.", "I like %s.", "I need %s.", "Can I have %s?", "Could I get %s?"
    };

    /** Tier 2-3: richer sentence shapes including politeness words. */
    private static final String[] L2_SENTENCES_TIER2 = {
            "I want %s.", "I like %s.", "I need %s.", "Can I have %s?", "Could I get %s?",
            "I want %s, please.", "I'd like %s, please.", "I would like %s, please.",
            "Can I have %s, please?", "Could I get %s, please?", "Can I get %s, please?"
    };

    /** Tier 3: two items in one sentence. */
    private static final String[] L2_SENTENCES_TIER3_DOUBLE = {
            "I want %s and %s.", "Can I have %s and %s, please?", "I'd like %s and %s, please.",
            "Could I get %s and %s?", "Can I get %s and %s, please?", "I want %s and %s, please.",
            "Could I have %s and %s, please?"
    };

    /** Key words used as blanks / distractors. */
    private static final String[] L2_VERBS = { "want", "like", "need", "have", "get" };
    private static final String[] L2_NUMBERS = { "one", "two", "three" };

    public List<FoodDto> getAllFoods() {
        return foodRepository.findByActiveTrue()
                .stream()
                .map(this::mapFoodToDto)
                .collect(Collectors.toList());
    }

    public List<LevelDto> getLevels() {
        return List.of(
                LevelDto.builder()
                        .id(Level.EASY)
                        .name("Nghe & Chọn")
                        .tag("Cấp độ 1")
                        .description("Nghe câu tiếng Anh, chọn đúng món ăn trong các món hiển thị.")
                        .icon("🌱")
                        .difficulty("Dễ")
                        .challengeCount(0) // will be randomized 3-5
                        .build(),
                LevelDto.builder()
                        .id(Level.MEDIUM)
                        .name("Xếp Từ & Điền Từ")
                        .tag("Cấp độ 2")
                        .description("Nghe câu tiếng Anh rồi xếp từ, điền từ vào chỗ trống, hoặc tìm từ thừa.")
                        .icon("🍔")
                        .difficulty("Trung bình")
                        .challengeCount(0)
                        .build(),
                LevelDto.builder()
                        .id(Level.HARD)
                        .name("Nhớ & Gọi món")
                        .tag("Cấp độ 3")
                        .description(
                                "Nghe yêu cầu, ghi nhớ món ăn rồi chọn đúng món và số lượng trên menu, bấm đặt hàng.")
                        .icon("⭐")
                        .difficulty("Khó")
                        .challengeCount(0)
                        .build());
    }

    @Transactional
    public SessionDto startSession(StartChallengeRequest request) {
        Level level = request.getLevel() != null ? request.getLevel() : Level.EASY;
        List<Food> allFoods = foodRepository.findByActiveTrue();

        if (allFoods.isEmpty()) {
            throw new IllegalStateException("No active foods found in database.");
        }

        String sessionId = UUID.randomUUID().toString();
        Random random = new Random();
        // Level 3 (HARD) has a fixed 4-challenge ladder: easy -> hard.
        int totalChallenges = level == Level.HARD ? 4 : 3 + random.nextInt(3); // Random 3-5 challenges

        ChallengeSession session = ChallengeSession.builder()
                .id(sessionId)
                .level(level)
                .totalChallenges(totalChallenges)
                .currentChallengeIndex(0)
                .hearts(3)
                .maxHearts(3)
                .score(0)
                .status(SessionStatus.IN_PROGRESS)
                .build();

        sessionRepository.save(session);

        List<Challenge> challenges = generateChallengesForLevel(sessionId, level, totalChallenges, allFoods, random);
        challengeRepository.saveAll(challenges);

        return getSession(sessionId);
    }

    public SessionDto getSession(String sessionId) {
        ChallengeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<Challenge> challenges = challengeRepository.findBySessionIdOrderBySequenceIndexAsc(sessionId);
        List<ChallengeDto> challengeDtos = challenges.stream()
                .map(this::mapChallengeToDto)
                .collect(Collectors.toList());

        ChallengeDto currentChallengeDto = null;
        if (session.getCurrentChallengeIndex() < challengeDtos.size()
                && session.getStatus() == SessionStatus.IN_PROGRESS) {
            currentChallengeDto = challengeDtos.get(session.getCurrentChallengeIndex());
        }

        return SessionDto.builder()
                .sessionId(session.getId())
                .level(session.getLevel())
                .totalChallenges(session.getTotalChallenges())
                .currentChallengeIndex(session.getCurrentChallengeIndex())
                .hearts(session.getHearts())
                .maxHearts(session.getMaxHearts())
                .score(session.getScore())
                .status(session.getStatus())
                .challenges(challengeDtos)
                .currentChallenge(currentChallengeDto)
                .build();
    }

    private ValidationResultDto rejectedResult(ChallengeSession session, String message, String feedbackType) {
        return ValidationResultDto.builder()
                .correct(false)
                .foodCorrect(false)
                .speechCorrect(false)
                .message(message)
                .progress(session.getCurrentChallengeIndex())
                .total(session.getTotalChallenges())
                .hearts(session.getHearts())
                .sessionStatus(session.getStatus())
                .feedbackType(feedbackType)
                .build();
    }

    @Transactional
    public ValidationResultDto submitAnswer(String sessionId, SubmitAnswerRequest request) {
        ChallengeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            return rejectedResult(session, "Trò chơi đã kết thúc.", "GAME_OVER");
        }

        Challenge challenge = challengeRepository.findById(request.getChallengeId())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + request.getChallengeId()));

        // Reject stale/duplicate submissions: the challenge must belong to this
        // session,
        // must be the one currently being played, and must not be answered already.
        // Without this guard a repeated POST would advance the index and the score
        // twice.
        if (!sessionId.equals(challenge.getSessionId())
                || !challenge.getSequenceIndex().equals(session.getCurrentChallengeIndex())
                || Boolean.TRUE.equals(challenge.getCompleted())) {
            return rejectedResult(session, "Câu hỏi đã qua, hãy chọn lại nhé!", "STALE_CHALLENGE");
        }

        List<ChallengeItemDto> targetItems = deserializeItems(challenge.getItemsJson());
        List<ChallengeItemDto> selectedItems = request.getSelectedItems() != null ? request.getSelectedItems()
                : Collections.emptyList();

        String spokenText = request.getSpokenText() != null ? request.getSpokenText().trim() : "";
        String answerText = request.getAnswerText() != null ? request.getAnswerText().trim() : "";
        String challengeType = challenge.getChallengeType();

        boolean foodCorrect;
        boolean speechCorrect;
        boolean isOverallCorrect;

        if (isLanguageChallenge(challengeType)) {
            // Level 2 puzzles are pure grammar exercises: no food picking, no microphone.
            ChallengePayloadDto payload = deserializePayload(challenge.getPayloadJson());
            boolean languageCorrect = validateLanguageAnswer(challenge, payload, answerText);
            foodCorrect = true;
            speechCorrect = languageCorrect;
            isOverallCorrect = languageCorrect;
        } else {
            // 1. Validate Food Selection & Quantities
            foodCorrect = validateSelectedFoodItems(targetItems, selectedItems);

            // 2. Validate Spoken Text (only for challenge types that require speech)
            boolean speechRequired = "SPEAKING".equals(challengeType) || "ORDER".equals(challengeType)
                    || "SUPER_ORDER".equals(challengeType);
            speechCorrect = speechRequired
                    ? speechValidationService.isSpeechMatch(spokenText, challenge.getSpeechTarget())
                    : true;

            isOverallCorrect = foodCorrect && speechCorrect;
        }

        if (isOverallCorrect) {
            challenge.setCompleted(true);
            challenge.setCorrect(true);
            challengeRepository.save(challenge);

            int newIndex = session.getCurrentChallengeIndex() + 1;
            session.setCurrentChallengeIndex(newIndex);
            session.setScore(session.getScore() + 100);

            if (newIndex >= session.getTotalChallenges()) {
                session.setStatus(SessionStatus.COMPLETED);
                session.setCompletedAt(LocalDateTime.now());
                assignRandomReward(session);
            }

            sessionRepository.save(session);

            List<Challenge> remainingChallenges = challengeRepository.findBySessionIdOrderBySequenceIndexAsc(sessionId);
            ChallengeDto nextChallengeDto = null;
            if (newIndex < remainingChallenges.size() && session.getStatus() == SessionStatus.IN_PROGRESS) {
                nextChallengeDto = mapChallengeToDto(remainingChallenges.get(newIndex));
            }

            return ValidationResultDto.builder()
                    .correct(true)
                    .foodCorrect(true)
                    .speechCorrect(true)
                    .message("Tuyệt vời! Bé đã trả lời đúng! ")
                    .progress(newIndex)
                    .total(session.getTotalChallenges())
                    .hearts(session.getHearts())
                    .sessionStatus(session.getStatus())
                    .feedbackType("SUCCESS")
                    .nextChallenge(nextChallengeDto)
                    .expectedSpeech(challenge.getSpeechTarget())
                    .spokenNormalized(speechValidationService
                            .normalize(isLanguageChallenge(challengeType) ? answerText : spokenText))
                    .build();
        } else {
            // Deduct a heart
            int remainingHearts = Math.max(0, session.getHearts() - 1);
            session.setHearts(remainingHearts);

            String message;
            String feedbackType;

            if (isLanguageChallenge(challengeType)) {
                message = switch (challengeType) {
                    case "WORD_SCRAMBLE" -> "Thứ tự từ chưa đúng rồi! Nghe lại và xếp lại nhé!";
                    case "FILL_BLANK" -> "Từ này chưa hợp câu rồi! Nghe lại câu mẫu nhé!";
                    case "EXTRA_WORD" -> "Từ bạn xóa chưa phải từ thừa! Thử lại nhé!";
                    default -> "Chưa đúng rồi, thử lại nhé!";
                };
                feedbackType = "RETRY_WORD";
            } else if (!foodCorrect && !speechCorrect) {
                message = "Gần đúng rồi! Kiểm tra lại món ăn và thử nói rõ hơn nhé!";
                feedbackType = "RETRY_FOOD";
            } else if (!foodCorrect) {
                message = "Kiểm tra lại món ăn hoặc số lượng nhé!";
                feedbackType = "RETRY_FOOD";
            } else {
                message = "Chọn món đúng rồi! Hãy thử nói lại nhé!";
                feedbackType = "RETRY_SPEECH";
            }

            if (remainingHearts <= 0) {
                session.setStatus(SessionStatus.FAILED);
                message = "Bé đã cố gắng rất giỏi! Chơi lại để chiến thắng nhé! 🌟";
                feedbackType = "GAME_OVER";
            }

            sessionRepository.save(session);

            return ValidationResultDto.builder()
                    .correct(false)
                    .foodCorrect(foodCorrect)
                    .speechCorrect(speechCorrect)
                    .message(message)
                    .progress(session.getCurrentChallengeIndex())
                    .total(session.getTotalChallenges())
                    .hearts(remainingHearts)
                    .sessionStatus(session.getStatus())
                    .feedbackType(feedbackType)
                    .expectedSpeech(challenge.getSpeechTarget())
                    .spokenNormalized(speechValidationService
                            .normalize(isLanguageChallenge(challengeType) ? answerText : spokenText))
                    .build();
        }
    }

    public SessionResultDto getSessionResult(String sessionId) {
        ChallengeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        int completed = (int) challengeRepository.findBySessionIdOrderBySequenceIndexAsc(sessionId)
                .stream().filter(Challenge::getCorrect).count();

        String message = session.getStatus() == SessionStatus.COMPLETED
                ? "🎉 Xuất sắc! Bé đã hoàn thành tất cả thử thách!"
                : "Bé đã cố gắng rất giỏi! Thử lại để nhận phần thưởng nhé!";

        boolean passed = session.getStatus() == SessionStatus.COMPLETED;

        return SessionResultDto.builder()
                .sessionId(session.getId())
                .level(session.getLevel())
                .status(session.getStatus())
                .totalChallenges(session.getTotalChallenges())
                .completedChallenges(completed)
                .heartsRemaining(session.getHearts())
                .score(session.getScore())
                .rewardTitle(passed ? session.getRewardTitle() : null)
                .rewardCode(passed ? session.getRewardCode() : null)
                .rewardIcon(passed ? session.getRewardIcon() : null)
                .discountPercent(passed ? discountPercentForLevel(session.getLevel()) : null)
                .congratulationMessage(message)
                .completedAt(session.getCompletedAt())
                .build();
    }

    /** Coupon discount by level: EASY 5%, MEDIUM 10%, HARD 20%. */
    private int discountPercentForLevel(Level level) {
        return switch (level) {
            case EASY -> 5;
            case MEDIUM -> 10;
            case HARD -> 20;
        };
    }

    private void assignRandomReward(ChallengeSession session) {
        int percent = discountPercentForLevel(session.getLevel());
        session.setRewardTitle(percent + "% OFF Voucher");
        session.setRewardCode("KFC-" + percent + "-" + (1000 + new Random().nextInt(9000)));
        session.setRewardIcon("🎟️");
    }

    private boolean validateSelectedFoodItems(List<ChallengeItemDto> targetItems,
            List<ChallengeItemDto> selectedItems) {
        Map<Long, Integer> targetMap = new HashMap<>();
        for (ChallengeItemDto item : targetItems) {
            targetMap.put(item.getFoodId(), targetMap.getOrDefault(item.getFoodId(), 0) + item.getQuantity());
        }

        Map<Long, Integer> selectedMap = new HashMap<>();
        for (ChallengeItemDto item : selectedItems) {
            if (item.getQuantity() != null && item.getQuantity() > 0) {
                selectedMap.put(item.getFoodId(), selectedMap.getOrDefault(item.getFoodId(), 0) + item.getQuantity());
            }
        }

        return targetMap.equals(selectedMap);
    }

    /**
     * Generates randomized challenges with anti-memorization constraints.
     * Each session gets 3-5 challenges with randomized food items.
     */
    private List<Challenge> generateChallengesForLevel(String sessionId, Level level, int count, List<Food> allFoods,
            Random random) {
        List<Challenge> challenges = new ArrayList<>();

        // Shuffle food list copy for random picking without immediate repetition
        List<Food> shuffledFoods = new ArrayList<>(allFoods);
        Collections.shuffle(shuffledFoods, random);

        // Level 2 draws its puzzle type from a reshuffled bag so the three modes
        // never repeat back-to-back and stay unpredictable across sessions.
        List<String> typeBag = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Challenge challenge;
            switch (level) {
                case EASY -> challenge = generateEasyChallenge(sessionId, i, shuffledFoods, allFoods, random);
                case MEDIUM -> {
                    if (typeBag.isEmpty()) {
                        typeBag.addAll(List.of(L2_CHALLENGE_TYPES));
                        Collections.shuffle(typeBag, random);
                    }
                    challenge = generateMediumChallenge(sessionId, i, count, typeBag.remove(0), shuffledFoods, allFoods,
                            random);
                }
                case HARD -> challenge = generateHardChallenge(sessionId, i, allFoods, random);
                default -> challenge = generateEasyChallenge(sessionId, i, shuffledFoods, allFoods, random);
            }
            challenges.add(challenge);
        }

        return challenges;
    }

    /**
     * Level 1 - EASY: Listen & Choose
     * Child hears an English food name, picks the correct food card from 4 options.
     * No speech required.
     */
    private Challenge generateEasyChallenge(String sessionId, int index, List<Food> shuffledPool, List<Food> allFoods,
            Random random) {
        // Target food selected from pool to prevent duplicates
        Food targetFood = shuffledPool.get(index % shuffledPool.size());

        // Select 3 distractors
        List<Food> distractors = allFoods.stream()
                .filter(f -> !f.getId().equals(targetFood.getId()))
                .collect(Collectors.toList());
        Collections.shuffle(distractors, random);

        List<Food> options = new ArrayList<>();
        options.add(targetFood);
        for (int i = 0; i < 3 && i < distractors.size(); i++) {
            options.add(distractors.get(i));
        }
        Collections.shuffle(options, random);

        List<ChallengeItemDto> items = List.of(
                ChallengeItemDto.builder()
                        .foodId(targetFood.getId())
                        .foodName(targetFood.getName())
                        .displayName(targetFood.getDisplayName())
                        .quantity(1)
                        .build());

        // 3 Mini-Challenges for Level 1 (Visual Matching, Listening, Speaking)
        // Cycle or randomize challenge types based on sequence index to avoid
        // repetition
        String[] challengeTypes = { "VISUAL_MATCH", "LISTENING", "SPEAKING" };
        // Offset starting type randomly per session, but cycle sequentially so each
        // challenge in a round is different
        String challengeType = challengeTypes[(index + random.nextInt(3)) % challengeTypes.length];

        // Random prompt variations
        String[] prompts = {
                "I'd like " + targetFood.getName().toLowerCase() + ", please.",
                "Can I have " + targetFood.getName().toLowerCase() + "?",
                "One " + targetFood.getName().toLowerCase() + ", please.",
                targetFood.getName() + ", please."
        };
        String promptAudio = prompts[random.nextInt(prompts.length)];
        String speechTarget = targetFood.getName();

        return Challenge.builder()
                .sessionId(sessionId)
                .sequenceIndex(index)
                .challengeType(challengeType)
                .speechTarget(speechTarget)
                .promptAudioText(promptAudio)
                .itemsJson(serialize(items))
                .optionsJson(serialize(options.stream().map(this::mapFoodToDto).collect(Collectors.toList())))
                .completed(false)
                .correct(false)
                .build();
    }

    /**
     * Level 2 - MEDIUM: 3 rotating language puzzles.
     * WORD_SCRAMBLE -> rebuild a shuffled sentence in the right order
     * FILL_BLANK -> drop a key word into the blank
     * EXTRA_WORD -> spot and remove the intruder word
     * Difficulty ramps up as the child advances through the session.
     */
    private Challenge generateMediumChallenge(String sessionId, int index, int count, String challengeType,
            List<Food> shuffledPool, List<Food> allFoods, Random random) {
        int difficulty = Math.min(3, 1 + (index * 3) / Math.max(1, count));

        // Tier 3 asks for two items, the rest use a single item
        int numFoods = difficulty >= 3 ? 2 : 1;
        List<Food> selectedFoods = new ArrayList<>();
        int startIndex = (index * 2) % shuffledPool.size();
        for (int i = 0; i < numFoods; i++) {
            selectedFoods.add(shuffledPool.get((startIndex + i) % shuffledPool.size()));
        }

        List<ChallengeItemDto> items = new ArrayList<>();
        List<String> foodPhrases = new ArrayList<>();
        for (Food food : selectedFoods) {
            int qty = difficulty == 1 ? 1 : 1 + random.nextInt(2); // tier 1 always one item
            items.add(ChallengeItemDto.builder()
                    .foodId(food.getId())
                    .foodName(food.getName())
                    .displayName(food.getDisplayName())
                    .quantity(qty)
                    .build());

            foodPhrases.add(formatQuantityFood(qty, food.getName()));
        }

        // Pick a sentence shape that matches the difficulty tier
        String[] templates;
        if (foodPhrases.size() > 1) {
            templates = L2_SENTENCES_TIER3_DOUBLE;
        } else if (difficulty == 1) {
            templates = L2_SENTENCES_TIER1;
        } else {
            templates = L2_SENTENCES_TIER2;
        }
        String template = templates[random.nextInt(templates.length)];
        String sentence = foodPhrases.size() > 1
                ? String.format(template, foodPhrases.get(0), foodPhrases.get(1))
                : String.format(template, foodPhrases.get(0));

        ChallengePayloadDto payload = switch (challengeType) {
            case "WORD_SCRAMBLE" -> buildScramblePayload(sentence, difficulty, random);
            case "FILL_BLANK" -> buildBlankPayload(sentence, foodPhrases, difficulty, random);
            case "EXTRA_WORD" -> buildExtraWordPayload(sentence, selectedFoods, allFoods, difficulty, random);
            default -> ChallengePayloadDto.builder().difficulty(difficulty).build();
        };

        // Options: full menu, still used by the result screen / future levels
        List<FoodDto> options = allFoods.stream().map(this::mapFoodToDto).collect(Collectors.toList());
        Collections.shuffle(options, random);

        return Challenge.builder()
                .sessionId(sessionId)
                .sequenceIndex(index)
                .challengeType(challengeType)
                .speechTarget(sentence)
                .promptAudioText(sentence)
                .itemsJson(serialize(items))
                .optionsJson(serialize(options))
                .payloadJson(serialize(payload))
                .completed(false)
                .correct(false)
                .build();
    }

    /**
     * WORD_SCRAMBLE: shuffle the sentence tokens so the child must rebuild the
     * order.
     */
    private ChallengePayloadDto buildScramblePayload(String sentence, int difficulty, Random random) {
        List<String> tokens = new ArrayList<>(tokenize(sentence));
        List<String> scrambled = new ArrayList<>(tokens);

        // Keep shuffling until the puzzle is not already solved
        for (int attempt = 0; attempt < 10 && scrambled.equals(tokens); attempt++) {
            Collections.shuffle(scrambled, random);
        }

        return ChallengePayloadDto.builder()
                .instruction("Nghe câu mẫu, rồi chạm các từ theo đúng thứ tự để xếp thành câu!")
                .difficulty(difficulty)
                .scrambledWords(scrambled)
                .build();
    }

    /**
     * FILL_BLANK: hide one key word and offer 3 candidates.
     * Tier 1 blanks the number (easy: recognise the food), tier 2-3 blanks the verb
     * (grammar).
     * Only single-token positions are blanked so the sentence stays grammatical.
     */
    private ChallengePayloadDto buildBlankPayload(String sentence, List<String> foodPhrases,
            int difficulty, Random random) {
        List<String> tokens = tokenize(sentence);
        List<String> normalized = normalizeTokens(tokens);

        int verbIndex = firstIndexOfAny(normalized, L2_VERBS);
        int numberIndex = firstIndexOfAny(normalized, L2_NUMBERS);

        // The number can only be blanked when the food name is a single word,
        // otherwise the sentence would read "one ____ fries".
        boolean singleWordFood = !foodPhrases.isEmpty()
                && foodPhrases.stream().allMatch(p -> tokenize(p).size() == 2);
        boolean numberBlankable = numberIndex >= 0 && singleWordFood;

        int blankIndex;
        List<String> distractorPool;

        if (difficulty == 1 && numberBlankable) {
            blankIndex = numberIndex;
            distractorPool = Arrays.asList(L2_NUMBERS);
        } else if (verbIndex >= 0) {
            blankIndex = verbIndex;
            distractorPool = Arrays.asList(L2_VERBS);
        } else if (numberBlankable) {
            blankIndex = numberIndex;
            distractorPool = Arrays.asList(L2_NUMBERS);
        } else if (verbIndex >= 0) {
            blankIndex = verbIndex;
            distractorPool = Arrays.asList(L2_VERBS);
        } else {
            // Every generated sentence carries a verb, so this is unreachable in practice
            blankIndex = 0;
            distractorPool = Arrays.asList(L2_NUMBERS);
        }

        String correctWord = tokens.get(blankIndex);

        List<String> options = new ArrayList<>();
        options.add(correctWord);
        List<String> shuffledPool = new ArrayList<>(distractorPool);
        Collections.shuffle(shuffledPool, random);
        for (String candidate : shuffledPool) {
            if (options.size() >= 3) {
                break;
            }
            if (speechValidationService.normalize(candidate).equals(speechValidationService.normalize(correctWord))) {
                continue;
            }
            options.add(candidate);
        }
        Collections.shuffle(options, random);

        List<String> blankTokens = new ArrayList<>(tokens);
        blankTokens.set(blankIndex, withBlank(correctWord));

        return ChallengePayloadDto.builder()
                .instruction("Nghe câu thoại rồi chọn từ thích hợp để điền vào chỗ trống!")
                .difficulty(difficulty)
                .blankSentence(String.join(" ", blankTokens))
                .blankOptions(options)
                .correctBlankWord(correctWord)
                .build();
    }

    /**
     * EXTRA_WORD: sneak one more food name into a correct sentence, child must
     * remove it.
     * The intruder is always a single clean word so the child sees a normal word
     * chip.
     */
    private ChallengePayloadDto buildExtraWordPayload(String sentence, List<Food> sentenceFoods,
            List<Food> allFoods, int difficulty, Random random) {
        List<String> tokens = tokenize(sentence);

        List<Food> candidates = allFoods.stream()
                .filter(f -> sentenceFoods.stream().noneMatch(sf -> sf.getId().equals(f.getId())))
                .collect(Collectors.toList());
        Collections.shuffle(candidates, random);

        // Prefer a single-word name: a two-word chip would look like one long blob on
        // the board
        List<Food> singleWord = candidates.stream()
                .filter(f -> tokenize(f.getName()).size() == 1)
                .collect(Collectors.toList());

        Food intruder = singleWord.isEmpty() ? candidates.get(0) : singleWord.get(0);
        if (difficulty >= 3) {
            // Hard mode: pick a food from the same category so it is genuinely confusing
            String category = sentenceFoods.get(0).getCategory();
            intruder = singleWord.stream()
                    .filter(f -> Objects.equals(f.getCategory(), category))
                    .findFirst()
                    .orElse(intruder);
        }

        // Last token of a multi-word name, so the chip is always exactly one word
        String intruderWord = tokenize(intruder.getName()).get(tokenize(intruder.getName()).size() - 1);
        int insertAt = 1 + random.nextInt(Math.max(1, tokens.size() - 1));

        List<String> wordsWithExtra = new ArrayList<>(tokens);
        wordsWithExtra.add(insertAt, intruderWord);

        return ChallengePayloadDto.builder()
                .instruction("Nghe câu này rồi chạm vào TỪ THỪA để xóa đi nhé!")
                .difficulty(difficulty)
                .sentenceWords(wordsWithExtra)
                .extraWordIndex(insertAt)
                .extraWord(intruderWord)
                .build();
    }

    private List<String> tokenize(String sentence) {
        List<String> tokens = new ArrayList<>();
        if (sentence == null) {
            return tokens;
        }
        for (String raw : sentence.trim().split("\\s+")) {
            if (!raw.isEmpty()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private List<String> normalizeTokens(List<String> tokens) {
        return tokens.stream().map(speechValidationService::normalize).collect(Collectors.toList());
    }

    private int firstIndexOfAny(List<String> tokens, String[] candidates) {
        for (int i = 0; i < tokens.size(); i++) {
            for (String candidate : candidates) {
                if (tokens.get(i).equals(candidate)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Replaces a token with the blank placeholder, keeping any trailing
     * punctuation.
     */
    private String withBlank(String token) {
        int cut = 0;
        while (cut < token.length() && !Character.isLetterOrDigit(token.charAt(cut))) {
            cut++;
        }
        int end = token.length();
        while (end > cut && !Character.isLetterOrDigit(token.charAt(end - 1))) {
            end--;
        }
        return "____" + token.substring(end);
    }

    private boolean isLanguageChallenge(String type) {
        return "WORD_SCRAMBLE".equals(type) || "FILL_BLANK".equals(type) || "EXTRA_WORD".equals(type);
    }

    /**
     * Validates the child's answer for the Level 2 language puzzles.
     * WORD_SCRAMBLE requires the exact token order; the other two a single word.
     */
    private boolean validateLanguageAnswer(Challenge challenge, ChallengePayloadDto payload, String answerText) {
        if (payload == null || answerText == null || answerText.trim().isEmpty()) {
            return false;
        }

        return switch (challenge.getChallengeType()) {
            case "WORD_SCRAMBLE" -> speechValidationService.normalize(answerText)
                    .equals(speechValidationService.normalize(challenge.getSpeechTarget()));
            case "FILL_BLANK" -> speechValidationService.normalize(answerText)
                    .equals(speechValidationService.normalize(payload.getCorrectBlankWord()));
            case "EXTRA_WORD" -> speechValidationService.normalize(answerText)
                    .equals(speechValidationService.normalize(payload.getExtraWord()));
            default -> false;
        };
    }

    /**
     * Level 3 - HARD: Nhớ & Gọi món (Remember & Order) — menu only, no microphone.
     * Fixed 4-challenge ladder, randomly picked foods but escalating difficulty:
     * 1. RẤT DỄ -> one single item, quantity 1, full text hint shown.
     * 2. DỄ -> two items from two different categories, qty 1 each, full text hint
     * shown.
     * 3. TRUNG BÌNH -> memory: two familiar items, small quantities, text
     * auto-hidden.
     * 4. KHÓ -> memory: three items with quantities, text auto-hidden.
     */
    private Challenge generateHardChallenge(String sessionId, int index, List<Food> allFoods, Random random) {
        int tier = index + 1;
        boolean memory = tier >= 3;

        List<Food> targets = switch (tier) {
            case 2, 3 -> pickFromDistinctCategories(allFoods, random, 2);
            case 4 -> pickDistinct(allFoods, random, 3);
            default -> pickDistinct(allFoods, random, 1);
        };
        int[] quantities = quantitiesForTier(tier, random);

        List<ChallengeItemDto> items = new ArrayList<>();
        List<String> spokenParts = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Food food = targets.get(i);
            items.add(ChallengeItemDto.builder()
                    .foodId(food.getId())
                    .foodName(food.getName())
                    .displayName(food.getDisplayName())
                    .quantity(quantities[i])
                    .build());
            spokenParts.add(formatQuantityFood(quantities[i], food.getDisplayName()));
        }

        String orderSentence = buildL3OrderSentence(tier, spokenParts, random);

        // Options: the full menu so the child must find the right food across the tabs.
        List<FoodDto> options = allFoods.stream().map(this::mapFoodToDto).collect(Collectors.toList());
        Collections.shuffle(options, random);

        ChallengePayloadDto payload = ChallengePayloadDto.builder()
                .memory(memory)
                .difficulty(tier)
                .build();

        return Challenge.builder()
                .sessionId(sessionId)
                .sequenceIndex(index)
                .challengeType(memory ? "MEMORY_ORDER" : "MENU_ORDER")
                .speechTarget(orderSentence)
                .promptAudioText(orderSentence)
                .itemsJson(serialize(items))
                .optionsJson(serialize(options))
                .payloadJson(serialize(payload))
                .completed(false)
                .correct(false)
                .build();
    }

    /** Builds the spoken/sentence prompt for the given ladder tier. */
    private String buildL3OrderSentence(int tier, List<String> parts, Random random) {
        if (parts.size() == 1) {
            return String.format(L3_SINGLE_TEMPLATES[random.nextInt(L3_SINGLE_TEMPLATES.length)], parts.get(0));
        }
        if (parts.size() == 2) {
            String[] templates = tier == 3 ? L3_TWO_MEMORY_TEMPLATES : L3_TWO_HINT_TEMPLATES;
            return String.format(templates[random.nextInt(templates.length)], parts.get(0), parts.get(1));
        }
        return String.format(L3_THREE_MEMORY_TEMPLATES[random.nextInt(L3_THREE_MEMORY_TEMPLATES.length)],
                parts.get(0), parts.get(1), parts.get(2));
    }

    /**
     * Picks n distinct foods, each from its own category (guaranteed different
     * categories).
     */
    private List<Food> pickFromDistinctCategories(List<Food> pool, Random random, int n) {
        Map<String, List<Food>> byCategory = pool.stream()
                .collect(Collectors.groupingBy(f -> f.getCategory() != null ? f.getCategory() : "Other",
                        LinkedHashMap::new, Collectors.toList()));
        List<List<Food>> categoryGroups = new ArrayList<>(byCategory.values());
        Collections.shuffle(categoryGroups, random);

        List<Food> result = new ArrayList<>();
        for (List<Food> group : categoryGroups) {
            if (result.size() >= n)
                break;
            List<Food> copy = new ArrayList<>(group);
            Collections.shuffle(copy, random);
            result.add(copy.get(0));
        }

        // Fallback in case there are fewer distinct categories than needed.
        if (result.size() < n) {
            List<Food> rest = new ArrayList<>(pool);
            rest.removeAll(result);
            Collections.shuffle(rest, random);
            for (Food food : rest) {
                if (result.size() >= n)
                    break;
                if (!result.contains(food)) {
                    result.add(food);
                }
            }
        }
        return result;
    }

    /** Picks n distinct foods from the pool (shuffled). */
    private List<Food> pickDistinct(List<Food> pool, Random random, int n) {
        List<Food> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, Math.min(n, copy.size())));
    }

    /**
     * Quantity configurations per ladder tier:
     * tier 1: 1 item x1 | tier 2: 2 items x1 | tier 3: 2 items, one pair of them
     * x2 | tier 4: 3 items with quantities 1-2, at least one doubled so the count
     * matters.
     */
    private int[] quantitiesForTier(int tier, Random random) {
        switch (tier) {
            case 2:
                return new int[] { 1, 1 };
            case 3:
                return random.nextBoolean() ? new int[] { 1, 2 } : new int[] { 2, 1 };
            case 4:
                int[] q = new int[3];
                for (int i = 0; i < q.length; i++) {
                    q[i] = 1 + random.nextInt(2); // 1 or 2
                }
                if (q[0] != 2 && q[1] != 2 && q[2] != 2) {
                    q[random.nextInt(q.length)] = 2;
                }
                return q;
            default:
                return new int[] { 1 };
        }
    }

    private String formatQuantityFood(int quantity, String displayName) {
        String numWord = quantity == 1 ? "one" : "two";
        if (quantity == 1) {
            // Singular: never pluralize ("one Burger", not "one Burgers").
            return numWord + " " + titleCase(displayName.toLowerCase());
        }
        return numWord + " " + titleCase(pluralize(displayName.toLowerCase()));
    }

    private String pluralize(String name) {
        return switch (name) {
            // Mass nouns or already-plural words: they stay exactly as they are
            case "sandwich" -> "sandwiches";
            case "fries", "french fries", "chicken rice", "fried chicken" -> name;
            case "ice cream" -> "ice creams";
            default -> {
                if (name.endsWith("s")) {
                    // Already plural ("fries", "noodles"): never build "frieses"
                    yield name;
                }
                if (name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) {
                    yield name + "es";
                }
                yield name + "s";
            }
        };
    }

    private String titleCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (String word : str.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private FoodDto mapFoodToDto(Food food) {
        return FoodDto.builder()
                .id(food.getId())
                .name(food.getName())
                .displayName(food.getDisplayName())
                .image(food.getImage())
                .category(food.getCategory())
                .pronunciationText(food.getPronunciationText())
                .price(food.getPrice())
                .active(food.getActive())
                .build();
    }

    private ChallengeDto mapChallengeToDto(Challenge challenge) {
        ChallengePayloadDto payload = deserializePayload(challenge.getPayloadJson());

        return ChallengeDto.builder()
                .challengeId(challenge.getId())
                .sequenceIndex(challenge.getSequenceIndex())
                .type(challenge.getChallengeType())
                .speechTarget(challenge.getSpeechTarget())
                .promptAudioText(challenge.getPromptAudioText())
                .items(deserializeItems(challenge.getItemsJson()))
                .options(deserializeOptions(challenge.getOptionsJson()))
                .completed(challenge.getCompleted())
                .correct(challenge.getCorrect())
                .instruction(payload != null ? payload.getInstruction() : null)
                .difficulty(payload != null ? payload.getDifficulty() : null)
                .memory(payload != null ? payload.getMemory() : null)
                .scrambledWords(payload != null ? payload.getScrambledWords() : null)
                .sentenceWords(payload != null ? payload.getSentenceWords() : null)
                // extraWordIndex / correctBlankWord / extraWord stay server-side
                .blankSentence(payload != null ? payload.getBlankSentence() : null)
                .blankOptions(payload != null ? payload.getBlankOptions() : null)
                .build();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize json", e);
            return "[]";
        }
    }

    private List<ChallengeItemDto> deserializeItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("Failed to deserialize items json: {}", json, e);
            return Collections.emptyList();
        }
    }

    private List<FoodDto> deserializeOptions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("Failed to deserialize options json: {}", json, e);
            return Collections.emptyList();
        }
    }

    private ChallengePayloadDto deserializePayload(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ChallengePayloadDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize payload json: {}", json, e);
            return null;
        }
    }
}
