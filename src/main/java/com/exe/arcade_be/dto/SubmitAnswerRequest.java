package com.exe.arcade_be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAnswerRequest {
    private Long challengeId;
    private List<ChallengeItemDto> selectedItems;
    private String spokenText;
    /**
     * Child's answer for language challenges:
     * WORD_SCRAMBLE -> the assembled sentence, e.g. "I want a chicken please"
     * FILL_BLANK    -> the chosen word, e.g. "have"
     * EXTRA_WORD    -> the word the child tapped to remove, e.g. "apple"
     */
    private String answerText;

    public SubmitAnswerRequest(Long challengeId, List<ChallengeItemDto> selectedItems, String spokenText) {
        this(challengeId, selectedItems, spokenText, null);
    }
}
