package ca.fuwafuwa.gaku.legacy.core;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import java.sql.SQLException;

public abstract class DatabaseHelper extends OrmLiteSqliteOpenHelper implements IDatabaseHelper {
    public DatabaseHelper(Context context, String databaseName, SQLiteDatabase.CursorFactory factory, int databaseVersion) {
        super(context, databaseName, factory, databaseVersion);
    }

    @Override
    public abstract void onCreate(SQLiteDatabase database, ConnectionSource connectionSource);

    @Override
    public abstract void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion);

    public abstract void deleteDatabase();

    @Override
    public abstract <T> Dao<T, Integer> getDbDao(Class clazz) throws SQLException;
}
