package ca.fuwafuwa.gaku.Network;

import java.util.List;

public class JitenDTOs {

    public static class DeckWordDto {
        public long deckWordId;
        public int deckId;
        public int wordId;
        public String originalText;
        public int readingIndex;
        public int occurrences;
        public List<String> conjugations;
    }

    public static class WordDto {
        public int wordId;
        public ReadingDto mainReading;
        public List<ReadingDto> alternativeReadings;
        public List<String> partsOfSpeech;
        public List<DefinitionDto> definitions;
        public int occurrences;
        public List<Integer> pitchAccents;
        public List<Integer> knownStates;
    }

    public static class ReadingDto {
        public String text;
        public int readingType; // 0 = Reading, 1 = KanaReading
        public int readingIndex;
        public int frequencyRank;
        public double frequencyPercentage;
        public int usedInMediaAmount;
    }

    public static class DefinitionDto {
        public int index;
        public List<String> meanings;
        public List<String> partsOfSpeech;
    }
}
