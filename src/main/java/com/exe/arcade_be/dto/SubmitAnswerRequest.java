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
}
