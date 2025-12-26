package ca.fuwafuwa.gaku.Analysis

import ca.fuwafuwa.gaku.Ocr.OcrResult
import ca.fuwafuwa.gaku.Windows.Data.DisplayDataOcr

class ParsedLine(
    val text: String,
    val boundingBox: android.graphics.Rect
)

class ParsedResult(
    val words: List<ParsedWord>,
    val lines: List<ParsedLine>,
    val displayData: DisplayDataOcr,
    private val ocrTime: Long
) {
    val message: String get() = String.format("Analysis Time: %.2fs", ocrTime / 1000.0)

    override fun toString(): String {
        return "ParsedResult(words=${words.size}, time=$ocrTime)"
    }
}
