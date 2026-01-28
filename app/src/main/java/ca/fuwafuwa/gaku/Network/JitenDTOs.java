package ca.fuwafuwa.gaku.Network;

import java.util.Collections;
import java.util.List;

public class JitenDTOs {

    public static class ReaderParseRequest {
        public List<String> text;

        public ReaderParseRequest(String t) {
            this.text = Collections.singletonList(t);
        }
    }

    public static class LookupVocabularyRequest {
        public List<List<Integer>> words; // List of [wordId, readingIndex]
    }

    public static class SrsReviewRequest {
        public int wordId;
        public int readingIndex;
        public int rating; // 1: Again, 2: Hard, 3: Good, 4: Easy
    }

    public static class SetVocabularyStateRequest {
        public int wordId;
        public int readingIndex;
        public String state; // "blacklist" or "neverForget"
    }

    public static class DeckWordDto {
        public int wordId;
        public String originalText;
        public int readingIndex;
        // JitenReader uses these for positioning, but Gaku calculates its own Rects
    }

    public static class WordDto {
        public int wordId;
        public ReadingDto mainReading;
        public List<DefinitionDto> definitions;
        public List<Integer> pitchAccents;
        public List<Integer> knownStates; // [0: New, 1: Young, 2: Mature, 3: Blacklisted, 4: Due, 5: Mastered]
    }

    public static class ReadingDto {
        public String text;
    }

    public static class DefinitionDto {
        public List<String> meanings;
        public List<String> partsOfSpeech;
    }
}