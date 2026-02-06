package ca.fuwafuwa.gaku.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Dictionary
import ca.fuwafuwa.gaku.data.Kanji
import ca.fuwafuwa.gaku.data.KanjiMeta
import ca.fuwafuwa.gaku.data.TagMeta
import ca.fuwafuwa.gaku.data.Term
import ca.fuwafuwa.gaku.data.TermMeta

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

    @Query("SELECT * FROM terms WHERE expression IN (:queries) OR reading IN (:queries)")
    fun findTermsExact(queries: List<String>): List<Term>

    @Query("SELECT * FROM terms WHERE expression LIKE :prefix || '%' OR reading LIKE :prefix || '%'")
    fun findTermsByPrefix(prefix: String): List<Term>

    @Query("SELECT * FROM terms WHERE expression LIKE '%' || :suffix OR reading LIKE '%' || :suffix")
    fun findTermsBySuffix(suffix: String): List<Term>

    @Query("SELECT * FROM terms WHERE sequence IN (:sequences)")
    fun findTermsBySequence(sequences: List<Int>): List<Term>

    @Query("SELECT * FROM terms WHERE expression = :expression AND reading = :reading")
    fun findTermExact(expression: String, reading: String): List<Term>


    @Query(
        """
        SELECT * FROM terms
        WHERE dictionaryId IN (:activeDictionaryIds)
          AND (expression IN (:variants) OR reading IN (:variants))
        ORDER BY CASE WHEN score <= 0 THEN 2147483647 ELSE score END ASC,
                 LENGTH(expression) ASC
        """
    )
    fun findTermsByVariants(variants: List<String>, activeDictionaryIds: List<Long>): List<Term>

    @Query("SELECT COUNT(*) FROM terms")
    fun count(): Int
}

@Dao
interface DefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(definitions: List<Definition>)

    @Query("SELECT * FROM definitions WHERE termId = :termId")
    fun getDefinitionsForTerm(termId: Long): List<Definition>
}

@Dao
interface KanjiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(kanji: List<Kanji>)

    @Query("SELECT * FROM kanji WHERE character = :characterQuery AND dictionaryId IN (:activeDictionaryIds)")
    fun findKanji(characterQuery: String, activeDictionaryIds: List<Long>): List<Kanji>

    @Query("SELECT COUNT(*) FROM kanji")
    fun count(): Int

    @Query("SELECT * FROM kanji WHERE character IN (:characters)")
    fun findKanjiBulk(characters: List<String>): List<Kanji>
}

@Dao
interface TermMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<TermMeta>)

    @Query("SELECT COUNT(*) FROM term_meta")
    fun count(): Int

    @Query("SELECT * FROM term_meta WHERE expression IN (:expressions)")
    fun findByExpressions(expressions: List<String>): List<TermMeta>
}

@Dao
interface KanjiMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<KanjiMeta>)

    @Query("SELECT COUNT(*) FROM kanji_meta")
    fun count(): Int

    @Query("SELECT * FROM kanji_meta WHERE character IN (:characters)")
    fun findByCharacters(characters: List<String>): List<KanjiMeta>
}

@Dao
interface TagMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<TagMeta>)

    @Query("SELECT COUNT(*) FROM tag_meta")
    fun count(): Int

    @Query(
        """
        SELECT tag_meta.* FROM tag_meta
        INNER JOIN dictionaries ON dictionaries.id = tag_meta.dictionaryId
        WHERE tag_meta.name = :name AND dictionaries.name = :dictionaryName
        LIMIT 1
        """
    )
    fun findTagByNameAndDictionary(name: String, dictionaryName: String): TagMeta?
}
