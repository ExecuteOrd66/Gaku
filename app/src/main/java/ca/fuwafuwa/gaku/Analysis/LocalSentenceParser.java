package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.util.Log;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ca.fuwafuwa.gaku.data.AppDatabase;
import ca.fuwafuwa.gaku.data.Definition;
import ca.fuwafuwa.gaku.data.Dictionary;
import ca.fuwafuwa.gaku.data.Term;
import ca.fuwafuwa.gaku.legacy.user.UserDatabaseHelper;
import ca.fuwafuwa.gaku.legacy.user.UserWord;
import ca.fuwafuwa.gaku.Deinflictor.DeinflectionInfo;
import ca.fuwafuwa.gaku.Deinflictor.Deinflector;
import ca.fuwafuwa.gaku.LangUtils;

public class LocalSentenceParser implements SentenceParser {

    private static final String TAG = "LocalSentenceParser";

    private Tokenizer tokenizer;
    private UserDatabaseHelper userDbHelper;
    private AppDatabase appDatabase;
    private Deinflector deinflector;

    public LocalSentenceParser(Context context) {
        this.tokenizer = new Tokenizer();
        this.userDbHelper = UserDatabaseHelper.instance(context);
        this.appDatabase = AppDatabase.Companion.getDatabase(context);
        this.deinflector = new Deinflector(context);
    }

    @Override
    public List<ParsedWord> parse(String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        List<ParsedWord> words = new ArrayList<>();

        List<Long> activeDictIds = appDatabase.dictionaryDao().getAllDictionaries().stream()
                .filter(Dictionary::isEnabled)
                .map(Dictionary::getId)
                .collect(Collectors.toList());

        for (Token token : tokens) {
            String surface = token.getSurface();
            String rawReading = token.getReading();
            String baseForm = token.getBaseForm();

            String displayReading;
            if (rawReading == null || rawReading.equals("*")) {
                displayReading = surface;
            } else {
                displayReading = LangUtils.Companion.ConvertKanatanaToHiragana(rawReading);
            }

            ParsedWord word = new ParsedWord(surface, displayReading, baseForm, null);
            word.setPos(token.getPartOfSpeechLevel1());

            // 1. Look up status in User DB
            try {
                UserWord userWord = userDbHelper.getUserWordDao().queryBuilder()
                        .where().eq("text", surface).queryForFirst();
                if (userWord != null) {
                    word.setStatus(userWord.getStatus());
                }
            } catch (SQLException e) {
                Log.e(TAG, "User DB query failed", e);
            }

            // 2. Look up all matching terms in AppDatabase (Yomitan)
            if (!activeDictIds.isEmpty()) {
                List<Term> matchingTerms = findMatchingTerms(surface, baseForm, activeDictIds);

                if (!matchingTerms.isEmpty()) {
                    word.setDictionary("Yomitan");
                    List<String> allFormattedMeanings = new ArrayList<>();

                    for (Term term : matchingTerms) {
                        // Create a Header for each entry: 【Kanji】 (Reading) [Tags]
                        String tags = term.getTermTags().isEmpty() ? ""
                                : " [" + term.getTermTags().replace(" ", ", ") + "]";
                        String header = String.format("【%s】 (%s)%s",
                                term.getExpression(),
                                term.getReading(),
                                tags);

                        allFormattedMeanings.add(header);

                        // Add definitions for this specific term
                        List<Definition> defs = term.getDefinitions();
                        if (defs != null) {
                            for (Definition def : defs) {
                                allFormattedMeanings.add("  • " + def.getContent());
                            }
                        }
                        // Add a spacer between different dictionary terms
                        allFormattedMeanings.add("");
                    }
                    word.setMeanings(allFormattedMeanings);
                }
            }

            words.add(word);
        }

        return words;
    }

    private List<Term> findMatchingTerms(String surface, String baseForm, List<Long> dictIds) {
        // Collect all possible search variants
        java.util.Set<String> variants = new java.util.HashSet<>();
        variants.add(surface);
        if (baseForm != null)
            variants.add(baseForm);

        // Include deinflections (e.g. "soshite" -> "sosu"?)
        List<DeinflectionInfo> deinflections = deinflector.getPotentialDeinflections(surface);
        for (DeinflectionInfo info : deinflections) {
            variants.add(info.getWord());
        }

        // Query DB for everything matching any variant
        List<Term> candidates = appDatabase.termDao().findTermsByVariants(new ArrayList<>(variants), dictIds);

        // Sort candidates so the most likely match is at the top
        candidates.sort((a, b) -> {
            int scoreA = calculateCustomScore(a, surface, baseForm);
            int scoreB = calculateCustomScore(b, surface, baseForm);
            return Integer.compare(scoreA, scoreB);
        });

        // Limit to top 5 results to avoid overwhelming the popup
        return candidates.subList(0, Math.min(candidates.size(), 5));
    }

    private int calculateCustomScore(Term term, String surface, String baseForm) {
        int score = term.getScore();
        if (score <= 0)
            score = 100000; // Unranked entries

        // High priority: Exact match to the surface form
        if (term.getExpression().equals(surface)) {
            score -= 60000;
        }
        // Medium priority: Match to the tokenizer's base form (Kuromoji's guess)
        else if (baseForm != null && term.getExpression().equals(baseForm)) {
            score -= 40000;
        }
        // Lower priority: Reading match
        else if (term.getReading().equals(surface)) {
            score -= 20000;
        }

        return score;
    }
}