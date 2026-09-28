package com.exe.arcade_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeItemDto {
    private Long foodId;
    private String foodName;
    private String displayName;
    private Integer quantity;
}
