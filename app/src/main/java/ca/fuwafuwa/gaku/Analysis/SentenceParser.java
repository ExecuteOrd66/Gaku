package ca.fuwafuwa.gaku.Analysis;

import java.util.List;

public interface SentenceParser {
    List<ParsedWord> parse(String text);
}
