package ca.fuwafuwa.gaku.data.dao

import androidx.room.*
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Dictionary
import ca.fuwafuwa.gaku.data.Kanji
import ca.fuwafuwa.gaku.data.Term

@Dao
interface DictionaryDao {
    @Insert
    fun insert(dictionary: Dictionary): Long

    @Query("SELECT * FROM dictionaries")
    fun getAllDictionaries(): List<Dictionary>

    @Query("DELETE FROM dictionaries WHERE id = :id")
    fun deleteDictionary(id: Long)
}

@Dao
interface TermDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(term: Term): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(terms: List<Term>)

    // Basic lookup: Matches expression or reading exactly
    // Ordered by score (Frequency), assuming higher score = more common
    @Query("""
        SELECT * FROM terms 
        WHERE (expression = :query OR reading = :query)
        AND dictionaryId IN (:activeDictionaryIds)
        ORDER BY score DESC
    """)
    fun findTerms(query: String, activeDictionaryIds: List<Long>): List<Term>

    // Advanced lookup: Matches a list of deinflected variants
    // e.g. input "tabeta" -> deinflector produces ["tabeta", "taberu"] -> query checks both
    @Query("""
        SELECT * FROM terms 
        WHERE (expression IN (:queries) OR reading IN (:queries))
        AND dictionaryId IN (:activeDictionaryIds)
        ORDER BY score DESC
    """)
    fun findTermsByVariants(queries: List<String>, activeDictionaryIds: List<Long>): List<Term>
}

@Dao
interface DefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(definitions: List<Definition>)

    // Efficient lookup by Foreign Key
    @Query("SELECT * FROM definitions WHERE termId = :termId")
    fun getDefinitionsForTerm(termId: Long): List<Definition>
}

@Dao
interface KanjiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(kanji: List<Kanji>)

    @Query("SELECT * FROM kanji WHERE character = :char AND dictionaryId IN (:activeDictionaryIds)")
    fun findKanji(char: String, activeDictionaryIds: List<Long>): List<Kanji>
}