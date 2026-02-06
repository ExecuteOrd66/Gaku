package ca.fuwafuwa.gaku.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.fuwafuwa.gaku.data.dao.DefinitionDao
import ca.fuwafuwa.gaku.data.dao.DictionaryDao
import ca.fuwafuwa.gaku.data.dao.KanjiDao
import ca.fuwafuwa.gaku.data.dao.KanjiMetaDao
import ca.fuwafuwa.gaku.data.dao.TagMetaDao
import ca.fuwafuwa.gaku.data.dao.TermDao
import ca.fuwafuwa.gaku.data.dao.TermMetaDao

@Database(
    entities = [
        Dictionary::class,
        Term::class,
        Definition::class,
        Kanji::class,
        TermMeta::class,
        KanjiMeta::class,
        TagMeta::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun termDao(): TermDao
    abstract fun definitionDao(): DefinitionDao
    abstract fun kanjiDao(): KanjiDao
    abstract fun termMetaDao(): TermMetaDao
    abstract fun kanjiMetaDao(): KanjiMetaDao
    abstract fun tagMetaDao(): TagMetaDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
