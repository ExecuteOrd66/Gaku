package ca.fuwafuwa.gaku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.fuwafuwa.gaku.data.dao.TermDao
import ca.fuwafuwa.gaku.data.importer.YomitanImporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
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
    fun import_validDictionary_supportsPortedFindTermsBulkCases() {
        val zipBytes = createZipFromDirectory(File("yomichan tests/data/dictionaries/valid-dictionary1"))
        importer.importDictionary(zipBytes.inputStream())

        val exact = db.termDao().findTermsExact(listOf("打", "打つ", "打ち込む"))
        assertEquals(10, exact.size)

        val byExpression = exact.groupingBy { it.expression }.eachCount()
        assertEquals(2, byExpression["打"])
        assertEquals(4, byExpression["打つ"])
        assertEquals(4, byExpression["打ち込む"])

        val byReading = exact.groupingBy { it.reading }.eachCount()
        assertEquals(1, byReading["だ"])
        assertEquals(1, byReading["ダース"])
        assertEquals(2, byReading["うつ"])
        assertEquals(2, byReading["ぶつ"])
        assertEquals(2, byReading["うちこむ"])
        assertEquals(2, byReading["ぶちこむ"])

        val prefix = db.termDao().findTermsByPrefix("打")
        assertEquals(10, prefix.size)

        val suffix = db.termDao().findTermsBySuffix("込む")
        assertEquals(4, suffix.size)

        val noExactMatch = db.termDao().findTermsExact(listOf("込む"))
        assertTrue(noExactMatch.isEmpty())
    }

    @Test
    fun import_validDictionary_supportsPortedFindTermsExactAndSequenceCases() {
        val zipBytes = createZipFromDirectory(File("yomichan tests/data/dictionaries/valid-dictionary1"))
        importer.importDictionary(zipBytes.inputStream())

        val termsExact = db.termDao().findTermsExactByPairs(listOf("打" to "だ", "打つ" to "うつ", "打ち込む" to "うちこむ"))
        assertEquals(5, termsExact.size)

        val wrongReading = db.termDao().findTermsExactByPairs(listOf("打つ" to "うちこむ"))
        assertTrue(wrongReading.isEmpty())

        val sequenceResults = db.termDao().findTermsBySequence(listOf(1, 2, 3, 4, 5))
        assertEquals(11, sequenceResults.size)
        val seqByExpression = sequenceResults.groupingBy { it.expression }.eachCount()
        assertEquals(2, seqByExpression["打"])
        assertEquals(4, seqByExpression["打つ"])
        assertEquals(4, seqByExpression["打ち込む"])
        assertEquals(1, seqByExpression["画像"])
    }

    @Test
    fun import_validDictionary_supportsPortedMetaAndKanjiLookupCases() {
        val zipBytes = createZipFromDirectory(File("yomichan tests/data/dictionaries/valid-dictionary1"))
        importer.importDictionary(zipBytes.inputStream())

        val termMeta = db.termMetaDao().findByExpressions(listOf("打ち込む"))
        assertEquals(13, termMeta.size)
        assertEquals(10, termMeta.count { it.mode == "freq" })
        assertEquals(3, termMeta.count { it.mode == "pitch" })

        val kanji = db.kanjiDao().findKanjiBulk(listOf("打", "込"))
        assertEquals(2, kanji.size)

        val kanjiMeta = db.kanjiMetaDao().findByCharacters(listOf("打"))
        assertEquals(3, kanjiMeta.size)
        assertEquals(3, kanjiMeta.count { it.mode == "freq" })

        val e1Tag = db.tagMetaDao().findTagByNameAndDictionary("E1", "Test Dictionary")
        assertNotNull(e1Tag)
        assertEquals("default", e1Tag?.category)
        assertEquals("example tag 1", e1Tag?.notes)

        val missingTag = db.tagMetaDao().findTagByNameAndDictionary("invalid", "Test Dictionary")
        assertNull(missingTag)
    }


    @Test
    fun import_dictionaryFileWithIndexAtEnd_succeeds() {
        val sourceDir = File("yomichan tests/data/dictionaries/valid-dictionary1")
        val tempZip = File.createTempFile("yomitan-test-", ".zip")

        try {
            ZipOutputStream(tempZip.outputStream()).use { zip ->
                val files = sourceDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
                files.filter { it.name != "index.json" }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
                val indexFile = files.first { it.name == "index.json" }
                zip.putNextEntry(ZipEntry(indexFile.name))
                FileInputStream(indexFile).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }

            importer.importDictionary(tempZip)

            assertEquals(1, db.dictionaryDao().getAllDictionaries().size)
            assertEquals(34, db.termDao().count())
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun import_supportsNestedArchivePaths() {
        val zipBytes = createZipFromDirectory(
            directory = File("yomichan tests/data/dictionaries/valid-dictionary1"),
            prefix = "nested/"
        )

        importer.importDictionary(zipBytes.inputStream())

        assertEquals(1, db.dictionaryDao().getAllDictionaries().size)
        assertEquals(34, db.termDao().count())
    }

    @Test(expected = IllegalStateException::class)
    fun import_missingIndex_throws() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("term_bank_1.json"))
            zip.write("[]".toByteArray())
            zip.closeEntry()
        }

        importer.importDictionary(out.toByteArray().inputStream())
    }

    private fun createZipFromDirectory(directory: File, prefix: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            directory.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry(prefix + file.name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return out.toByteArray()
    }
}

private fun TermDao.findTermsExactByPairs(pairs: List<Pair<String, String>>): List<Term> {
    if (pairs.isEmpty()) return emptyList()

    val result = mutableListOf<Term>()
    for ((expression, reading) in pairs) {
        result += findTermExact(expression, reading)
    }
    return result
}
