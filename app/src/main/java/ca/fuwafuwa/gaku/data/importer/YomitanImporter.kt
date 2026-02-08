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

class YomitanImporter(private val db: AppDatabase) {

    private val gson = Gson()

    fun importDictionary(inputStream: InputStream, progressCallback: (String) -> Unit = {}) {
        val tempZip = File.createTempFile("gaku-yomitan-", ".zip")
        try {
            tempZip.outputStream().use { output -> inputStream.copyTo(output) }
            importDictionary(tempZip, progressCallback)
        } finally {
            tempZip.delete()
        }
    }

    fun importDictionary(zipArchive: File, progressCallback: (String) -> Unit = {}) {
        ZipFile(zipArchive).use { zip ->
            val indexEntry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.substringAfterLast('/') == "index.json" }
                ?: throw IllegalStateException("Missing index.json in Yomitan dictionary archive")

            progressCallback("Reading Index...")
            val dictionaryId = db.runInTransaction<Long> {
                zip.getInputStream(indexEntry).use { parseAndInsertIndex(it) }
            }

            val orderedEntries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .sortedBy { it.name.substringAfterLast('/') }
                .toList()

            for (entry in orderedEntries) {
                val name = entry.name.substringAfterLast('/')
                if (name == "index.json") continue

                zip.getInputStream(entry).use { stream ->
                    when {
                        name.startsWith("term_bank") -> {
                            progressCallback("Importing Terms: $name")
                            parseAndInsertTerms(stream, dictionaryId)
                        }
                        name.startsWith("kanji_bank") -> {
                            progressCallback("Importing Kanji: $name")
                            parseAndInsertKanji(stream, dictionaryId)
                        }
                        name.startsWith("term_meta_bank") -> parseAndInsertTermMeta(stream, dictionaryId)
                        name.startsWith("kanji_meta_bank") -> parseAndInsertKanjiMeta(stream, dictionaryId)
                        name.startsWith("tag_bank") -> parseAndInsertTagMeta(stream, dictionaryId)
                    }
                }
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

        val definitionBuffer = ArrayList<Definition>()
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

            val termId = db.termDao().insert(
                Term(
                    dictionaryId = dictId,
                    expression = expression,
                    reading = reading,
                    tags = tags,
                    rules = rules,
                    score = score,
                    sequence = sequence,
                    termTags = termTags
                )
            )

            definitions.forEach { def ->
                definitionBuffer.add(Definition(termId = termId, content = def.first, type = def.second))
            }
            if (definitionBuffer.size >= 1000) {
                db.runInTransaction {
                    db.definitionDao().insertAll(definitionBuffer)
                }
                definitionBuffer.clear()
            }
        }
        if (definitionBuffer.isNotEmpty()) {
            db.runInTransaction {
                db.definitionDao().insertAll(definitionBuffer)
            }
        }
        reader.endArray()
    }

    private fun parseGlossary(reader: JsonReader): List<Pair<String, String>> {
        val defs = mutableListOf<Pair<String, String>>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING -> defs.add(reader.nextString().trim() to "text")
                else -> {
                    val obj = gson.fromJson<Any>(reader, Any::class.java)
                    defs.add(gson.toJson(obj) to "structured")
                }
            }
        }
        reader.endArray()
        return defs
    }

    private fun parseAndInsertKanji(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        reader.beginArray()
        val buffer = ArrayList<Kanji>()
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
        }
        if (buffer.isNotEmpty()) db.kanjiDao().insertAll(buffer)
        reader.endArray()
    }

    private fun parseAndInsertTermMeta(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        val buffer = ArrayList<TermMeta>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            val expression = readAsString(reader)
            val mode = readAsString(reader)
            val data = readUnknownAsJsonOrString(reader)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            buffer.add(TermMeta(dictionaryId = dictId, expression = expression, mode = mode, data = data))
        }
        reader.endArray()
        if (buffer.isNotEmpty()) db.termMetaDao().insertAll(buffer)
    }

    private fun parseAndInsertKanjiMeta(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        val buffer = ArrayList<KanjiMeta>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            val character = readAsString(reader)
            val mode = readAsString(reader)
            val data = readUnknownAsJsonOrString(reader)
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            buffer.add(KanjiMeta(dictionaryId = dictId, character = character, mode = mode, data = data))
        }
        reader.endArray()
        if (buffer.isNotEmpty()) db.kanjiMetaDao().insertAll(buffer)
    }

    private fun parseAndInsertTagMeta(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
        val buffer = ArrayList<TagMeta>()
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
