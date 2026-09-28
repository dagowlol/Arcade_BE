package com.exe.arcade_be.controller;

import com.exe.arcade_be.dto.*;
import com.exe.arcade_be.service.FoodChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/food-challenge")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FoodChallengeController {

    private final FoodChallengeService foodChallengeService;

    @GetMapping("/foods")
    public ResponseEntity<List<FoodDto>> getFoods() {
        return ResponseEntity.ok(foodChallengeService.getAllFoods());
    }

    @GetMapping("/levels")
    public ResponseEntity<List<LevelDto>> getLevels() {
        return ResponseEntity.ok(foodChallengeService.getLevels());
    }

    @PostMapping("/start")
    public ResponseEntity<SessionDto> startChallenge(@RequestBody(required = false) StartChallengeRequest request) {
        if (request == null) {
            request = new StartChallengeRequest();
        }
        return ResponseEntity.ok(foodChallengeService.startSession(request));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<ValidationResultDto> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody SubmitAnswerRequest request
    ) {
        return ResponseEntity.ok(foodChallengeService.submitAnswer(sessionId, request));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDto> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(foodChallengeService.getSession(sessionId));
    }

    @GetMapping("/{sessionId}/result")
    public ResponseEntity<SessionResultDto> getResult(@PathVariable String sessionId) {
        return ResponseEntity.ok(foodChallengeService.getSessionResult(sessionId));
    }
}
