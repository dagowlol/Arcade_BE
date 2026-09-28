package com.exe.arcade_be.dto;

import com.exe.arcade_be.enums.Level;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartChallengeRequest {
    private Level level;
}
