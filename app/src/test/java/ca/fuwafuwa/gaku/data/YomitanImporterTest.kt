package ca.fuwafuwa.gaku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.fuwafuwa.gaku.data.importer.YomitanImporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YomitanImporterTest {

    private lateinit var db: AppDatabase
    private lateinit var importer: YomitanImporter

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        importer = YomitanImporter(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun import_validDictionary_populatesCoreAndMetaTables() {
        val zipBytes = createZipFromDirectory(File("yomichan tests/data/dictionaries/valid-dictionary1"))

        importer.importDictionary(zipBytes.inputStream())

        val dictionaries = db.dictionaryDao().getAllDictionaries()
        assertEquals(1, dictionaries.size)
        val dictionary = dictionaries.first()
        assertEquals("Test Dictionary", dictionary.name)
        assertEquals("test", dictionary.revision)
        assertEquals(3, dictionary.format)
        assertTrue(dictionary.sequenced)

        assertEquals(34, db.termDao().count())
        assertEquals(2, db.kanjiDao().count())
        assertEquals(40, db.termMetaDao().count())
        assertEquals(6, db.kanjiMetaDao().count())
        assertEquals(15, db.tagMetaDao().count())
    }

    @Test
    fun import_validDictionary_supportsTermQueriesFromPortedCases() {
        val zipBytes = createZipFromDirectory(File("yomichan tests/data/dictionaries/valid-dictionary1"))
        importer.importDictionary(zipBytes.inputStream())

        val exact = db.termDao().findTermsExact(listOf("打", "打つ", "打ち込む"))
        assertEquals(10, exact.size)

        val prefix = db.termDao().findTermsByPrefix("打")
        assertEquals(10, prefix.size)

        val suffix = db.termDao().findTermsBySuffix("込む")
        assertEquals(4, suffix.size)

        val sequences = db.termDao().findTermsBySequence(listOf(1, 2, 3))
        assertTrue(sequences.isNotEmpty())
    }

    private fun createZipFromDirectory(directory: File): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            directory.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return out.toByteArray()
    }
}
