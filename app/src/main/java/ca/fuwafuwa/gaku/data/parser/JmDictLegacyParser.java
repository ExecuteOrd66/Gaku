package ca.fuwafuwa.gaku.data.parser;

import ca.fuwafuwa.gaku.legacy.core.IDatabaseHelper;
import org.xmlpull.v1.XmlPullParser;

/**
 * Temporary replacement for the removed XmlParsers.JmDict.JmParser.
 *
 * This keeps old generator code compiling while the app migrates to Room/Yomitan.
 */
public class JmDictLegacyParser implements XmlDictionaryParser {

    private final IDatabaseHelper dbHelper;

    public JmDictLegacyParser(IDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public void parse(XmlPullParser input) {
        throw new UnsupportedOperationException(
                "Legacy JMdict XML parser is no longer bundled. " +
                "Use the Room/Yomitan importer under ca.fuwafuwa.gaku.data.importer."
        );
    }
}
