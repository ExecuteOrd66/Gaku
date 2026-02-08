package ca.fuwafuwa.gaku.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ca.fuwafuwa.gaku.data.Dictionary
import ca.fuwafuwa.gaku.data.Term
import ca.fuwafuwa.gaku.data.Kanji
import ca.fuwafuwa.gaku.data.TermMeta
import ca.fuwafuwa.gaku.data.KanjiMeta
import ca.fuwafuwa.gaku.data.TagMeta
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
        Kanji::class,
        TermMeta::class,
        KanjiMeta::class,
        TagMeta::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun termDao(): TermDao
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