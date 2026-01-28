package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.Network.JpdbApiClient;
import ca.fuwafuwa.gaku.Network.JpdbDTOs;

public class JpdbSentenceParser implements SentenceParser {

    private static final String TAG = "JpdbSentenceParser";
    private JpdbApiClient apiClient;

    private android.content.SharedPreferences prefs;

    public JpdbSentenceParser(Context context) {
        this.apiClient = JpdbApiClient.getInstance(context);
        this.prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public List<ParsedWord> parse(String text) {
        List<ParsedWord> parsedWords = new ArrayList<>();
        try {
            JpdbDTOs.ParseResponse response = apiClient.parse(text);
            if (response == null || response.tokens == null || response.tokens.isEmpty()) {
                return parsedWords;
            }

            // Convert text to bytes once to handle UTF-8 offsets correctly
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

            // tokens[0] corresponds to the first string in the request input list
            List<List<Object>> tokensForText = response.tokens.get(0);

            for (List<Object> tokenTuple : tokensForText) {
                // [vocab_index, pos, length, furigana]
                int vocabIndex = ((Number) tokenTuple.get(0)).intValue();
                int position = ((Number) tokenTuple.get(1)).intValue(); // Byte offset
                int length = ((Number) tokenTuple.get(2)).intValue(); // Byte length

                if (vocabIndex >= response.vocabulary.size())
                    continue;

                List<Object> vocabTuple = response.vocabulary.get(vocabIndex);
                // Schema: [vid, sid, rid, spelling, reading, card_state, meanings_chunks,
                // meanings_pos, pitch_accent, due_at]

                int vid = ((Number) vocabTuple.get(0)).intValue();
                int sid = ((Number) vocabTuple.get(1)).intValue();

                // Extract actual surface text based on token position (Byte Slicing)
                String surface = "";
                if (position >= 0 && position + length <= textBytes.length) {
                    surface = new String(textBytes, position, length, StandardCharsets.UTF_8);
                } else {
                    surface = (String) vocabTuple.get(3); // Fallback to dictionary spelling
                }

                String lemma = (String) vocabTuple.get(3);
                String reading = (String) vocabTuple.get(4);

                ParsedWord word = new ParsedWord(surface, reading, lemma, null);
                word.putMetadata("vid", vid);
                word.putMetadata("sid", sid);

                // State
                Object stateObj = vocabTuple.get(5);
                Object dueAtObj = (vocabTuple.size() > 9) ? vocabTuple.get(9) : null;
                word.setStatus(mapJpdbStatus(stateObj, dueAtObj));

                // Meanings
                List<String> userMeanings = new ArrayList<>();
                List<String> userPos = new ArrayList<>();

                if (vocabTuple.get(6) instanceof List) {
                    List<?> meaningsChunks = (List<?>) vocabTuple.get(6);
                    for (Object chunk : meaningsChunks) {
                        if (chunk instanceof List) {
                            List<?> glosses = (List<?>) chunk;
                            for (Object gloss : glosses) {
                                userMeanings.add(gloss.toString());
                            }
                        }
                    }
                }
                word.setMeanings(userMeanings);

                if (vocabTuple.get(7) instanceof List) {
                    List<?> posChunks = (List<?>) vocabTuple.get(7);
                    for (Object chunk : posChunks) {
                        if (chunk instanceof List) {
                            List<?> poses = (List<?>) chunk;
                            for (Object pos : poses) {
                                if (!userPos.contains(pos.toString())) {
                                    userPos.add(pos.toString());
                                }
                            }
                        }
                    }
                }
                word.setMeaningPos(userPos);

                // Pitch Accent: Convert ["L", "H"] to "01"
                if (vocabTuple.get(8) instanceof List) {
                    List<?> pitches = (List<?>) vocabTuple.get(8);
                    StringBuilder pitchStr = new StringBuilder();
                    for (Object p : pitches) {
                        String pVal = p.toString();
                        if ("L".equals(pVal))
                            pitchStr.append("0");
                        else if ("H".equals(pVal))
                            pitchStr.append("1");
                        else
                            pitchStr.append("0"); // Default low if unknown
                    }
                    word.setPitchPattern(pitchStr.toString());
                }

                word.setDictionary("JPDB");
                parsedWords.add(word);
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to parse text via JPDB API", e);
        }
        return parsedWords;
    }

    private int mapJpdbStatus(Object stateObj, Object dueAtObj) {
        if (stateObj == null)
            return UserWord.STATUS_UNKNOWN;

        if (stateObj instanceof List) {
            List<?> stateList = (List<?>) stateObj;
            if (stateList.isEmpty())
                return UserWord.STATUS_UNKNOWN;

            String primaryState = stateList.get(0).toString();

            if (primaryState.equals("locked") || primaryState.equals("redundant")) {
                if (stateList.size() > 1) {
                    primaryState = stateList.get(1).toString();
                }
            }

            switch (primaryState) {
                case "new":
                    return UserWord.STATUS_UNKNOWN;
                case "learning":
                case "known":
                    return determineLearningOrMastered(dueAtObj);
                case "never-forget":
                    return UserWord.STATUS_MASTERED;
                case "due":
                case "failed":
                    return UserWord.STATUS_DUE;
                case "suspended":
                case "blacklisted":
                    return UserWord.STATUS_DISMISSED;
                default:
                    return UserWord.STATUS_UNKNOWN;
            }
        }

        return UserWord.STATUS_UNKNOWN;
    }

    private int determineLearningOrMastered(Object dueAtObj) {
        if (dueAtObj instanceof Number) {
            long dueAt = ((Number) dueAtObj).longValue();
            long now = System.currentTimeMillis() / 1000;
            long secondsUntilDue = dueAt - now;

            String thresholdStr = prefs.getString("pref_young_threshold", "21");
            int thresholdDays = 21;
            try {
                thresholdDays = Integer.parseInt(thresholdStr);
            } catch (NumberFormatException e) {
                // ignore
            }
            long thresholdSeconds = thresholdDays * 24L * 60L * 60L;

            if (secondsUntilDue >= thresholdSeconds) {
                return UserWord.STATUS_MASTERED;
            } else {
                return UserWord.STATUS_LEARNING;
            }
        }
        return UserWord.STATUS_LEARNING;
    }
}