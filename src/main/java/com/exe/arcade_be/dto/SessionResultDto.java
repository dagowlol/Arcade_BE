package com.exe.arcade_be.dto;

import com.exe.arcade_be.enums.Level;
import com.exe.arcade_be.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResultDto {
    private String sessionId;
    private Level level;
    private SessionStatus status;
    private Integer totalChallenges;
    private Integer completedChallenges;
    private Integer heartsRemaining;
    private Integer score;
    private String rewardTitle;
    private String rewardCode;
    private String rewardIcon;
    private String congratulationMessage;
    private LocalDateTime completedAt;
}
