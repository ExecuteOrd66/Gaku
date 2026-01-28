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
            // Using the high-level parse method we implemented in JitenApiClient
            List<JitenDTOs.DeckWordDto> deckWords = apiClient.parse(text);

            if (deckWords == null) {
                return parsedWords;
            }

            for (JitenDTOs.DeckWordDto deckWord : deckWords) {
                // If wordId is 0, it's likely a gap/punctuation that couldn't be parsed
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
                    // Fallback
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
        String baseForm = "";

        if (wordDetails.mainReading != null) {
            reading = wordDetails.mainReading.text;
        }

        ParsedWord word = new ParsedWord(surface, reading, baseForm, null);

        // CRITICAL: Map IDs into metadata so ReviewController can find them for SRS
        // buttons
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

        if (wordDetails.pitchAccents != null) {
            StringBuilder sb = new StringBuilder();
            for (Integer p : wordDetails.pitchAccents) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(p);
            }
            word.setPitchPattern(sb.toString());
        }

        if (wordDetails.knownStates != null && !wordDetails.knownStates.isEmpty()) {
            // knownStates: [0: New, 1: Young, 2: Mature, 3: Blacklisted, 4: Due, 5:
            // Mastered]
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
}