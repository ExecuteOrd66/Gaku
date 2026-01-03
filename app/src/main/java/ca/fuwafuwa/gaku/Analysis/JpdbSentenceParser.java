package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.Network.JpdbApiClient;
import ca.fuwafuwa.gaku.Network.JpdbDTOs;

public class JpdbSentenceParser implements SentenceParser {

    private static final String TAG = "JpdbSentenceParser";
    private JpdbApiClient apiClient;

    public JpdbSentenceParser(Context context) {
        this.apiClient = JpdbApiClient.getInstance(context);
    }

    @Override
    public List<ParsedWord> parse(String text) {
        List<ParsedWord> parsedWords = new ArrayList<>();
        try {
            JpdbDTOs.ParseResponse response = apiClient.parse(text);
            if (response == null || response.tokens == null || response.tokens.isEmpty()) {
                return parsedWords;
            }

            // tokens[0] corresponds to the first string in the request input list
            List<List<Object>> tokensForText = response.tokens.get(0);

            for (List<Object> tokenTuple : tokensForText) {
                // [vocab_index, pos, length, furigana]
                int vocabIndex = ((Number) tokenTuple.get(0)).intValue();
                // We ignore position/length here as TextAnalyzer re-calculates rects based on
                // chars.
                // Using spelling from vocab is safer.

                if (vocabIndex >= response.vocabulary.size())
                    continue;

                List<Object> vocabTuple = response.vocabulary.get(vocabIndex);
                // Schema: [vid, sid, rid, spelling, reading, card_state, meanings_chunks,
                // meanings_pos, pitch_accent]

                String spelling = (String) vocabTuple.get(3);
                String reading = (String) vocabTuple.get(4);

                ParsedWord word = new ParsedWord(spelling, reading, spelling, null);

                // State
                Object stateObj = vocabTuple.get(5);
                word.setStatus(mapJpdbStatus(stateObj));

                // Meanings
                // meanings_chunks is List<List<String>> (glosses per meaning)
                // meanings_pos is List<List<String>> (POS per meaning)

                // We need to flatten or just take the first meaning's glosses?
                // ParsedWord expects List<String> meanings.
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
                                // Add unique POS?
                                if (!userPos.contains(pos.toString())) {
                                    userPos.add(pos.toString());
                                }
                            }
                        }
                    }
                }
                word.setMeaningPos(userPos);

                // Pitch Accent
                // JPDB returns pitch_accent as string[]? ["L", "H", "H"...] or similar?
                // TS Definition: string[] | null
                if (vocabTuple.get(8) instanceof List) {
                    List<?> pitches = (List<?>) vocabTuple.get(8);
                    StringBuilder pitchStr = new StringBuilder();
                    for (Object p : pitches) {
                        // Gaku expects... uh, typically numeric patterns or string rep?
                        // Local parser put "readings" there which usually has pitch info??
                        // Jiten provided numeric indices.
                        // JPDB provides?? Let's assume text representation for now.
                        // Actually TS says "string[]".
                        pitchStr.append(p.toString());
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

    private int mapJpdbStatus(Object stateObj) {
        if (stateObj == null)
            return UserWord.STATUS_UNKNOWN;

        // stateObj is ['state', ...] or just 'state'?
        // TS type: ApiCardState is a Tuple: ['new'] or ['locked', 'new'] etc.
        // It's a List<String>.

        if (stateObj instanceof List) {
            List<?> stateList = (List<?>) stateObj;
            if (stateList.isEmpty())
                return UserWord.STATUS_UNKNOWN;

            String primaryState = stateList.get(0).toString();
            // Order of tuple matters?
            // ['new']
            // ['redundant', 'learning']
            // ['locked', 'new']

            // If first is 'locked' or 'redundant', check second?
            if (primaryState.equals("locked") || primaryState.equals("redundant")) {
                if (stateList.size() > 1) {
                    primaryState = stateList.get(1).toString();
                }
            }

            switch (primaryState) {
                case "new":
                    return UserWord.STATUS_UNKNOWN;
                case "learning":
                    return UserWord.STATUS_LEARNING;
                case "known":
                    return UserWord.STATUS_KNOWN;
                case "never-forget":
                    return UserWord.STATUS_MASTERED;
                case "due":
                    return UserWord.STATUS_DUE;
                case "failed":
                    return UserWord.STATUS_DUE; // or learning?
                case "suspended":
                    return UserWord.STATUS_DISMISSED;
                case "blacklisted":
                    return UserWord.STATUS_DISMISSED;
                default:
                    return UserWord.STATUS_UNKNOWN;
            }
        }

        return UserWord.STATUS_UNKNOWN;
    }
}
