package ca.fuwafuwa.gaku.Network;

import java.util.Collections;
import java.util.List;

public class JitenDTOs {

    public static class ReaderParseRequest {
        public List<String> text;

        public ReaderParseRequest(String t) {
            this.text = java.util.Collections.singletonList(t);
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

    public static class ReaderParseResponse {
        public List<List<DeckWordDto>> tokens;
        // The API also sends "vocabulary", but we only need "tokens" for the parser
    }

    public static class DeckWordDto {
        public int wordId;
        public int readingIndex;
        public int start;
        public int end;
        public int length;
    }

    public static class WordDto {
        public int wordId;
        public ReadingDto mainReading;
        public List<ReadingDto> alternativeReadings; // Added to find pure kana
        public List<DefinitionDto> definitions;
        public List<Integer> pitchAccents;
        public List<Integer> knownStates;
    }

    public static class ReadingDto {
        public String text;
        public int readingType; // 0 = Spelling (Kanji), 1 = Reading (Kana)
        public int readingIndex;
        public int frequencyRank; // Added for stars
    }

    public static class DefinitionDto {
        public List<String> meanings;
        public List<String> partsOfSpeech;
    }
}