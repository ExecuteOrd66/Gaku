package ca.fuwafuwa.gaku.Search

import android.content.Context
import android.os.AsyncTask
import ca.fuwafuwa.gaku.data.AppDatabase
import ca.fuwafuwa.gaku.data.Kanji

/**
 * Migrated to use Room (AppDatabase) instead of the old ORMLite Kd2DatabaseHelper.
 */
class Kd2Task(
    private val mSearchInfo: SearchInfo,
    private val mSearchKd2TaskDone: SearchKd2TaskDone,
    context: Context
) : AsyncTask<Void, Void, List<Kanji>>() {

    companion object {
        private val TAG = Kd2Task::class.java.name
    }

    private val db: AppDatabase = AppDatabase.getDatabase(context)

    interface SearchKd2TaskDone {
        fun kd2TaskCallback(results: List<Kanji>, searchInfo: SearchInfo)
    }

    override fun doInBackground(vararg params: Void): List<Kanji> {
        val text = mSearchInfo.text
        val textOffset = mSearchInfo.textOffset

        // Extract the specific character from the text string
        val charCode = text.codePointAt(textOffset)
        val character = String(intArrayOf(charCode), 0, 1)

        // 1. Get Active Dictionary IDs
        // In the new architecture, we must know which dictionaries to search.
        // We fetch enabled dictionaries first.
        val activeDictIds = db.dictionaryDao().getAllDictionaries()
            .filter { it.isEnabled }
            .map { it.id }

        if (activeDictIds.isEmpty()) {
            return emptyList()
        }

        // 2. Query the KanjiDao using the new method signature
        return db.kanjiDao().findKanji(character, activeDictIds)
    }

    override fun onPostExecute(result: List<Kanji>) {
        mSearchKd2TaskDone.kd2TaskCallback(result, mSearchInfo)
    }
}