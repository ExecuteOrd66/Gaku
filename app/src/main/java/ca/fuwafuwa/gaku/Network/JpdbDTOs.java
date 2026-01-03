package ca.fuwafuwa.gaku.Network;

import java.util.List;

public class JpdbDTOs {

    public static class ParseRequest {
        public List<String> text;
        public List<String> token_fields;
        public List<String> vocabulary_fields;

        public ParseRequest(List<String> text, List<String> token_fields, List<String> vocabulary_fields) {
            this.text = text;
            this.token_fields = token_fields;
            this.vocabulary_fields = vocabulary_fields;
        }
    }

    public static class ParseResponse {
        public List<List<List<Object>>> tokens; // [ [ [vocab_index, pos, len, furigana], ... ] ]
        public List<List<Object>> vocabulary; // [ [vid, sid, rid, ...], ... ]
    }

    // Helper to request fields
    public static final String[] TOKEN_FIELDS = { "vocabulary_index", "position", "length", "furigana" };
    public static final String[] VOCAB_FIELDS = {
            "vid", "sid", "rid", "spelling", "reading",
            "card_state", "meanings", "part_of_speech", "pitch_accent"
    };
    // Note: meanings_chunks in TS code, but swagger/logic implies meaning
    // structure.
    // Typescript used meanings_chunks and meanings_part_of_speech.
    // I should match TS usage for consistency if mimicking it:
    // "meanings_chunks", "meanings_part_of_speech"

    public static final String[] VOCAB_FIELDS_REQUEST = {
            "vid", "sid", "rid", "spelling", "reading",
            "card_state", "meanings_chunks", "meanings_part_of_speech", "pitch_accent"
    };
}
