package ca.fuwafuwa.gaku.data.importer

import ca.fuwafuwa.gaku.data.AppDatabase
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Dictionary
import ca.fuwafuwa.gaku.data.Kanji
import ca.fuwafuwa.gaku.data.KanjiMeta
import ca.fuwafuwa.gaku.data.TagMeta
import ca.fuwafuwa.gaku.data.Term
import ca.fuwafuwa.gaku.data.TermMeta
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile

/**
 * Optimized importer that embeds definitions directly into Terms.
 */
class YomitanImporter(private val db: AppDatabase) {

    private val gson = Gson()
    private val BATCH_SIZE = 2000

    data class ImportProgress(
        val currentFileIndex: Int,
        val totalFiles: Int,
        val fileName: String
    )

    fun importDictionary(inputStream: InputStream, progressCallback: (ImportProgress) -> Unit) {
        val tempZip = File.createTempFile("gaku-yomitan-", ".zip")
        try {
            tempZip.outputStream().use { output -> inputStream.copyTo(output) }
            importDictionary(tempZip, progressCallback)
        } finally {
            tempZip.delete()
        }
    }

    fun importDictionary(zipArchive: File, progressCallback: (ImportProgress) -> Unit) {
        ZipFile(zipArchive).use { zip ->
            // 1. Scan structure
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .sortedBy { it.name }
                .toList()

            val indexEntry = entries.firstOrNull { it.name.endsWith("index.json") }
                ?: throw IllegalStateException("Missing index.json in Yomitan dictionary archive")

            val bankFiles = entries.filter { 
                it.name.contains("term_bank") || 
                it.name.contains("kanji_bank") || 
                it.name.contains("meta_bank") ||
                it.name.contains("tag_bank")
            }
            
            val totalFiles = bankFiles.size + 1 

            // 2. Import Index
            progressCallback(ImportProgress(0, totalFiles, "index.json"))
            val dictionaryId = db.runInTransaction<Long> {
                zip.getInputStream(indexEntry).use { parseAndInsertIndex(it) }
            }

            // 3. Process Banks
            var processedCount = 1
            
            for (entry in bankFiles) {
                val name = entry.name.substringAfterLast('/')
                progressCallback(ImportProgress(processedCount, totalFiles, name))

                // Using transactions speeds up SQLite insertions by ~100x
                db.runInTransaction {
                    zip.getInputStream(entry).use { stream ->
                        when {
                            name.contains("term_bank") -> parseAndInsertTerms(stream, dictionaryId)
                            name.contains("kanji_bank") -> parseAndInsertKanji(stream, dictionaryId)
                            name.contains("term_meta_bank") -> parseAndInsertTermMeta(stream, dictionaryId)
                            name.contains("kanji_meta_bank") -> parseAndInsertKanjiMeta(stream, dictionaryId)
                            name.contains("tag_bank") -> parseAndInsertTagMeta(stream, dictionaryId)
                        }
                    }
                }
                processedCount++
            }
        }
    }

    private fun parseAndInsertIndex(stream: InputStream): Long {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        var title = ""
        var revision = ""
        var format = 0
        var sequenced = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> title = readAsString(reader)
                "revision" -> revision = readAsString(reader)
                "format", "version" -> format = readAsInt(reader)
                "sequenced" -> sequenced = if (reader.peek() == JsonToken.BOOLEAN) reader.nextBoolean() else readAsInt(reader) != 0
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val dict = Dictionary(name = title, revision = revision, format = format, sequenced = sequenced)
        return db.dictionaryDao().insert(dict)
    }

    private fun parseAndInsertTerms(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        reader.beginArray()

        val termBuffer = ArrayList<Term>(BATCH_SIZE)

        while (reader.hasNext()) {
            reader.beginArray()
            val expression = readAsString(reader)
            val reading = readAsString(reader)
            val tags = readAsString(reader)
            val rules = readAsString(reader)
            val score = readAsInt(reader)
            val definitions = parseGlossary(reader)
            val sequence = readAsInt(reader)
            val termTags = if (reader.hasNext()) readAsString(reader) else ""
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()

            termBuffer.add(
                Term(
                    dictionaryId = dictId,
                    expression = expression,
                    reading = reading,
                    tags = tags,
                    rules = rules,
                    score = score,
                    sequence = sequence,
                    termTags = termTags,
                    definitions = definitions // Now stored directly
                )
            )

            if (termBuffer.size >= BATCH_SIZE) {
                db.termDao().insertAll(termBuffer)
                termBuffer.clear()
            }
        }
        
        if (termBuffer.isNotEmpty()) {
            db.termDao().insertAll(termBuffer)
        }
        reader.endArray()
    }

