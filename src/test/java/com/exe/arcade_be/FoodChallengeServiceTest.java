package com.exe.arcade_be;

import com.exe.arcade_be.dto.ChallengeItemDto;
import com.exe.arcade_be.dto.SessionDto;
import com.exe.arcade_be.dto.StartChallengeRequest;
import com.exe.arcade_be.dto.SubmitAnswerRequest;
import com.exe.arcade_be.dto.ValidationResultDto;
import com.exe.arcade_be.enums.Level;
import com.exe.arcade_be.enums.SessionStatus;
import com.exe.arcade_be.service.FoodChallengeService;
import com.exe.arcade_be.service.SpeechValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FoodChallengeServiceTest {

    @Autowired
    private FoodChallengeService foodChallengeService;

    @Autowired
    private SpeechValidationService speechValidationService;

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
        assertEquals(4, session.getTotalChallenges());
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
}
