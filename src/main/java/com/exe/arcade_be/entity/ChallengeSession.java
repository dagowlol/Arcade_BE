package com.exe.arcade_be.entity;

import com.exe.arcade_be.enums.Level;
import com.exe.arcade_be.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenge_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeSession {

    @Id
    @Column(length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(nullable = false)
    private Integer totalChallenges;

    @Builder.Default
    private Integer currentChallengeIndex = 0;

    @Builder.Default
    private Integer hearts = 3;

    @Builder.Default
    private Integer maxHearts = 3;

    @Builder.Default
    private Integer score = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private String rewardTitle;
    private String rewardCode;
    private String rewardIcon;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
