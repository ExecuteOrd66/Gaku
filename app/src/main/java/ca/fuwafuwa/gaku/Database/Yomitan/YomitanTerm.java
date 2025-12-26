package ca.fuwafuwa.gaku.Database.Yomitan;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "yomitan_terms")
public class YomitanTerm {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false, index = true)
    private int dictionaryId;

    @DatabaseField(canBeNull = false, index = true)
    private String kanji;

    @DatabaseField(index = true)
    private String reading;

    @DatabaseField
    private String definitionTags;

    @DatabaseField
    private String deinflectionRules;

    @DatabaseField
    private int popularity;

    @DatabaseField(width = 4000)
    private String meanings; // Stored as JSON or delimited string

    @DatabaseField
    private String dictionaryTags;

    public YomitanTerm() {
    }

    public YomitanTerm(int dictionaryId, String kanji, String reading, String definitionTags,
            String deinflectionRules, int popularity, String meanings, String dictionaryTags) {
        this.dictionaryId = dictionaryId;
        this.kanji = kanji;
        this.reading = reading;
        this.definitionTags = definitionTags;
        this.deinflectionRules = deinflectionRules;
        this.popularity = popularity;
        this.meanings = meanings;
        this.dictionaryTags = dictionaryTags;
    }

    public String getKanji() {
        return kanji;
    }

    public String getReading() {
        return reading;
    }

    public String getMeanings() {
        return meanings;
    }

    public int getDictionaryId() {
        return dictionaryId;
    }

    public int getId() {
        return id;
    }

    public String getDefinitionTags() {
        return definitionTags;
    }

    public String getDeinflectionRules() {
        return deinflectionRules;
    }

    public int getPopularity() {
        return popularity;
    }

    public String getDictionaryTags() {
        return dictionaryTags;
    }
}
