package ca.fuwafuwa.gaku.legacy.furigana;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

import ca.fuwafuwa.gaku.Constants;
import ca.fuwafuwa.gaku.legacy.core.DatabaseHelper;

public class PitchAccentDatabaseHelper extends DatabaseHelper {

    private static final String TAG = PitchAccentDatabaseHelper.class.getName();
    public static final String DATABASE_NAME = Constants.JM_DICT_FURIGANA_DATABASE_NAME;
    private static final int DATABASE_VERSION = 1;

    private static PitchAccentDatabaseHelper instance;
    private Context mContext;

    private PitchAccentDatabaseHelper(Context context) {
        // Path logic consistent with JmDatabaseHelper
        super(context, String.format("%s/%s", context.getFilesDir().getAbsolutePath(), DATABASE_NAME), null,
                DATABASE_VERSION);
        mContext = context;
    }

    public static synchronized PitchAccentDatabaseHelper instance(Context context) {
        if (instance == null) {
            instance = new PitchAccentDatabaseHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        // We expect this DB to be pre-populated or imported,
        // but if we need to create it from scratch:
        try {
            TableUtils.createTable(connectionSource, PitchAccent.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        // Migration logic
    }

    @Override
    public void deleteDatabase() {
        mContext.deleteDatabase(String.format("%s/%s", mContext.getFilesDir().getAbsolutePath(), DATABASE_NAME));
    }

    public Dao<PitchAccent, Integer> getPitchAccentDao() throws SQLException {
        return getDao(PitchAccent.class);
    }

    @Override
    public <T> Dao<T, Integer> getDbDao(Class clazz) throws SQLException {
        return getDao(clazz);
    }
}
