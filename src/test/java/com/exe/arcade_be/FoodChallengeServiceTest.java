package com.exe.arcade_be;

import com.exe.arcade_be.dto.ChallengeDto;
import com.exe.arcade_be.dto.ChallengeItemDto;
import com.exe.arcade_be.dto.SessionDto;
import com.exe.arcade_be.dto.StartChallengeRequest;
import com.exe.arcade_be.dto.SubmitAnswerRequest;
import com.exe.arcade_be.dto.ValidationResultDto;
import com.exe.arcade_be.enums.Level;
import com.exe.arcade_be.enums.SessionStatus;
import com.exe.arcade_be.service.FoodChallengeService;
import com.exe.arcade_be.service.SpeechValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FoodChallengeServiceTest {

    @Autowired
    private FoodChallengeService foodChallengeService;

    @Autowired
    private SpeechValidationService speechValidationService;

    @BeforeEach
    void resetSessionCache() {
        mediumSession = null;
        challengeSessionId = null;
    }

    @Test
    void testSpeechValidation() {
        // Exact match
        assertTrue(speechValidationService.isSpeechMatch("Pizza", "Pizza."));
        assertTrue(speechValidationService.isSpeechMatch("one burger please", "One burger, please."));
        assertTrue(speechValidationService.isSpeechMatch("1 burger please", "One burger, please."));
        assertTrue(speechValidationService.isSpeechMatch("two hot dogs please", "Two hot dogs, please."));
        assertTrue(speechValidationService.isSpeechMatch("i'd like two hot dogs and one ice cream please", "I'd like two hot dogs and one ice cream, please."));
        assertTrue(speechValidationService.isSpeechMatch("i would like two hotdogs and one icecream please", "I'd like two hot dogs and one ice cream, please."));
        
        // Tolerant matching for kids
        assertTrue(speechValidationService.isSpeechMatch("two hot dogs and one ice cream", "I'd like two hot dogs and one ice cream, please."));
        assertFalse(speechValidationService.isSpeechMatch("pizza and salad", "Two hot dogs, please."));
    }

    @Test
    void testStartEasySessionAndAnswer() {
        SessionDto session = foodChallengeService.startSession(new StartChallengeRequest(Level.EASY));
        assertNotNull(session.getSessionId());
        assertTrue(session.getTotalChallenges() >= 3 && session.getTotalChallenges() <= 5);
        assertEquals(3, session.getHearts());
        assertEquals(SessionStatus.IN_PROGRESS, session.getStatus());
        assertNotNull(session.getCurrentChallenge());

        var currentChallenge = session.getCurrentChallenge();
        Long foodId = currentChallenge.getItems().get(0).getFoodId();
        String foodName = currentChallenge.getItems().get(0).getFoodName();

        // Submit correct answer
        ValidationResultDto result = foodChallengeService.submitAnswer(
                session.getSessionId(),
                new SubmitAnswerRequest(
                        currentChallenge.getChallengeId(),
                        List.of(ChallengeItemDto.builder().foodId(foodId).quantity(1).build()),
                        foodName
                )
        );

        assertTrue(result.getCorrect());
        assertTrue(result.getFoodCorrect());
        assertTrue(result.getSpeechCorrect());
        assertEquals(1, result.getProgress());
    }

    @Test
    void testWrongAnswerDecreasesHearts() {
        SessionDto session = foodChallengeService.startSession(new StartChallengeRequest(Level.MEDIUM));
        var currentChallenge = session.getCurrentChallenge();

        // Submit completely wrong answer
        ValidationResultDto result = foodChallengeService.submitAnswer(
                session.getSessionId(),
                new SubmitAnswerRequest(
                        currentChallenge.getChallengeId(),
                        List.of(ChallengeItemDto.builder().foodId(999L).quantity(5).build()),
                        "Wrong spoken text"
                )
        );

        assertFalse(result.getCorrect());
        assertEquals(2, result.getHearts());
    }

    @Test
    void testDuplicateSubmitDoesNotAdvanceTwice() {
        SessionDto session = foodChallengeService.startSession(new StartChallengeRequest(Level.EASY));
        var currentChallenge = session.getCurrentChallenge();
        var answer = new SubmitAnswerRequest(
                currentChallenge.getChallengeId(),
                List.of(ChallengeItemDto.builder()
                        .foodId(currentChallenge.getItems().get(0).getFoodId())
                        .quantity(1)
                        .build()),
                currentChallenge.getItems().get(0).getFoodName()
        );

        ValidationResultDto first = foodChallengeService.submitAnswer(session.getSessionId(), answer);
        assertTrue(first.getCorrect());
        assertEquals(1, first.getProgress());

        // Replaying the very same answer must be rejected instead of advancing again
        ValidationResultDto replay = foodChallengeService.submitAnswer(session.getSessionId(), answer);
        assertFalse(replay.getCorrect());
        assertEquals("STALE_CHALLENGE", replay.getFeedbackType());
        assertEquals(1, replay.getProgress());
        assertEquals(3, replay.getHearts());

        // Session score must reflect a single answer only
        SessionDto after = foodChallengeService.getSession(session.getSessionId());
        assertEquals(100, after.getScore());
        assertEquals(1, after.getCurrentChallengeIndex());
    }

    // =========================================================
    // Level 2 - language puzzles (WORD_SCRAMBLE / FILL_BLANK / EXTRA_WORD)
    // =========================================================

    @Test
    void testMediumSessionCoversAllThreePuzzleTypes() {
        SessionDto session = foodChallengeService.startSession(new StartChallengeRequest(Level.MEDIUM));

        Set<String> types = session.getChallenges().stream()
                .map(ChallengeDto::getType)
                .collect(Collectors.toSet());

        assertEquals(Set.of("WORD_SCRAMBLE", "FILL_BLANK", "EXTRA_WORD"), types);
    }

    @Test
    void testMediumDifficultyRampsUpWithinSession() {
        SessionDto session = foodChallengeService.startSession(new StartChallengeRequest(Level.MEDIUM));
        List<ChallengeDto> challenges = session.getChallenges();

        int previous = 0;
        for (ChallengeDto challenge : challenges) {
            assertNotNull(challenge.getDifficulty());
            assertTrue(challenge.getDifficulty() >= previous, "difficulty must not decrease");
            assertTrue(challenge.getDifficulty() >= 1 && challenge.getDifficulty() <= 3);
            previous = challenge.getDifficulty();
        }
        assertEquals(3, previous, "last challenge must be the hardest tier");
    }

    @Test
    void testWordScrambleShufflesAndValidatesOrder() {
        ensureSession();

        int index = indexOfType("WORD_SCRAMBLE");
        List<String> correctOrder = tokensOf(mediumSession.getChallenges().get(index).getSpeechTarget());
        List<String> scrambled = mediumSession.getChallenges().get(index).getScrambledWords();

        assertNotNull(scrambled);
        assertFalse(scrambled.equals(correctOrder), "words must actually be shuffled");
        assertEquals(correctOrder.size(), scrambled.size());

        ChallengeDto current = advanceTo(index);

        // Right words but wrong order must be rejected
        ValidationResultDto wrongOrder = submit(current.getChallengeId(), String.join(" ", reversed(correctOrder)));
        assertFalse(wrongOrder.getCorrect());
        assertEquals("RETRY_WORD", wrongOrder.getFeedbackType());

        ValidationResultDto right = submit(current.getChallengeId(), current.getSpeechTarget());
        assertTrue(right.getCorrect());
    }

    @Test
    void testFillInBlankAcceptsOnlyTheKeyWord() {
        ensureSession();

        int index = indexOfType("FILL_BLANK");
        ChallengeDto planned = mediumSession.getChallenges().get(index);

        assertTrue(planned.getBlankSentence().contains("____"), "sentence must have a blank");
        assertEquals(planned.getSpeechTarget().trim().split("\\s+").length,
                planned.getBlankSentence().trim().split("\\s+").length, "blank must replace one word");
        assertEquals(3, planned.getBlankOptions().size());

        ChallengeDto current = advanceTo(index);
        String correctWord = blankAnswerOf(current);
        String wrongWord = current.getBlankOptions().stream()
                .filter(w -> !w.equals(correctWord))
                .findFirst()
                .orElseThrow();

        assertFalse(submit(current.getChallengeId(), wrongWord).getCorrect());
        assertTrue(submit(current.getChallengeId(), correctWord).getCorrect());
    }

    @Test
    void testExtraWordOnlyAcceptsTheIntruder() {
        ensureSession();

        int index = indexOfType("EXTRA_WORD");
        ChallengeDto planned = mediumSession.getChallenges().get(index);
        assertEquals(tokensOf(planned.getSpeechTarget()).size() + 1, planned.getSentenceWords().size());

        ChallengeDto current = advanceTo(index);
        String intruder = intruderWordOf(current);

        assertFalse(submit(current.getChallengeId(), "please").getCorrect());
        assertTrue(submit(current.getChallengeId(), intruder).getCorrect());
    }

    @Test
    void testLevelTwoNoLongerRequiresMicrophone() {
        ensureSession();
        ChallengeDto challenge = mediumSession.getCurrentChallenge();

        // Answering with the right text but no food items and no speech must still pass
        ValidationResultDto result = foodChallengeService.submitAnswer(
                challengeSessionId,
                new SubmitAnswerRequest(challenge.getChallengeId(), null, null, correctAnswerOf(challenge)));

        assertTrue(result.getCorrect());
        assertEquals(3, result.getHearts());
    }

    // ---- helpers ----

    private SessionDto mediumSession;
    private String challengeSessionId;

    private void ensureSession() {
        if (mediumSession == null) {
            mediumSession = foodChallengeService.startSession(new StartChallengeRequest(Level.MEDIUM));
            challengeSessionId = mediumSession.getSessionId();
        }
    }

    private int indexOfType(String type) {
        ensureSession();
        for (int i = 0; i < mediumSession.getChallenges().size(); i++) {
            if (type.equals(mediumSession.getChallenges().get(i).getType())) {
                return i;
            }
        }
        throw new AssertionError("No " + type + " challenge generated");
    }

    private ValidationResultDto submit(Long challengeId, String answerText) {
        return foodChallengeService.submitAnswer(
                challengeSessionId, new SubmitAnswerRequest(challengeId, null, null, answerText));
    }

    /** Plays through the session with correct answers until {@code targetIndex} is the live challenge. */
    private ChallengeDto advanceTo(int targetIndex) {
        ensureSession();
        SessionDto current = foodChallengeService.getSession(challengeSessionId);
        while (current.getCurrentChallengeIndex() < targetIndex) {
            ChallengeDto challenge = current.getCurrentChallenge();
            ValidationResultDto result = submit(challenge.getChallengeId(), correctAnswerOf(challenge));
            assertTrue(result.getCorrect(), "setup answer rejected for " + challenge.getType());
            current = foodChallengeService.getSession(challengeSessionId);
        }
        return current.getCurrentChallenge();
    }

    private String correctAnswerOf(ChallengeDto challenge) {
        return switch (challenge.getType()) {
            case "WORD_SCRAMBLE" -> challenge.getSpeechTarget();
            case "FILL_BLANK" -> blankAnswerOf(challenge);
            case "EXTRA_WORD" -> intruderWordOf(challenge);
            default -> throw new AssertionError("Not a language challenge");
        };
    }

    /** Recovers the hidden blank word by aligning the blanked sentence with the real one. */
    private String blankAnswerOf(ChallengeDto challenge) {
        List<String> blankTokens = tokensOf(challenge.getBlankSentence());
        List<String> realTokens = tokensOf(challenge.getSpeechTarget());
        for (int i = 0; i < blankTokens.size(); i++) {
            if (blankTokens.get(i).startsWith("____")) {
                return realTokens.get(i);
            }
        }
        throw new AssertionError("No blank found in: " + challenge.getBlankSentence());
    }

    /** Recovers the intruder word as the token the sentence has but the original does not. */
    private String intruderWordOf(ChallengeDto challenge) {
        List<String> remaining = new ArrayList<>(tokensOf(challenge.getSpeechTarget()));
        for (String word : challenge.getSentenceWords()) {
            if (remaining.remove(word)) {
                continue;
            }
            return word;
        }
        throw new AssertionError("No extra word found in: " + challenge.getSentenceWords());
    }

    private static List<String> tokensOf(String sentence) {
        List<String> tokens = new ArrayList<>();
        for (String raw : sentence.trim().split("\\s+")) {
            if (!raw.isEmpty()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private static List<String> reversed(List<String> tokens) {
        List<String> copy = new ArrayList<>(tokens);
        Collections.reverse(copy);
        return copy;
    }
}
