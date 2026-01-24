package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import android.graphics.Rect;

import com.google.mlkit.vision.text.Text;

import ca.fuwafuwa.gaku.Windows.Data.DisplayDataOcr;

import java.util.ArrayList;
import java.util.List;

public class TextAnalyzer {

    private SentenceParserFactory parserFactory;
    private Context context;

    public TextAnalyzer(Context context) {
        this.context = context;
        this.parserFactory = SentenceParserFactory.getInstance(context);
    }

    /**
     * Processes ML Kit Text blocks and returns a list of ParsedWords with layout
     * info.
     */
    public ParsedResult analyze(Text mlKitText, DisplayDataOcr displayData, long ocrTime) {
        List<ParsedWord> parsedWords = new ArrayList<>();
        List<ParsedLine> parsedLines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        List<Text.Symbol> allSymbols = new ArrayList<>();

        for (Text.TextBlock block : mlKitText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                parsedLines.add(new ParsedLine(line.getText(), line.getBoundingBox()));
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line.getText());
                for (Text.Element element : line.getElements()) {
                    allSymbols.addAll(element.getSymbols());
                }
            }
        }

        String fullText = sb.toString();
        if (fullText.isEmpty()) {
            return new ParsedResult(parsedWords, parsedLines, displayData, ocrTime);
        }

        SentenceParser parser = parserFactory.getParser();
        long startTime = System.currentTimeMillis();
        List<ParsedWord> words = parser.parse(fullText);
        long endTime = System.currentTimeMillis();
        android.util.Log.d("TextAnalyzer",
                String.format("Batched parse of %d lines took %d ms", parsedLines.size(), (endTime - startTime)));

        int charIndex = 0;
        int symbolIndex = 0;

        for (ParsedWord word : words) {
            String surface = word.getSurface();

            // Sync charIndex with surface start, skipping newlines
            while (charIndex < fullText.length() && fullText.charAt(charIndex) == '\n') {
                charIndex++;
            }

            // Verify surface match (optional but good for debugging)
            if (charIndex + surface.length() <= fullText.length()) {
                String sub = fullText.substring(charIndex, charIndex + surface.length());
                if (!sub.equals(surface)) {
                    // This might happen if parser normalizes text.
                    // For now, we trust the index but log if needed.
                }
            }

            Rect tokenRect = calculateTokenRectFromGlobalSymbols(allSymbols, fullText, charIndex, surface.length());
            charIndex += surface.length();

            ParsedWord newWord = new ParsedWord(surface, word.getReading(), word.getLemma(), tokenRect);
            newWord.setStatus(word.getStatus());
            newWord.setPitchPattern(word.getPitchPattern());
            newWord.setMeanings(word.getMeanings());
            newWord.setPos(word.getPos());
            newWord.setDictionary(word.getDictionary());
            newWord.setMeaningPos(word.getMeaningPos());

            parsedWords.add(newWord);
        }

        return new ParsedResult(parsedWords, parsedLines, displayData, ocrTime);
    }

    private Rect calculateTokenRectFromGlobalSymbols(List<Text.Symbol> symbols, String fullText, int startIndex,
            int length) {
        Rect unionRect = null;
        int currentFullTextIdx = 0;
        int symIdx = 0;

        // Skip characters in fullText until we reach startIndex, accounting for
        // newlines that aren't in symbols
        for (int i = 0; i < startIndex; i++) {
            if (fullText.charAt(i) != '\n') {
                symIdx++;
            }
        }

        // Collect symbols for the length of the word
        int charsToCollect = length;
        while (charsToCollect > 0 && symIdx < symbols.size()) {
            // Find next non-newline char in fullText to see if it corresponds to current
            // symbol
            int nextTargetCharIdx = startIndex + (length - charsToCollect);
            while (nextTargetCharIdx < fullText.length() && fullText.charAt(nextTargetCharIdx) == '\n') {
                nextTargetCharIdx++;
            }

            if (nextTargetCharIdx >= fullText.length())
                break;

            Text.Symbol symbol = symbols.get(symIdx);
            Rect symbolRect = symbol.getBoundingBox();
            if (symbolRect != null) {
                if (unionRect == null) {
                    unionRect = new Rect(symbolRect);
                } else {
                    unionRect.union(symbolRect);
                }
            }
            symIdx++;
            charsToCollect--;
        }

        if (unionRect == null) {
            return new Rect(0, 0, 0, 0);
        }
        return unionRect;
    }

}
