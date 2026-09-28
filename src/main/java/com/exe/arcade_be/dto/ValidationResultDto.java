package com.exe.arcade_be.dto;

import com.exe.arcade_be.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultDto {
    private Boolean correct;
    private Boolean foodCorrect;
    private Boolean speechCorrect;
    private String message;
    private Integer progress;
    private Integer total;
    private Integer hearts;
    private SessionStatus sessionStatus;
    private String feedbackType; // "SUCCESS", "RETRY_FOOD", "RETRY_SPEECH", "GAME_OVER"
    private ChallengeDto nextChallenge;
    private String expectedSpeech;
    private String spokenNormalized;
}
