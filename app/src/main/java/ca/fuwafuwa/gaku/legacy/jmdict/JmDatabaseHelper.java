package ca.fuwafuwa.gaku.legacy.jmdict;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;

import ca.fuwafuwa.gaku.Constants;
import ca.fuwafuwa.gaku.legacy.core.DatabaseHelper;

public class JmDatabaseHelper extends DatabaseHelper {
    private static final int DATABASE_VERSION = 1;
    private static JmDatabaseHelper instance;
    private final Context context;

    private JmDatabaseHelper(Context context) {
        super(context, Constants.JMDICT_DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    public static synchronized JmDatabaseHelper instance(Context context) {
        if (instance == null) instance = new JmDatabaseHelper(context);
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) { }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) { }

    @Override
    public void deleteDatabase() {
        context.deleteDatabase(Constants.JMDICT_DATABASE_NAME);
    }

    @Override
    public <T> Dao<T, Integer> getDbDao(Class clazz) throws SQLException {
        return getDao(clazz);
    }
}
