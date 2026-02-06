package ca.fuwafuwa.gaku.data.importer

import ca.fuwafuwa.gaku.data.AppDatabase
import ca.fuwafuwa.gaku.data.Dictionary
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Term
import ca.fuwafuwa.gaku.data.Kanji
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class YomitanImporter(private val db: AppDatabase) {

    private val gson = Gson()

    fun importDictionary(inputStream: InputStream, progressCallback: (String) -> Unit) {
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry

        var dictionaryId: Long = -1

        while (entry != null) {
            val name = entry.name

            if (name == "index.json") {
                progressCallback("Reading Index...")
                dictionaryId = parseAndInsertIndex(zipStream)
            } else if (dictionaryId != -1L) {
                if (name.startsWith("term_bank")) {
                    progressCallback("Importing Terms: $name")
                    parseAndInsertTerms(zipStream, dictionaryId)
                } else if (name.startsWith("kanji_bank")) {
                    progressCallback("Importing Kanji: $name")
                    parseAndInsertKanji(zipStream, dictionaryId)
                }
            }

            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()
    }

    private fun parseAndInsertIndex(stream: InputStream): Long {
        val reader = JsonReader(InputStreamReader(stream, "UTF-8"))
        var title = ""
        var revision = ""
        var version = 0
        var type = 0

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> title = reader.nextString()
                "revision" -> revision = reader.nextString()
                "version" -> version = reader.nextInt()
                "type" -> if (reader.nextString() == "kanji") type = 1
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val dict = Dictionary(name = title, revision = revision, version = version, type = type)
        return db.dictionaryDao().insert(dict)
    }

    private fun parseAndInsertTerms(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, "UTF-8"))

        // Yomitan term banks are a list of items: [expression, reading, tags, rules, score, glossary, sequence, term_tags]
        reader.beginArray() // Start file array

        val termBuffer = ArrayList<Term>(500)
        val defBuffer = ArrayList<Definition>(1000)

        db.runInTransaction {
            while (reader.hasNext()) {
                reader.beginArray() // Start single term entry

                val expression = reader.nextString()
                val reading = reader.nextString()
                val tags = reader.nextString()
                val rules = reader.nextString()
                val score = reader.nextInt()

                // 5. Glossary (Array of strings or objects)
                val definitions = parseGlossary(reader)

                val sequence = reader.nextInt()
                val termTags = reader.nextString()

                reader.endArray() // End single term entry

                // Create Term Object
                val term = Term(
                    dictionaryId = dictId,
                    expression = expression,
                    reading = reading,
                    tags = tags + " " + termTags, // Combine tags for search efficiency
                    rules = rules,
                    score = score,
                    sequence = sequence
                )

                // We insert immediately to get the ID for definitions
                // Optimization: In massive imports, you might batch insert Terms,
                // get IDs, then batch insert Definitions. For simplicity, we do row-by-row here.
                val termId = db.termDao().insert(term)

                for (defContent in definitions) {
                    defBuffer.add(Definition(termId = termId, content = defContent, type = "text"))
                }

                // Batch insert definitions
                if (defBuffer.size >= 1000) {
                    db.definitionDao().insertAll(defBuffer)
                    defBuffer.clear()
                }
            }
            // Flush remaining
            if (defBuffer.isNotEmpty()) db.definitionDao().insertAll(defBuffer)
        }
        reader.endArray() // End file array
    }

    private fun parseGlossary(reader: JsonReader): List<String> {
        val defs = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            // Yomitan glossary items can be strings or Structured Content objects
            // For efficiency, we store objects as raw JSON strings
            val token = reader.peek()
            if (token == com.google.gson.stream.JsonToken.STRING) {
                defs.add(reader.nextString())
            } else {
                // It's an object (Structured Content), serialize it to string
                val obj = gson.fromJson<Any>(reader, Any::class.java)
                defs.add(gson.toJson(obj))
            }
        }
        reader.endArray()
        return defs
    }

    private fun parseAndInsertKanji(stream: InputStream, dictId: Long) {
        val reader = JsonReader(InputStreamReader(stream, "UTF-8"))
        reader.beginArray()

        val buffer = ArrayList<Kanji>(500)

        while (reader.hasNext()) {
            // [character, onyomi, kunyomi, tags, meanings, stats]
            reader.beginArray()
            val char = reader.nextString()
            val on = reader.nextString()
            val kun = reader.nextString()
            val tags = reader.nextString()

            // Meanings (Array)
            val meaningsList = mutableListOf<String>()
            reader.beginArray()
            while(reader.hasNext()) meaningsList.add(reader.nextString())
            reader.endArray()

            // Stats (Object) - Skip for now or serialize
            reader.skipValue()

            reader.endArray()

            buffer.add(Kanji(
                dictionaryId = dictId,
                character = char,
                onyomi = on,
                kunyomi = kun,
                tags = tags,
                meanings = meaningsList.joinToString("\n")
            ))

            if (buffer.size >= 500) {
                db.kanjiDao().insertAll(buffer)
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) db.kanjiDao().insertAll(buffer)
        reader.endArray()
    }
}