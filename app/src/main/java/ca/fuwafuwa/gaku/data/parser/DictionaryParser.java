package ca.fuwafuwa.gaku.data.parser;

/**
 * Backend-agnostic dictionary parser contract.
 *
 * @param <TInput> parser input format (e.g. XmlPullParser, InputStream, ZipInputStream)
 */
public interface DictionaryParser<TInput> {
    void parse(TInput input) throws Exception;
}
