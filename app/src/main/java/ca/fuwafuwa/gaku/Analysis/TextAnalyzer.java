package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.google.mlkit.vision.text.Text;

import ca.fuwafuwa.gaku.Database.JmDictDatabase.JmDatabaseHelper;
import ca.fuwafuwa.gaku.Database.JmDictDatabase.Models.EntryOptimized;
import ca.fuwafuwa.gaku.Database.JmDictFurigana.PitchAccent;
import ca.fuwafuwa.gaku.Database.JmDictFurigana.PitchAccentDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.LangUtils;
import ca.fuwafuwa.gaku.Windows.Data.DisplayDataOcr;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TextAnalyzer {

    private static final String TAG = TextAnalyzer.class.getName();
    private Tokenizer tokenizer;
    private Context context;
    private UserDatabaseHelper userDbHelper;
    private JmDatabaseHelper jmDbHelper;
    private ca.fuwafuwa.gaku.Database.Yomitan.YomitanDatabaseHelper yomitanDbHelper;
    private PitchAccentDatabaseHelper pitchDbHelper;

    public TextAnalyzer(Context context) {
        this.context = context;
        // Initialize Kuromoji (can be slow, might want to do this async/singleton)
        this.tokenizer = new Tokenizer();
        this.userDbHelper = UserDatabaseHelper.instance(context);
        this.jmDbHelper = JmDatabaseHelper.instance(context);
        this.yomitanDbHelper = ca.fuwafuwa.gaku.Database.Yomitan.YomitanDatabaseHelper.getInstance(context);
        this.pitchDbHelper = PitchAccentDatabaseHelper.instance(context);
    }

    /**
     * Processes ML Kit Text blocks and returns a list of ParsedWords with layout
     * info.
     */
    public ParsedResult analyze(Text mlKitText, DisplayDataOcr displayData, long ocrTime) {
        List<ParsedWord> parsedWords = new ArrayList<>();
        List<ParsedLine> parsedLines = new ArrayList<>();

        for (Text.TextBlock block : mlKitText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                parsedLines.add(new ParsedLine(line.getText(), line.getBoundingBox()));
                parsedWords.addAll(processLine(line));
            }
        }

        return new ParsedResult(parsedWords, parsedLines, displayData, ocrTime);
    }

    private List<ParsedWord> processLine(Text.Line line) {
        String lineText = line.getText();
        List<Token> tokens = tokenizer.tokenize(lineText);
        List<ParsedWord> words = new ArrayList<>();

        int charIndex = 0;
        List<Text.Symbol> symbols = new ArrayList<>();
        for (Text.Element element : line.getElements()) {
            symbols.addAll(element.getSymbols());
        }

        for (Token token : tokens) {
            String surface = token.getSurface();
            String reading = token.getReading(); // This is Katakana usually
            String baseForm = token.getBaseForm();

            // Calculate bounding box for this token using character-level symbols
            Rect tokenRect = calculateTokenRect(symbols, charIndex, surface.length());
            charIndex += surface.length();

            ParsedWord word = new ParsedWord(surface, reading, baseForm, tokenRect);
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

            // 2. Look up meaning in JmDict
            try {
                // Try surface first, then baseForm, then readings
                String hiraganaReading = LangUtils.Companion.ConvertKanatanaToHiragana(reading);

                EntryOptimized entry = jmDbHelper.getDao(EntryOptimized.class).queryBuilder()
                        .where().eq("kanji", surface)
                        .or().eq("kanji", baseForm)
                        .or().like("readings", "%" + reading + "%")
                        .or().like("readings", "%" + hiraganaReading + "%")
                        .queryForFirst();

                if (entry != null) {
                    String meaningsStr = entry.getMeanings();
                    if (meaningsStr != null) {
                        word.setMeanings(new ArrayList<>(Arrays.asList(meaningsStr.split("\ufffc"))));
                    }
                    if (word.getReading() == null || word.getReading().isEmpty()) {
                        word.setPitchPattern(entry.getReadings());
                    }
                }

                // 2b. Also look up in Yomitan DB
                List<ca.fuwafuwa.gaku.Database.Yomitan.YomitanTerm> yomitanTerms = yomitanDbHelper.getTermDao()
                        .queryBuilder()
                        .where().eq("kanji", surface)
                        .or().eq("kanji", baseForm)
                        .or().eq("reading", reading)
                        .query();

                if (!yomitanTerms.isEmpty()) {
                    List<String> combinedMeanings = word.getMeanings();
                    if (combinedMeanings == null)
                        combinedMeanings = new ArrayList<>();
                    for (ca.fuwafuwa.gaku.Database.Yomitan.YomitanTerm yt : yomitanTerms) {
                        combinedMeanings.add("[" + yt.getReading() + "] " + yt.getMeanings());
                    }
                    word.setMeanings(combinedMeanings);
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

    /**
     * Maps a range of characters in the line string to the union of bounding boxes
     * of the corresponding ML Kit Symbols (characters).
     */
    private Rect calculateTokenRect(List<Text.Symbol> symbols, int startIndex, int length) {
        Rect unionRect = null;
        int accumulatedTextLen = 0;

        for (Text.Symbol symbol : symbols) {
            String symbolText = symbol.getText();
            int symbolStart = accumulatedTextLen;
            int symbolEnd = symbolStart + symbolText.length();

            // Check intersection
            int overlapStart = Math.max(startIndex, symbolStart);
            int overlapEnd = Math.min(startIndex + length, symbolEnd);

            if (overlapStart < overlapEnd) {
                Rect symbolRect = symbol.getBoundingBox();
                if (symbolRect != null) {
                    if (unionRect == null) {
                        unionRect = new Rect(symbolRect);
                    } else {
                        unionRect.union(symbolRect);
                    }
                }
            }
            accumulatedTextLen += symbolText.length();
        }

        if (unionRect == null) {
            return new Rect(0, 0, 0, 0); // Fallback
        }
        return unionRect;
    }
}
