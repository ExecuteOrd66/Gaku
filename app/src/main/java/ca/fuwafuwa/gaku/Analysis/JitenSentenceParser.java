package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.fuwafuwa.gaku.Network.JitenApiClient;
import ca.fuwafuwa.gaku.Network.JitenDTOs;
import ca.fuwafuwa.gaku.legacy.user.UserWord;

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
            JitenDTOs.ReaderParseResponse response = apiClient.parse(text);

            if (response == null || response.tokens == null) {
                return parsedWords;
            }

            for (List<JitenDTOs.DeckWordDto> tokenBlock : response.tokens) {
                for (JitenDTOs.DeckWordDto deckWord : tokenBlock) {

                    // Get the actual text from the input string using offsets
                    String surface = "";
                    if (deckWord.start >= 0 && deckWord.end <= text.length()) {
                        surface = text.substring(deckWord.start, deckWord.end);
                    }

                    if (deckWord.wordId <= 0) {
                        parsedWords.add(new ParsedWord(surface, "", "", null));
                        continue;
                    }

                    String cacheKey = deckWord.wordId + "_" + deckWord.readingIndex;
                    JitenDTOs.WordDto wordDetails = wordCache.get(cacheKey);

                    if (wordDetails == null) {
                        wordDetails = apiClient.getWordDetails(deckWord.wordId, deckWord.readingIndex);
                        if (wordDetails != null)
                            wordCache.put(cacheKey, wordDetails);
                    }

                    if (wordDetails != null) {
                        parsedWords.add(convertToParsedWord(surface, deckWord, wordDetails));
                    } else {
                        parsedWords.add(new ParsedWord(surface, "", "", null));
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Jiten API Parse failed", e);
        }
        return parsedWords;
    }

    private ParsedWord convertToParsedWord(String surface, JitenDTOs.DeckWordDto deckWord,
            JitenDTOs.WordDto wordDetails) {

        // 1. Full Reading Extraction
        // Jiten provides the reading in an annotated format: "日[ひ]々[び]" or "続[つづ]く"
        // The standard algorithm to get pure Hiragana is to replace all "Kanji[Kana]"
        // blocks with just "Kana".
        String rawReading = (wordDetails.mainReading != null) ? wordDetails.mainReading.text : "";

        // This regex matches: [Any non-kana characters] followed by [text inside
        // brackets]
        // and replaces it with just the [text inside brackets].
        // This handles 々, Kanji, and preserves okurigana (kana outside brackets).
        String cleanReading = rawReading.replaceAll("[^\u3040-\u309F\u30A0-\u30FF]+\\[(.*?)\\]", "$1");

        // If the reading was already pure kana (no brackets), rawReading remains
        // correct.
        if (cleanReading.isEmpty() && !rawReading.isEmpty()) {
            cleanReading = rawReading;
        }

        ParsedWord word = new ParsedWord(surface, cleanReading, surface, null);

        // 2. Pass metadata to the UI and TextAnalyzer
        word.putMetadata("start", deckWord.start);
        word.putMetadata("end", deckWord.end);
        word.putMetadata("wordId", wordDetails.wordId);
        word.putMetadata("readingIndex", deckWord.readingIndex);

        if (wordDetails.mainReading != null) {
            word.putMetadata("freqRank", wordDetails.mainReading.frequencyRank);
        }

        // 3. Jiten Word Status Mapping (Sync with Jiten SRS stages)
        if (wordDetails.knownStates != null && !wordDetails.knownStates.isEmpty()) {
            int jitenState = wordDetails.knownStates.get(0);
            switch (jitenState) {
                case 0:
                    word.setStatus(UserWord.STATUS_UNKNOWN);
                    break;
                case 1:
                    word.setStatus(UserWord.STATUS_LEARNING);
                    break; // Young
                case 2:
                    word.setStatus(UserWord.STATUS_MATURE);
                    break; // Mature
                case 3:
                    word.setStatus(UserWord.STATUS_DISMISSED);
                    break;// Blacklisted
                case 4:
                    word.setStatus(UserWord.STATUS_DUE);
                    break; // Due
                case 5:
                    word.setStatus(UserWord.STATUS_MASTERED);
                    break; // Mastered
                default:
                    word.setStatus(UserWord.STATUS_UNKNOWN);
            }
        }

        // 4. Pitch Accent Calculation (using the pure kana cleanReading)
        if (wordDetails.pitchAccents != null && !wordDetails.pitchAccents.isEmpty()) {
            int accentIndex = wordDetails.pitchAccents.get(0);
            word.setPitchPattern(convertPitchToBinary(accentIndex, cleanReading));
        }

        // 5. Definition Processing
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

        word.setDictionary("Jiten.moe");
        return word;
    }

    /**
     * Ported Logic: Counts morae (ignoring small yoon vowels) to accurately place
     * the downstep
     */
    private int countMorae(String reading) {
        if (reading == null)
            return 0;
        int count = 0;
        String smallNonMora = "ゃゅょャュョァィゥェォ";
        for (int i = 0; i < reading.length(); i++) {
            char c = reading.charAt(i);
            if (smallNonMora.indexOf(c) == -1) {
                count++;
            }
        }
        return count;
    }

    /**
     * Ported Logic: Converts Jiten accent index to Gaku binary visual pattern
     * index 0 = Heiban (Low-High...)
     * index 1 = Atamadaka (High-Low...)
     */
    private String convertPitchToBinary(int accent, String reading) {
        if (reading == null || reading.isEmpty())
            return "";

        int moraeCount = countMorae(reading);
        StringBuilder sb = new StringBuilder();

        // Binary pattern construction for Gaku Graph View:
        // 0 = Low, 1 = High
        for (int i = 1; i <= reading.length(); i++) {
            // Note: This logic simplifies mora-to-char mapping for the graph
            if (accent == 0) {
                // Heiban: Low then High onwards
                sb.append(i == 1 ? "0" : "1");
            } else if (accent == 1) {
                // Atamadaka: High then Low onwards
                sb.append(i == 1 ? "1" : "0");
            } else {
                // Nakadaka / Odaka: Low, then High until downstep
                if (i == 1)
                    sb.append("0");
                else if (i <= accent)
                    sb.append("1");
                else
                    sb.append("0");
            }
        }
        return sb.toString();
    }
}