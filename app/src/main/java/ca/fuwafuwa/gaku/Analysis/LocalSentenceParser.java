package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.fuwafuwa.gaku.Database.JmDictDatabase.JmDatabaseHelper;
import ca.fuwafuwa.gaku.Database.JmDictDatabase.Models.EntryOptimized;
import ca.fuwafuwa.gaku.Database.JmDictFurigana.PitchAccent;
import ca.fuwafuwa.gaku.Database.JmDictFurigana.PitchAccentDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.Deinflictor.DeinflectionInfo;
import ca.fuwafuwa.gaku.Deinflictor.Deinflector;
import ca.fuwafuwa.gaku.LangUtils;

public class LocalSentenceParser implements SentenceParser {

    private static final String TAG = LocalSentenceParser.class.getName();

    private Tokenizer tokenizer;
    private UserDatabaseHelper userDbHelper;
    private JmDatabaseHelper jmDbHelper;
    private PitchAccentDatabaseHelper pitchDbHelper;
    private Deinflector deinflector;

    public LocalSentenceParser(Context context) {
        this.tokenizer = new Tokenizer();
        this.userDbHelper = UserDatabaseHelper.instance(context);
        this.jmDbHelper = JmDatabaseHelper.instance(context);
        this.pitchDbHelper = PitchAccentDatabaseHelper.instance(context);
        this.deinflector = new Deinflector(context);
    }

