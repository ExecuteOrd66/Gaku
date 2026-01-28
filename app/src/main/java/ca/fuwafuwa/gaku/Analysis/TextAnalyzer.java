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

        for (ParsedWord word : words) {
            String surface = word.getSurface();

            // Skip newlines/whitespace in the SOURCE text that aren't part of the word
            // But we must NOT skip symbols blindly.

            // Only skip leading newlines if the current parsed word is NOT a newline
            // itself.
            while (charIndex < fullText.length()
                    && fullText.charAt(charIndex) == '\n'
                    && !surface.startsWith("\n")) {
                charIndex++;
            }

            if (charIndex >= fullText.length()) {
                break;
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
        int symIdx = 0;

        if (startIndex < 0 || startIndex + length > fullText.length()) {
            return new Rect(0, 0, 0, 0);
        }

        // Count how many non-whitespace characters exist before startIndex
        // This ensures we align with the Symbol list which doesn't include whitespace.
        for (int i = 0; i < startIndex; i++) {
            if (!Character.isWhitespace(fullText.codePointAt(i))) {
                symIdx++;
            }
        }

        int charsToCollect = length;
        int currentTextIdx = startIndex;

        while (charsToCollect > 0 && symIdx < symbols.size() && currentTextIdx < fullText.length()) {

            // If the text character is whitespace, it won't have a corresponding symbol.
            // Consume the text character but do not advance symIdx.
            if (Character.isWhitespace(fullText.codePointAt(currentTextIdx))) {
                currentTextIdx++;
                charsToCollect--;
                continue;
            }

            // Map symbol to current char
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
            currentTextIdx++;
            charsToCollect--;
        }

        if (unionRect == null) {
            return new Rect(0, 0, 0, 0);
        }
        return unionRect;
    }
}