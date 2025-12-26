package ca.fuwafuwa.gaku.Database.Yomitan;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;

public class YomitanDatabaseHelper extends OrmLiteSqliteOpenHelper {

    private static final String DATABASE_NAME = "yomitan_dicts.db";
    private static final int DATABASE_VERSION = 1;
    private static YomitanDatabaseHelper instance;

    private Dao<YomitanTerm, Integer> termDao;
    private Dao<YomitanDictionary, Integer> dictionaryDao;

    public YomitanDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized YomitanDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new YomitanDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, YomitanDictionary.class);
            TableUtils.createTable(connectionSource, YomitanTerm.class);
        } catch (SQLException e) {
            Log.e("YomitanDB", "Could not create tables", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        // Handle migration
    }

    public Dao<YomitanTerm, Integer> getTermDao() throws SQLException {
        if (termDao == null)
            termDao = getDao(YomitanTerm.class);
        return termDao;
    }

    public Dao<YomitanDictionary, Integer> getDictionaryDao() throws SQLException {
        if (dictionaryDao == null)
            dictionaryDao = getDao(YomitanDictionary.class);
        return dictionaryDao;
    }
}
