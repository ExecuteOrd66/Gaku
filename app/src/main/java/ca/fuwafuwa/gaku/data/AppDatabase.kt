package ca.fuwafuwa.gaku.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.fuwafuwa.gaku.data.dao.DefinitionDao
import ca.fuwafuwa.gaku.data.dao.DictionaryDao
import ca.fuwafuwa.gaku.data.dao.KanjiDao
import ca.fuwafuwa.gaku.data.dao.TermDao

@Database(
    entities = [Dictionary::class, Term::class, Definition::class, Kanji::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun termDao(): TermDao
    abstract fun definitionDao(): DefinitionDao
    abstract fun kanjiDao(): KanjiDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gaku_yomitan.db"
                )
                    // Pre-populating or migration logic goes here
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}