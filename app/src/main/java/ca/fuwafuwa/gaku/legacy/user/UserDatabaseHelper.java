package ca.fuwafuwa.gaku.legacy.user;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

import ca.fuwafuwa.gaku.legacy.core.DatabaseHelper;

public class UserDatabaseHelper extends DatabaseHelper {

    private static final String TAG = UserDatabaseHelper.class.getName();
    private static final String DATABASE_NAME = "gaku_user_data.db";
    private static final int DATABASE_VERSION = 1;

    private static UserDatabaseHelper instance;
    private Context mContext;

    private UserDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    public static synchronized UserDatabaseHelper instance(Context context) {
        if (instance == null) {
            instance = new UserDatabaseHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, UserWord.class);
            Log.i(TAG, "Created UserWord table");
        } catch (SQLException e) {
            Log.e(TAG, "Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            TableUtils.dropTable(connectionSource, UserWord.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            Log.e(TAG, "Can't drop databases", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteDatabase() {
        mContext.deleteDatabase(DATABASE_NAME);
    }

    public Dao<UserWord, Integer> getUserWordDao() throws SQLException {
        return getDao(UserWord.class);
    }

    @Override
    public <T> Dao<T, Integer> getDbDao(Class clazz) throws SQLException {
        return getDao(clazz);
    }
}
