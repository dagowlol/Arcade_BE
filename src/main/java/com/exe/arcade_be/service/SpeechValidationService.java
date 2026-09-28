package com.exe.arcade_be.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SpeechValidationService {

    private static final Map<String, String> NUMBER_MAP = new HashMap<>();

    static {
        NUMBER_MAP.put("1", "one");
        NUMBER_MAP.put("2", "two");
        NUMBER_MAP.put("3", "three");
        NUMBER_MAP.put("a", "one");
        NUMBER_MAP.put("an", "one");
    }

    /**
     * Normalizes speech text for fair kid-friendly matching:
     * - Lowercase & trim
     * - Remove punctuation
     * - Normalize number words / contractions
     * - Normalize common compounds (e.g. icecream -> ice cream)
     * - Normalize plural/singular where reasonable
     */
    public String normalize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String cleaned = text.toLowerCase()
                .replaceAll("[\"'`.,!?;:\\-()/\\[\\]]", " ")
                .replaceAll("\\bi'd\\b", "i would")
                .replaceAll("\\bid\\b", "i would")
                .replaceAll("\\s+", " ")
                .trim();

        // Convert standalone digits and articles
        String[] words = cleaned.split(" ");
        List<String> normalizedWords = new ArrayList<>();
        for (String w : words) {
            if (NUMBER_MAP.containsKey(w)) {
                normalizedWords.add(NUMBER_MAP.get(w));
            } else {
                normalizedWords.add(w);
            }
        }

        String result = String.join(" ", normalizedWords);

        // Normalize compound words
        result = result.replace("icecream", "ice cream")
                .replace("hotdog", "hot dog")
                .replace("hotdogs", "hot dogs")
                .replace("frenchfries", "french fries")
                .replace("chickenrice", "chicken rice")
                .replace("friedchicken", "fried chicken");

        return result.trim();
    }

    /**
     * Checks if spoken text matches the expected target sentence with kid-friendly tolerance.
     */
    public boolean isSpeechMatch(String spokenText, String expectedTarget) {
        String normSpoken = normalize(spokenText);
        String normExpected = normalize(expectedTarget);

        if (normSpoken.isEmpty()) {
            return false;
        }

        // Direct normalized match
        if (normSpoken.equals(normExpected)) {
            return true;
        }

        // Check stripped phrases (with or without 'please', 'i would like')
        String strippedExpected = stripPoliteness(normExpected);
        String strippedSpoken = stripPoliteness(normSpoken);

        if (strippedSpoken.equals(strippedExpected)) {
            return true;
        }

        // Calculate Levenshtein similarity
        double similarity = calculateSimilarity(normSpoken, normExpected);
        if (similarity >= 0.75) {
            return true;
        }

        double strippedSimilarity = calculateSimilarity(strippedSpoken, strippedExpected);
        if (strippedSimilarity >= 0.75) {
            return true;
        }

        // Check if all essential keywords are present in spoken text
        return containsAllKeywords(strippedSpoken, strippedExpected);
    }

    private String stripPoliteness(String text) {
        return text.replace("i would like", "")
                .replace("would like", "")
                .replace("i like", "")
                .replace("please", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAllKeywords(String spoken, String expected) {
        String[] expectedKeywords = expected.split(" ");
        int matched = 0;
        int totalSignificant = 0;

        for (String kw : expectedKeywords) {
            if (kw.equals("and") || kw.isEmpty()) continue;
            totalSignificant++;
            if (spoken.contains(kw) || isFuzzyContained(spoken, kw)) {
                matched++;
            }
        }

        if (totalSignificant == 0) return true;
        return ((double) matched / totalSignificant) >= 0.80;
    }

    private boolean isFuzzyContained(String sentence, String word) {
        String[] sentenceWords = sentence.split(" ");
        for (String sw : sentenceWords) {
            if (calculateSimilarity(sw, word) >= 0.80) {
                return true;
            }
        }
        return false;
    }

    public double calculateSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }
}
