package ca.fuwafuwa.gaku.Analysis;

import android.graphics.Rect;

import java.util.List;

import ca.fuwafuwa.gaku.Database.JmDictDatabase.Models.EntryOptimized;

/**
 * Represents a tokenized word with all necessary data for the UI.
 */
public class ParsedWord {

    private String surface;
    private String reading;
    private String lemma; // Base form
    private Rect boundingBox;
    private int status; // From UserWord.STATUS_*
    private String pitchPattern;
    private List<String> meanings;
    private String pos; // Part of speech (e.g., "noun", "verb");

    public ParsedWord(String surface, String reading, String lemma, Rect boundingBox) {
        this.surface = surface;
        this.reading = reading;
        this.lemma = lemma;
        this.boundingBox = boundingBox;
    }

    public String getSurface() {
        return surface;
    }

    public String getReading() {
        return reading;
    }

    public String getLemma() {
        return lemma;
    }

    public Rect getBoundingBox() {
        return boundingBox;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getPitchPattern() {
        return pitchPattern;
    }

    public void setPitchPattern(String pitchPattern) {
        this.pitchPattern = pitchPattern;
    }

    public List<String> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<String> meanings) {
        this.meanings = meanings;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }
}
