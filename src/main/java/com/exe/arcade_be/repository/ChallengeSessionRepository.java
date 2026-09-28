package com.exe.arcade_be.repository;

import com.exe.arcade_be.entity.ChallengeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeSessionRepository extends JpaRepository<ChallengeSession, String> {
}