    @Override
    public List<ParsedWord> parse(String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        List<ParsedWord> words = new ArrayList<>();

        int charIndex = 0;

        for (Token token : tokens) {
            String surface = token.getSurface();
            String reading = token.getReading(); // This is Katakana usually
            String baseForm = token.getBaseForm();

            // We don't have the full text layout info here (mlKit Text.Element symbols),
            // so we'll set a dummy Rect or null. TextAnalyzer will need to handle mapping
            // if needed,
            // but for now let's assume this parser is just for the linguistic data.
            // Wait, TextAnalyzer *was* doing the rect mapping.
            // The method signature for parse takes just text.
            // If we want to preserve the rect mapping logic, we might need to pass in the
            // ML Kit line or symbols.
            // However, the Jiten API definitely won't return rects.
            // So the design should probably be: Parser returns linguistic words,
            // TextAnalyzer maps them to rects.
            // BUT TextAnalyzer's mapping logic depends on tokenization length matching
            // source text.
            // Let's assume for now we just return ParsedWords with null rects and let the
            // caller handle it or
            // just use this for the text processing part.

            ParsedWord word = new ParsedWord(surface, reading, baseForm, null);
            word.setPos(token.getPartOfSpeechLevel1());

            // 1. Look up status in User DB
            try {
                UserWord userWord = userDbHelper.getUserWordDao().queryBuilder()
                        .where().eq("text", surface).queryForFirst();
                if (userWord != null) {
                    word.setStatus(userWord.getStatus());
                } else {
                    word.setStatus(UserWord.STATUS_UNKNOWN);
                }
            } catch (SQLException e) {
                Log.e(TAG, "Failed to query user word", e);
            }

            // 2. Look up meaning in JmDict with legacy ranking
            try {
                String hiraganaReading = LangUtils.Companion.ConvertKanatanaToHiragana(reading);

                List<EntryOptimized> candidates = jmDbHelper.getDao(EntryOptimized.class).queryBuilder()
                        .where().eq("kanji", surface)
                        .or().eq("kanji", baseForm)
                        .or().like("readings", "%" + reading + "%")
                        .or().like("readings", "%" + hiraganaReading + "%")
                        .query();

                // If no candidates found, try manual deinflection (legacy fallback)
                if (candidates.isEmpty()) {
                    List<DeinflectionInfo> deinflections = deinflector.getPotentialDeinflections(surface);
                    for (DeinflectionInfo deinf : deinflections) {
                        List<EntryOptimized> deinfCandidates = jmDbHelper.getDao(EntryOptimized.class).queryBuilder()
                                .where().eq("kanji", deinf.getWord()).query();

                        // Validate POS for deinflections (legacy logic)
                        for (EntryOptimized entry : deinfCandidates) {
                            if (validateDeinflectionPOS(deinf, entry)) {
                                candidates.add(entry);
                            }
                        }
                    }
                }

                EntryOptimized bestEntry = rankCandidates(candidates, surface, baseForm, reading, hiraganaReading);

                if (bestEntry != null) {
                    word.setDictionary(bestEntry.getDictionary());
                    String meaningsStr = bestEntry.getMeanings();
                    if (meaningsStr != null) {
                        word.setMeanings(new ArrayList<>(Arrays.asList(meaningsStr.split("\ufffc"))));
                    }
                    String meaningPosStr = bestEntry.getPos();
                    if (meaningPosStr != null) {
                        word.setMeaningPos(new ArrayList<>(Arrays.asList(meaningPosStr.split("\ufffc"))));
                    }
                    if (word.getReading() == null || word.getReading().isEmpty()) {
                        word.setPitchPattern(bestEntry.getReadings());
                    }
                }
            } catch (SQLException e) {
                Log.e(TAG, "Failed to query dictionaries", e);
            }

            // 3. Look up pitch accent in JmDictFurigana
            try {
                String hiraganaReading = LangUtils.Companion.ConvertKanatanaToHiragana(reading);
                PitchAccent pitch = pitchDbHelper.getPitchAccentDao().queryBuilder()
                        .where().eq("expression", surface).and().eq("reading", hiraganaReading).queryForFirst();
                if (pitch == null) {
                    pitch = pitchDbHelper.getPitchAccentDao().queryBuilder()
                            .where().eq("expression", baseForm).and().eq("reading", hiraganaReading).queryForFirst();
                }
                if (pitch != null) {
                    word.setPitchPattern(pitch.getPitchPattern());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to query PitchAccent", e);
            }

            words.add(word);
        }

        return words;
    }

    private EntryOptimized rankCandidates(List<EntryOptimized> candidates, String surface, String baseForm,
            String reading, String hiraganaReading) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        EntryOptimized bestMatch = null;
        int bestScore = Integer.MAX_VALUE;

        for (EntryOptimized candidate : candidates) {
            int score = calculateScore(candidate, surface, baseForm, reading, hiraganaReading);
            if (score < bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }

        return bestMatch;
    }

    private int calculateScore(EntryOptimized entry, String surface, String baseForm, String reading,
            String hiraganaReading) {
        // Implementation of legacy ranking logic
        // 0. Exact match on surface or baseForm is better than reading match
        int matchPriority = 100;
        if (entry.getKanji().equals(surface) || entry.getKanji().equals(baseForm)) {
            matchPriority = 0;
        } else if (entry.getReadings().contains(reading) || entry.getReadings().contains(hiraganaReading)) {
            matchPriority = 50;
        }

        // 1. Primary entry status (legacy getEntryPriority)
        int entryPriority = entry.isPrimaryEntry() ? 0 : 1;

        // 2. Dictionary priority (legacy getDictPriority)
        int dictPriority = 2; // Default
        if (ca.fuwafuwa.gaku.Constants.JMDICT_DATABASE_NAME.equals(entry.getDictionary())) {
            dictPriority = 0;
        }

        // 3. Frequency tags (legacy getPriority)
        int freqPriority = getFrequencyPriority(entry);

        // Combine scores (lower is better, hierarchy: match > dict > entry > freq)
        return (matchPriority * 1000000) + (dictPriority * 100000) + (entryPriority * 10000) + freqPriority;
    }

    private int getFrequencyPriority(EntryOptimized entry) {
        String[] priorities = entry.getPriorities().split(",");
        int lowestPriority = 1000; // Default high

        for (String priority : priorities) {
            int pri = 1000;
            if (priority.startsWith("nf") && priority.length() > 2) {
                try {
                    pri = Integer.parseInt(priority.substring(2));
                } catch (NumberFormatException ignored) {
                }
            } else if (priority.equals("news1")) {
                pri = 60;
            } else if (priority.equals("news2")) {
                pri = 70;
            } else if (priority.equals("ichi1")) {
                pri = 80;
            } else if (priority.equals("ichi2")) {
                pri = 90;
            } else if (priority.equals("spec1")) {
                pri = 100;
            } else if (priority.equals("spec2")) {
                pri = 110;
            } else if (priority.equals("gai1")) {
                pri = 120;
            } else if (priority.equals("gai2")) {
                pri = 130;
            }
            if (pri < lowestPriority) {
                lowestPriority = pri;
            }
        }
        return lowestPriority;
    }

    private boolean validateDeinflectionPOS(DeinflectionInfo deinf, EntryOptimized entry) {
        if (deinf.getType() == 0xFF)
            return true; // Original word
        String entryPos = entry.getPos();
        return (deinf.getType() & 1) != 0 && entryPos.contains("v1") ||
                (deinf.getType() & 2) != 0 && entryPos.contains("v5") ||
                (deinf.getType() & 4) != 0 && entryPos.contains("adj-i") ||
                (deinf.getType() & 8) != 0 && entryPos.contains("vk") ||
                (deinf.getType() & 16) != 0 && entryPos.contains("vs-");
    }
}
