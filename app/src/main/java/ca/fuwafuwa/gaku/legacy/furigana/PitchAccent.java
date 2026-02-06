package ca.fuwafuwa.gaku.legacy.furigana;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "pitch_accents")
public class PitchAccent {

    @DatabaseField(generatedId = true)
    private Integer id;

    @DatabaseField(columnName = "expression")
    private String expression; // The word (kanji or kana)

    @DatabaseField(columnName = "reading")
    private String reading; // The reading, usually kana

    @DatabaseField(columnName = "pitch_pattern")
    private String pitchPattern; // e.g., "0100"

    // Optional: JmDictFurigana might have other fields, but we focus on these for
    // now.

    public PitchAccent() {
    }

    public String getExpression() {
        return expression;
    }

    public String getReading() {
        return reading;
    }

    public String getPitchPattern() {
        return pitchPattern;
    }
}
