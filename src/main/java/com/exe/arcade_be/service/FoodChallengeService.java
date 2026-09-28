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

    private static final String[] REWARD_TITLES = {
            "Free Ice Cream Voucher",
            "Crispy Burger Coupon",
            "Happy Meal Voucher",
            "Super Fries Combo Pass",
            "Sweet Dessert Voucher",
            "Golden Foodie Ticket"
    };

    private static final String[] REWARD_ICONS = {
            "🍦", "🍔", "🍟", "🍕", "🍗", "🎟️"
    };

    // Randomized speech sentence templates for Level 2 (MEDIUM)
    private static final String[][] MEDIUM_TEMPLATES = {
            // Single item templates
            {"Can I have %s, please?", "I'd like %s, please.", "One %s, please.", "Could I get %s?", "I want %s, please."},
            // Two item templates
            {"Can I have %s and %s, please?", "I'd like %s and %s, please.", "%s and %s, please.", "Could I get %s and %s?"}
    };

    // Randomized speech sentence templates for Level 3 (HARD)
    private static final String[][] HARD_TEMPLATES = {
            // Two item templates
            {"I'd like %s and %s, please.", "Can I order %s and %s, please?", "I want %s and %s, please.", "Could I have %s and %s?"},
            // Three item templates
            {"I'd like %s, %s and %s, please.", "Can I order %s, %s and %s, please?", "I want %s, %s and %s, please.", "Could I have %s, %s and %s?"}
    };

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
                        .name("Nghe & Gọi món")
                        .tag("Cấp độ 2")
                        .description("Nghe yêu cầu, chọn món, rồi nói tên món bằng tiếng Anh.")
                        .icon("🍔")
                        .difficulty("Trung bình")
                        .challengeCount(0)
                        .build(),
                LevelDto.builder()
                        .id(Level.HARD)
                        .name("Nhớ & Gọi món")
                        .tag("Cấp độ 3")
                        .description("Nghe yêu cầu, ghi nhớ, tự chọn món và nói câu gọi món bằng tiếng Anh.")
                        .icon("⭐")
                        .difficulty("Khó")
                        .challengeCount(0)
                        .build()
        );
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
        int totalChallenges = 3 + random.nextInt(3); // Random 3-5 challenges

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
        if (session.getCurrentChallengeIndex() < challengeDtos.size() && session.getStatus() == SessionStatus.IN_PROGRESS) {
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

    @Transactional
    public ValidationResultDto submitAnswer(String sessionId, SubmitAnswerRequest request) {
        ChallengeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            return ValidationResultDto.builder()
                    .correct(false)
                    .foodCorrect(false)
                    .speechCorrect(false)
                    .message("Trò chơi đã kết thúc.")
                    .progress(session.getCurrentChallengeIndex())
                    .total(session.getTotalChallenges())
                    .hearts(session.getHearts())
                    .sessionStatus(session.getStatus())
                    .feedbackType("GAME_OVER")
                    .build();
        }

        Challenge challenge = challengeRepository.findById(request.getChallengeId())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + request.getChallengeId()));

        List<ChallengeItemDto> targetItems = deserializeItems(challenge.getItemsJson());
        List<ChallengeItemDto> selectedItems = request.getSelectedItems() != null ? request.getSelectedItems() : Collections.emptyList();

        // 1. Validate Food Selection & Quantities
        boolean foodCorrect = validateSelectedFoodItems(targetItems, selectedItems);

        // 2. Validate Spoken Text (only for levels that require it)
        String spokenText = request.getSpokenText() != null ? request.getSpokenText().trim() : "";
        boolean speechRequired = !challenge.getChallengeType().equals("FINDER");
        boolean speechCorrect;

        if (speechRequired) {
            speechCorrect = speechValidationService.isSpeechMatch(spokenText, challenge.getSpeechTarget());
        } else {
            // For FINDER type (level 1), speech is not required
            speechCorrect = true;
        }

        boolean isOverallCorrect = foodCorrect && speechCorrect;

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
                    .message("Tuyệt vời! Con đã trả lời đúng! 🎉")
                    .progress(newIndex)
                    .total(session.getTotalChallenges())
                    .hearts(session.getHearts())
                    .sessionStatus(session.getStatus())
                    .feedbackType("SUCCESS")
                    .nextChallenge(nextChallengeDto)
                    .expectedSpeech(challenge.getSpeechTarget())
                    .spokenNormalized(speechValidationService.normalize(spokenText))
                    .build();
        } else {
            // Deduct a heart
            int remainingHearts = Math.max(0, session.getHearts() - 1);
            session.setHearts(remainingHearts);

            String message;
            String feedbackType;

            if (!foodCorrect && !speechCorrect) {
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
                message = "Con đã cố gắng rất giỏi! Chơi lại để chiến thắng nhé! 🌟";
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
                    .spokenNormalized(speechValidationService.normalize(spokenText))
                    .build();
        }
    }

    public SessionResultDto getSessionResult(String sessionId) {
        ChallengeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        int completed = (int) challengeRepository.findBySessionIdOrderBySequenceIndexAsc(sessionId)
                .stream().filter(Challenge::getCorrect).count();

        String message = session.getStatus() == SessionStatus.COMPLETED
                ? "🎉 Xuất sắc! Con đã hoàn thành tất cả thử thách!"
                : "Con đã cố gắng rất giỏi! Thử lại để nhận phần thưởng nhé!";

        return SessionResultDto.builder()
                .sessionId(session.getId())
                .level(session.getLevel())
                .status(session.getStatus())
                .totalChallenges(session.getTotalChallenges())
                .completedChallenges(completed)
                .heartsRemaining(session.getHearts())
                .score(session.getScore())
                .rewardTitle(session.getRewardTitle() != null ? session.getRewardTitle() : "Ngôi sao ẩm thực")
                .rewardCode(session.getRewardCode() != null ? session.getRewardCode() : "FOOD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .rewardIcon(session.getRewardIcon() != null ? session.getRewardIcon() : "🎟️")
                .congratulationMessage(message)
                .completedAt(session.getCompletedAt())
                .build();
    }

    private void assignRandomReward(ChallengeSession session) {
        Random random = new Random();
        int idx = random.nextInt(REWARD_TITLES.length);
        session.setRewardTitle(REWARD_TITLES[idx]);
        session.setRewardIcon(REWARD_ICONS[idx]);
        session.setRewardCode("VOUCHER-" + (1000 + random.nextInt(9000)));
    }

    private boolean validateSelectedFoodItems(List<ChallengeItemDto> targetItems, List<ChallengeItemDto> selectedItems) {
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
    private List<Challenge> generateChallengesForLevel(String sessionId, Level level, int count, List<Food> allFoods, Random random) {
        List<Challenge> challenges = new ArrayList<>();

        // Shuffle food list copy for random picking without immediate repetition
        List<Food> shuffledFoods = new ArrayList<>(allFoods);
        Collections.shuffle(shuffledFoods, random);

        for (int i = 0; i < count; i++) {
            Challenge challenge;
            switch (level) {
                case EASY -> challenge = generateEasyChallenge(sessionId, i, shuffledFoods, allFoods, random);
                case MEDIUM -> challenge = generateMediumChallenge(sessionId, i, shuffledFoods, allFoods, random);
                case HARD -> challenge = generateHardChallenge(sessionId, i, shuffledFoods, allFoods, random);
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
    private Challenge generateEasyChallenge(String sessionId, int index, List<Food> shuffledPool, List<Food> allFoods, Random random) {
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
                        .build()
        );

        // Random prompt variations
        String[] prompts = {
                "I'd like " + targetFood.getName().toLowerCase() + ", please.",
                "Can I have " + targetFood.getName().toLowerCase() + "?",
                "One " + targetFood.getName().toLowerCase() + ", please.",
                targetFood.getName() + ", please."
        };
        String promptAudio = prompts[random.nextInt(prompts.length)];
        String speechTarget = targetFood.getName(); // Just need to identify, no speech required

        return Challenge.builder()
                .sessionId(sessionId)
                .sequenceIndex(index)
                .challengeType("FINDER")
                .speechTarget(speechTarget)
                .promptAudioText(promptAudio)
                .itemsJson(serialize(items))
                .optionsJson(serialize(options.stream().map(this::mapFoodToDto).collect(Collectors.toList())))
                .completed(false)
                .correct(false)
                .build();
    }

    /**
     * Level 2 - MEDIUM: Listen & Order
     * Child hears the order, picks the food, then speaks the food name in English.
     */
    private Challenge generateMediumChallenge(String sessionId, int index, List<Food> shuffledPool, List<Food> allFoods, Random random) {
        // Random 1 or 2 distinct foods
        int numFoods = 1 + random.nextInt(2); // 1 or 2
        List<Food> selectedTargetFoods = new ArrayList<>();

        int startIndex = (index * 2) % shuffledPool.size();
        for (int i = 0; i < numFoods; i++) {
            selectedTargetFoods.add(shuffledPool.get((startIndex + i) % shuffledPool.size()));
        }

        List<ChallengeItemDto> items = new ArrayList<>();
        List<String> spokenParts = new ArrayList<>();

        for (Food food : selectedTargetFoods) {
            int qty = 1 + random.nextInt(2); // 1 or 2
            items.add(ChallengeItemDto.builder()
                    .foodId(food.getId())
                    .foodName(food.getName())
                    .displayName(food.getDisplayName())
                    .quantity(qty)
                    .build());

            spokenParts.add(formatQuantityFood(qty, food.getName()));
        }

        // Pick a random template
        String[] templates;
        String orderSentence;
        if (spokenParts.size() == 1) {
            templates = MEDIUM_TEMPLATES[0];
            String template = templates[random.nextInt(templates.length)];
            orderSentence = String.format(template, spokenParts.get(0));
        } else {
            templates = MEDIUM_TEMPLATES[1];
            String template = templates[random.nextInt(templates.length)];
            orderSentence = String.format(template, spokenParts.get(0), spokenParts.get(1));
        }

        // Options: Include all active foods for full menu tray experience
        List<FoodDto> options = allFoods.stream().map(this::mapFoodToDto).collect(Collectors.toList());
        Collections.shuffle(options, random);

        return Challenge.builder()
                .sessionId(sessionId)
                .sequenceIndex(index)
                .challengeType("ORDER")
                .speechTarget(orderSentence)
                .promptAudioText(orderSentence)
                .itemsJson(serialize(items))
                .optionsJson(serialize(options))
                .completed(false)
                .correct(false)
                .build();
    }

    /**
     * Level 3 - HARD: Remember & Order
     * Child hears the order, it disappears, then they must remember, select foods and speak the full sentence.
     */
    private Challenge generateHardChallenge(String sessionId, int index, List<Food> shuffledPool, List<Food> allFoods, Random random) {
        // Random 2 or 3 distinct foods
        int numFoods = 2 + random.nextInt(2); // 2 or 3
        List<Food> selectedTargetFoods = new ArrayList<>();

        int startIndex = (index * 3) % shuffledPool.size();
        for (int i = 0; i < numFoods; i++) {
            selectedTargetFoods.add(shuffledPool.get((startIndex + i) % shuffledPool.size()));
        }

        List<ChallengeItemDto> items = new ArrayList<>();
        List<String> spokenParts = new ArrayList<>();

        for (Food food : selectedTargetFoods) {
            int qty = 1 + random.nextInt(2); // 1 or 2
            items.add(ChallengeItemDto.builder()
                    .foodId(food.getId())
                    .foodName(food.getName())
                    .displayName(food.getDisplayName())
                    .quantity(qty)
                    .build());

            spokenParts.add(formatQuantityFood(qty, food.getName()));
        }

        // Pick a random template
        String[] templates;
        String orderSentence;
        if (spokenParts.size() == 2) {
            templates = HARD_TEMPLATES[0];
            String template = templates[random.nextInt(templates.length)];
            orderSentence = String.format(template, spokenParts.get(0), spokenParts.get(1));
        } else {
            templates = HARD_TEMPLATES[1];
            String template = templates[random.nextInt(templates.length)];
            orderSentence = String.format(template, spokenParts.get(0), spokenParts.get(1), spokenParts.get(2));
        }

        // Options: Include all active foods
        List<FoodDto> options = allFoods.stream().map(this::mapFoodToDto).collect(Collectors.toList());
        Collections.shuffle(options, random);

        return Challenge.builder()
                .sessionId(sessionId)
                .sequenceIndex(index)
                .challengeType("SUPER_ORDER")
                .speechTarget(orderSentence)
                .promptAudioText(orderSentence)
                .itemsJson(serialize(items))
                .optionsJson(serialize(options))
                .completed(false)
                .correct(false)
                .build();
    }

    private String formatQuantityFood(int quantity, String foodName) {
        String numWord = quantity == 1 ? "one" : "two";
        if (quantity == 1) {
            return numWord + " " + foodName.toLowerCase();
        } else {
            return numWord + " " + pluralize(foodName.toLowerCase());
        }
    }

    private String pluralize(String name) {
        if (name.equals("sandwich")) return "sandwiches";
        if (name.equals("french fries")) return "french fries";
        if (name.equals("ice cream")) return "ice creams";
        if (name.equals("chicken rice")) return "chicken rice";
        if (name.equals("fried chicken")) return "fried chicken";
        if (name.endsWith("s") || name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
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
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize items json: {}", json, e);
            return Collections.emptyList();
        }
    }

    private List<FoodDto> deserializeOptions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize options json: {}", json, e);
            return Collections.emptyList();
        }
    }
}
