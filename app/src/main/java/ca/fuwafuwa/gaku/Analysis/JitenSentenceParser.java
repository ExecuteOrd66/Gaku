package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.fuwafuwa.gaku.Network.JitenApiClient;
import ca.fuwafuwa.gaku.Network.JitenDTOs;

public class JitenSentenceParser implements SentenceParser {

    private static final String TAG = "JitenSentenceParser";
    private JitenApiClient apiClient;

    public JitenSentenceParser(Context context) {
        this.apiClient = JitenApiClient.getInstance(context);
    }

    @Override
    public List<ParsedWord> parse(String text) {
        List<ParsedWord> parsedWords = new ArrayList<>();
        try {
            List<JitenDTOs.DeckWordDto> deckWords = apiClient.parse(text);
            if (deckWords == null) {
                return parsedWords;
            }

            for (JitenDTOs.DeckWordDto deckWord : deckWords) {
                JitenDTOs.WordDto wordDetails = apiClient.getWordDetails(deckWord.wordId, deckWord.readingIndex);

                if (wordDetails != null) {
                    ParsedWord word = convertToParsedWord(deckWord, wordDetails);
                    parsedWords.add(word);
                } else {
                    // Fallback or handle missing details?
                    // For now, create a basic ParsedWord from DeckWordDto
                    ParsedWord word = new ParsedWord(deckWord.originalText, "", "", null);
                    parsedWords.add(word);
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to parse text via Jiten API", e);
        }
        return parsedWords;
    }

    private ParsedWord convertToParsedWord(JitenDTOs.DeckWordDto deckWord, JitenDTOs.WordDto wordDetails) {
        String surface = deckWord.originalText;
        String reading = "";
        String baseForm = "";

        if (wordDetails.mainReading != null) {
            reading = wordDetails.mainReading.text;
        }

        // Assuming baseForm is somewhat equivalent to the main reading or dictionary
        // form handling
        // Jiten API doesn't explicitly give "baseForm" in the same way Kuromoji does,
        // but the WordDto represents the base entry.

        ParsedWord word = new ParsedWord(surface, reading, baseForm, null);

        if (wordDetails.definitions != null) {
            List<String> meanings = new ArrayList<>();
            List<String> meaningPos = new ArrayList<>();
            for (JitenDTOs.DefinitionDto def : wordDetails.definitions) {
                if (def.meanings != null)
                    meanings.addAll(def.meanings);
                if (def.partsOfSpeech != null)
                    meaningPos.addAll(def.partsOfSpeech);
            }
            word.setMeanings(meanings);
            word.setMeaningPos(meaningPos);
        }

        if (wordDetails.pitchAccents != null) {
            // ParsedWord expects a String for pitch pattern? Or List<Integer>?
            // TextAnalyzer calls setPitchPattern(String).
            // Let's check ParsedWord.java to be sure.
            // For now, I'll assume we can format it as a string or if there's a setter for
            // list.
            // Since I can't see ParsedWord right now, I'll rely on TextAnalyzer usage.
            // TextAnalyzer: word.setPitchPattern(bestEntry.getReadings()); -> getReadings
            // is String?
            // "word.setPitchPattern(pitch.getPitchPattern());" -> pitch pattern is String?

            // Jiten returns List<Integer>. I should probably convert to comma separated
            // string or similar.
            StringBuilder sb = new StringBuilder();
            for (Integer p : wordDetails.pitchAccents) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(p);
            }
            word.setPitchPattern(sb.toString());
        }

        if (wordDetails.knownStates != null && !wordDetails.knownStates.isEmpty()) {
            int jitenState = wordDetails.knownStates.get(0);
            int mappedState = 0;

            switch (jitenState) {
                case 1:
                    mappedState = 1;
                    break; // Young -> Learning
                case 2:
                    mappedState = 3;
                    break; // Mature -> Mature
                case 3:
                    mappedState = 4;
                    break; // Blacklisted -> Dismissed
                case 4:
                    mappedState = 5;
                    break; // Due -> Due
                case 5:
                    mappedState = 6;
                    break; // Mastered -> Mastered
                default:
                    mappedState = 0;
                    break; // New -> Unknown
            }

            word.setStatus(mappedState);
        }

        word.setDictionary("Jiten.moe"); // Mark source

        return word;
    }
}
