package com.exe.arcade_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mode-specific payload for language challenges (Level 2).
 * Only the fields relevant to the challenge type are populated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengePayloadDto {

    /** Vietnamese instruction shown above the puzzle. */
    private String instruction;

    /** 1 = dễ, 2 = trung bình, 3 = khó. */
    private Integer difficulty;

    /** TRUE for Level 3 memory challenges: the prompt text must auto-hide. */
    private Boolean memory;

    /** WORD_SCRAMBLE: shuffled tokens the child taps to rebuild the sentence. */
    private List<String> scrambledWords;

    /** EXTRA_WORD: the sentence tokens, one of which does not belong. */
    private List<String> sentenceWords;

    /** EXTRA_WORD: index in {@link #sentenceWords} of the intruder word. */
    private Integer extraWordIndex;

    /** FILL_BLANK: sentence with the key word replaced by a blank. */
    private String blankSentence;

    /** FILL_BLANK: candidate words to choose from (contains the correct one). */
    private List<String> blankOptions;

    // ---- Server-only answer key. Never mapped into ChallengeDto. ----

    /** FILL_BLANK: the word that correctly completes the sentence. */
    private String correctBlankWord;

    /** EXTRA_WORD: the intruder word the child has to remove. */
    private String extraWord;
}
