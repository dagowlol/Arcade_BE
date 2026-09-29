package com.exe.arcade_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "challenges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false)
    private Integer sequenceIndex;

    @Column(nullable = false)
    private String challengeType;

    @Column(nullable = false)
    private String speechTarget;

    @Column(nullable = false)
    private String promptAudioText;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    // Mode-specific payload (scrambled words, blank sentence, extra word index, ...)
    // Empty for challenge types that do not need it.
    @Lob
    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Builder.Default
    private Boolean completed = false;

    @Builder.Default
    private Boolean correct = false;
}
