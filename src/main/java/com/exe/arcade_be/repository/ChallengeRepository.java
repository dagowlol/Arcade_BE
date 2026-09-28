package com.exe.arcade_be.repository;

import com.exe.arcade_be.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    List<Challenge> findBySessionIdOrderBySequenceIndexAsc(String sessionId);
}
