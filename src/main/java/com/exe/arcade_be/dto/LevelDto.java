package com.exe.arcade_be.dto;

import com.exe.arcade_be.enums.Level;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelDto {
    private Level id;
    private String name;
    private String tag;
    private String description;
    private String icon;
    private String difficulty;
    private Integer challengeCount;
}