    private fun parseGlossary(reader: JsonReader): List<Definition> {
        val defs = mutableListOf<Definition>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING -> defs.add(Definition(reader.nextString().trim(), "text"))
                else -> {
                    val obj = gson.fromJson<Any>(reader, Any::class.java)
                    defs.add(Definition(gson.toJson(obj), "structured"))
                }
            }
        }
        reader.endArray()
        return defs
    }

    private fun parseAndInsertKanji(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        reader.beginArray()
        val buffer = ArrayList<Kanji>(BATCH_SIZE)
        while (reader.hasNext()) {
            reader.beginArray()
            val char = readAsString(reader)
            val on = readAsString(reader)
            val kun = readAsString(reader)
            val tags = readAsString(reader)
            val meaningsList = mutableListOf<String>()
            reader.beginArray()
            while (reader.hasNext()) meaningsList.add(readAsString(reader).trim())
            reader.endArray()
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()

            buffer.add(
                Kanji(
                    dictionaryId = dictId,
                    character = char,
                    onyomi = on,
                    kunyomi = kun,
                    tags = tags,
                    meanings = meaningsList.joinToString("\n")
                )
            )
            
            if (buffer.size >= BATCH_SIZE) {
                db.kanjiDao().insertAll(buffer)
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) db.kanjiDao().insertAll(buffer)
        reader.endArray()
    }

    private fun parseAndInsertTermMeta(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        val buffer = ArrayList<TermMeta>(BATCH_SIZE)
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            val expression = readAsString(reader)
            val mode = readAsString(reader)
            val data = readUnknownAsJsonOrString(reader)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            buffer.add(TermMeta(dictionaryId = dictId, expression = expression, mode = mode, data = data))
            
            if (buffer.size >= BATCH_SIZE) {
                db.termMetaDao().insertAll(buffer)
                buffer.clear()
            }
        }
        reader.endArray()
        if (buffer.isNotEmpty()) db.termMetaDao().insertAll(buffer)
    }

    private fun parseAndInsertKanjiMeta(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        val buffer = ArrayList<KanjiMeta>(BATCH_SIZE)
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            val character = readAsString(reader)
            val mode = readAsString(reader)
            val data = readUnknownAsJsonOrString(reader)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            buffer.add(KanjiMeta(dictionaryId = dictId, character = character, mode = mode, data = data))
            
            if (buffer.size >= BATCH_SIZE) {
                db.kanjiMetaDao().insertAll(buffer)
                buffer.clear()
            }
        }
        reader.endArray()
        if (buffer.isNotEmpty()) db.kanjiMetaDao().insertAll(buffer)
    }

    private fun parseAndInsertTagMeta(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        val buffer = ArrayList<TagMeta>(BATCH_SIZE)
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            buffer.add(
                TagMeta(
                    dictionaryId = dictId,
                    name = readAsString(reader),
                    category = readAsString(reader),
                    orderIndex = readAsInt(reader),
                    notes = readAsString(reader),
                    score = readAsInt(reader)
                )
            )
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            
            if (buffer.size >= BATCH_SIZE) {
                db.tagMetaDao().insertAll(buffer)
                buffer.clear()
            }
        }
        reader.endArray()
        if (buffer.isNotEmpty()) db.tagMetaDao().insertAll(buffer)
    }

    private fun readAsString(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> reader.nextString()
        }
    }

    private fun readAsInt(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextInt()
            JsonToken.STRING -> reader.nextString().toIntOrNull() ?: 0
            JsonToken.NULL -> {
                reader.nextNull(); 0
            }
            else -> 0
        }
    }

    private fun readUnknownAsJsonOrString(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString().trim()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull(); ""
            }
            else -> gson.toJson(gson.fromJson<Any>(reader, Any::class.java))
        }
    }
}