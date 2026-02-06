package ca.fuwafuwa.gaku;

import android.util.Xml;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.junit.Ignore;
import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

import ca.fuwafuwa.gaku.legacy.core.IDatabaseHelper;
import ca.fuwafuwa.gaku.legacy.jmdict.models.EntryOptimized;
import ca.fuwafuwa.gaku.data.parser.JmDictLegacyParser;

public class GenerateDictionary {
    class DatabaseHelperImpl implements IDatabaseHelper {

        ConnectionSource mConnectionSource;

        DatabaseHelperImpl(ConnectionSource connectionSource) {
            mConnectionSource = connectionSource;
        }

        @Override
        public <T> Dao<T, Integer> getDbDao(Class clazz) throws SQLException {
            return DaoManager.createDao(mConnectionSource, clazz);
        }
    }

    /**
     * This isn't actually a test, it generates the SQLite dictionary for the gaku
     * app
     * I'm just too lazy to have it be in another project so it's in this here as a
     * test instead. Sorry.
     *
     * Wtf fuck Android this doesn't even work.
     *
     * @throws Exception
     */
    @Ignore("Legacy utility test: JMdict XML parser was removed during Room migration")
    @Test
    public void generateDic() throws Exception {

        String dbPath = "D:/Dev/gakuFiles/gaku_edict.sqlite";
        String xmlPath = "D:/Dev/gakuFiles/JMdictOriginal.xml";
        String databaseUrl = String.format("jdbc:sqlite:%s", dbPath);

        Files.deleteIfExists(Paths.get(dbPath));

        FileInputStream mDictXml = new FileInputStream(xmlPath);
        XmlPullParser mParser = new KXmlParser();
        mParser.setInput(mDictXml, null);

        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(databaseUrl);
            TableUtils.createTable(connectionSource, EntryOptimized.class);

            DatabaseHelperImpl dbHelper = new DatabaseHelperImpl(connectionSource);

            JmDictLegacyParser jmParser = new JmDictLegacyParser(dbHelper);
            jmParser.parse(mParser);
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }
}
