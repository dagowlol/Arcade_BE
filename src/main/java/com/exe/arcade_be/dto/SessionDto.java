package com.exe.arcade_be.dto;

import com.exe.arcade_be.enums.Level;
import com.exe.arcade_be.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {
    private String sessionId;
    private Level level;
    private Integer totalChallenges;
    private Integer currentChallengeIndex;
    private Integer hearts;
    private Integer maxHearts;
    private Integer score;
    private SessionStatus status;
    private List<ChallengeDto> challenges;
    private ChallengeDto currentChallenge;
}
