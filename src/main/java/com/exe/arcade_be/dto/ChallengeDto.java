package com.exe.arcade_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeDto {
    private Long challengeId;
    private Integer sequenceIndex;
    private String type;
    private String speechTarget;
    private String promptAudioText;
    private List<ChallengeItemDto> items;
    private List<FoodDto> options;
    private Boolean completed;
    private Boolean correct;

    // ---- Level 2 language challenges ----
    private String instruction;
    private Integer difficulty;
    private Boolean memory;
    private List<String> scrambledWords;
    private List<String> sentenceWords;
    private Integer extraWordIndex;
    private String blankSentence;
    private List<String> blankOptions;
}
