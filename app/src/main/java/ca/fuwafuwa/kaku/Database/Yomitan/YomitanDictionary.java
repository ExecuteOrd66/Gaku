package ca.fuwafuwa.gaku.Database.Yomitan;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "yomitan_dictionaries")
public class YomitanDictionary {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false)
    private String title;

    @DatabaseField
    private String author;

    @DatabaseField
    private String description;

    @DatabaseField
    private int revision;

    public YomitanDictionary() {
    }

    public YomitanDictionary(String title, String author, String description, int revision) {
        this.title = title;
        this.author = author;
        this.description = description;
        this.revision = revision;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getRevision() {
        return revision;
    }

    public String getDescription() {
        return description;
    }
}
