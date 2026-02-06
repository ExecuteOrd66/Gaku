package ca.fuwafuwa.gaku.legacy.jmdict.models;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "EntryOptimized")
public class EntryOptimized {
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(columnName = "kanji", index = true)
    public String kanji = "";

    @DatabaseField(columnName = "reading")
    public String readings = "";

    @DatabaseField(columnName = "pos")
    public String pos = "";

    @DatabaseField(columnName = "meanings")
    public String meanings = "";

    @DatabaseField(columnName = "priorities")
    public String priorities = "";

    @DatabaseField(columnName = "dictionary")
    public String dictionary = "";

    @DatabaseField(columnName = "isPrimaryEntry")
    public boolean isPrimaryEntry = false;

    public EntryOptimized() {}

    public String getKanji() { return kanji; }
    public String getReadings() { return readings; }
    public String getPos() { return pos; }
    public String getMeanings() { return meanings; }
    public String getPriorities() { return priorities; }
    public String getDictionary() { return dictionary; }
    public boolean isPrimaryEntry() { return isPrimaryEntry; }
}
