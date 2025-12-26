package ca.fuwafuwa.gaku.Database.User;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "UserWord")
public class UserWord {

    @DatabaseField(generatedId = true)
    private Integer id;

    @DatabaseField(uniqueCombo = true, index = true)
    private String text; // Surface form or Kanji

    @DatabaseField(uniqueCombo = true)
    private String reading; // Kana reading

    @DatabaseField
    private int status; // 0=Unknown, 1=Learning, 2=Known, 3=Mature, 4=Dismissed

    @DatabaseField
    private long timestamp; // For sync

    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_LEARNING = 1;
    public static final int STATUS_KNOWN = 2;
    public static final int STATUS_MATURE = 3;
    public static final int STATUS_DISMISSED = 4;

    public UserWord() {
        // ORMLite needs a no-arg constructor
    }

    public UserWord(String text, String reading, int status) {
        this.text = text;
        this.reading = reading;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    public Integer getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getReading() {
        return reading;
    }

    public void setReading(String reading) {
        this.reading = reading;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
