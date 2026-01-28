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

    private static final android.util.LruCache<String, JitenDTOs.WordDto> wordCache = new android.util.LruCache<>(200);

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
                if (deckWord.wordId <= 0) {
                    parsedWords.add(new ParsedWord(deckWord.originalText, "", "", null));
                    continue;
                }

                String cacheKey = deckWord.wordId + "_" + deckWord.readingIndex;
                JitenDTOs.WordDto wordDetails = wordCache.get(cacheKey);

                if (wordDetails == null) {
                    wordDetails = apiClient.getWordDetails(deckWord.wordId, deckWord.readingIndex);
                    if (wordDetails != null) {
                        wordCache.put(cacheKey, wordDetails);
                    }
                }

                if (wordDetails != null) {
                    ParsedWord word = convertToParsedWord(deckWord, wordDetails);
                    parsedWords.add(word);
                } else {
                    parsedWords.add(new ParsedWord(deckWord.originalText, "", "", null));
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

        if (wordDetails.mainReading != null) {
            reading = wordDetails.mainReading.text;
        }

        ParsedWord word = new ParsedWord(surface, reading, surface, null);

        word.putMetadata("wordId", wordDetails.wordId);
        word.putMetadata("readingIndex", deckWord.readingIndex);

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

        // Convert Jiten numeric pitch accent to binary string "01001"
        if (wordDetails.pitchAccents != null && !wordDetails.pitchAccents.isEmpty()) {
            // Jiten usually returns the downstep location (mora index).
            // 0 = Heiban (Flat)
            // 1 = Atamadaka (Head high)
            // n = Nakadaka/Odaka (Downstep after nth mora)

            // We generally take the first pitch accent if multiple are listed
            int accent = wordDetails.pitchAccents.get(0);
            String pattern = convertPitchToBinary(accent, reading);
            word.setPitchPattern(pattern);
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

        word.setDictionary("Jiten.moe");
        return word;
    }

    private String convertPitchToBinary(int accent, String reading) {
        if (reading == null || reading.isEmpty())
            return "";

        // Remove small kana or prolonged sound marks if necessary for accurate mora
        // counting,
        // but for visualization mapping to the string, usually 1 char = 1 slot in the
        // graph.
        // Assuming simple 1-to-1 char mapping for now.

        int length = reading.length();
        StringBuilder sb = new StringBuilder();

        if (accent == 0) {
            // Heiban: L H H H ...
            sb.append("0");
            for (int i = 1; i < length; i++) {
                sb.append("1");
            }
        } else if (accent == 1) {
            // Atamadaka: H L L L ...
            sb.append("1");
            for (int i = 1; i < length; i++) {
                sb.append("0");
            }
        } else {
            // Nakadaka/Odaka: L H H ... (drop after accent) L ...
            sb.append("0"); // First is low
            for (int i = 1; i < length; i++) {
                if (i < accent) {
                    sb.append("1"); // High until downstep
                } else {
                    sb.append("0"); // Low after downstep
                }
            }
        }
        return sb.toString();
    }
}