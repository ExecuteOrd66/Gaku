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

        // Flatten MLKit hierarchy into a single string and a list of symbols
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

        // Parse the full text (Kuromoji / Jiten / JPDB)
        SentenceParser parser = parserFactory.getParser();
        List<ParsedWord> words = parser.parse(fullText);

        int charIndex = 0;

        for (ParsedWord word : words) {
            String surface = word.getSurface();

            // FIX: Skip ALL whitespace in the SOURCE text that isn't part of the parser's
            // word.
            // MLKit often inserts spaces that the tokenizer removes. We must advance
            // charIndex
            // past any source whitespace that isn't at the start of the current surface
            // token.
            while (charIndex < fullText.length() &&
                    Character.isWhitespace(fullText.codePointAt(charIndex)) &&
                    !startsWithWhitespace(surface)) {
                charIndex++;
            }

            if (charIndex >= fullText.length()) {
                break;
            }

            // Calculate the visual bounding box for this word
            Rect tokenRect = calculateTokenRectFromGlobalSymbols(allSymbols, fullText, charIndex, surface.length());
            charIndex += surface.length();

            // Create new word with the calculated bounding box
            ParsedWord newWord = new ParsedWord(surface, word.getReading(), word.getLemma(), tokenRect);
            newWord.setStatus(word.getStatus());
            newWord.setPitchPattern(word.getPitchPattern());
            newWord.setMeanings(word.getMeanings());
            newWord.setPos(word.getPos());
            newWord.setDictionary(word.getDictionary());
            newWord.setMeaningPos(word.getMeaningPos());
            newWord.setMetadata(word.getMetadata());

            parsedWords.add(newWord);
        }

        return new ParsedResult(parsedWords, parsedLines, displayData, ocrTime);
    }

    private boolean startsWithWhitespace(String s) {
        if (s == null || s.isEmpty())
            return false;
        return Character.isWhitespace(s.codePointAt(0));
    }

    private Rect calculateTokenRectFromGlobalSymbols(List<Text.Symbol> symbols, String fullText, int startIndex,
            int length) {
        Rect unionRect = null;
        int symIdx = 0;

        // Validation
        if (startIndex < 0 || startIndex + length > fullText.length()) {
            return new Rect(0, 0, 0, 0);
        }

        // Fast-forward symIdx to match startIndex.
        // MLKit Symbols list DOES NOT contain whitespace, but fullText DOES.
        for (int i = 0; i < startIndex; i++) {
            if (!Character.isWhitespace(fullText.codePointAt(i))) {
                symIdx++;
            }
        }

        int charsToCollect = length;
        int currentTextIdx = startIndex;

        while (charsToCollect > 0 && currentTextIdx < fullText.length()) {

            // If the source text has whitespace here, we consume the text index but NOT a
            // symbol
            if (Character.isWhitespace(fullText.codePointAt(currentTextIdx))) {
                currentTextIdx++;
                charsToCollect--;
                continue;
            }

            if (symIdx < symbols.size()) {
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
            }

            currentTextIdx++;
            charsToCollect--;
        }

        if (unionRect == null) {
            return new Rect(0, 0, 0, 0);
        }
        return unionRect;
    }
}