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
        SentenceParser parser = parserFactory.getParser();
        List<ParsedWord> words = parser.parse(lineText);

        int charIndex = 0;
        List<Text.Symbol> symbols = new ArrayList<>();
        for (Text.Element element : line.getElements()) {
            symbols.addAll(element.getSymbols());
        }

        List<ParsedWord> resultWords = new ArrayList<>();

        for (ParsedWord word : words) {
            String surface = word.getSurface();
            // Calculate bounding box for this token using character-level symbols
            // Note: This assumes the parser returns tokens that sequentially match the
            // input string.
            // If Jiten API normalizes text (e.g. half-width to full-width), indices might
            // misalign.
            // For now assuming fidelity.
            Rect tokenRect = calculateTokenRect(symbols, charIndex, surface.length());
            charIndex += surface.length();

            // Create new word with rect (ParsedWord fields are private with no setters for
            // Rect...
            // wait, ParsedWord constructor takes Rect.
            // I can't set Rect on existing word if there is no setter.
            // ParsedWord has no setter for boundingBox.
            // So I must recreate it or add a setter.
            // Recreating is safer/cleaner given the existing class structure.

            ParsedWord newWord = new ParsedWord(surface, word.getReading(), word.getLemma(), tokenRect);
            newWord.setStatus(word.getStatus());
            newWord.setPitchPattern(word.getPitchPattern());
            newWord.setMeanings(word.getMeanings());
            newWord.setPos(word.getPos());
            newWord.setDictionary(word.getDictionary());
            newWord.setMeaningPos(word.getMeaningPos());

            resultWords.add(newWord);
        }

        return resultWords;
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
