package ca.fuwafuwa.gaku.Analysis;

import android.content.Context;
import androidx.preference.PreferenceManager;

public class SentenceParserFactory {

    private static SentenceParserFactory instance;
    private Context context;

    private SentenceParserFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SentenceParserFactory getInstance(Context context) {
        if (instance == null) {
            instance = new SentenceParserFactory(context);
        }
        return instance;
    }

    public SentenceParser getParser() {
        String backend = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_parser_backend", "jiten");

        switch (backend) {
            case "jpdb":
                return new JpdbSentenceParser(context);
            case "offline":
                return new LocalSentenceParser(context);
            case "jiten":
            default:
                return new JitenSentenceParser(context);
        }
    }

    public SentenceParser getLocalParser() {
        return new LocalSentenceParser(context);
    }
}
