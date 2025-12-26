package ca.fuwafuwa.gaku.Database.Yomitan

import android.content.Context
import android.net.Uri
import android.util.Log
import ca.fuwafuwa.gaku.Database.Yomitan.YomitanDatabaseHelper
import ca.fuwafuwa.gaku.Database.Yomitan.YomitanDictionary
import ca.fuwafuwa.gaku.Database.Yomitan.YomitanTerm
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.zip.ZipFile
import java.io.File
import java.io.FileOutputStream

class YomitanImporter(private val context: Context) {

    private val TAG = "YomitanImporter"

    fun importFromZip(uri: Uri, onProgress: (String, Int) -> Unit) {
        val tempFile = File(context.cacheDir, "temp_dict.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val zipFile = ZipFile(tempFile)
        val entries = zipFile.entries()
        
        var indexJson: String? = null
        val bankEntries = mutableListOf<String>()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name == "index.json") {
                indexJson = zipFile.getInputStream(entry).bufferedReader().readText()
            } else if (entry.name.startsWith("term_bank_")) {
                bankEntries.add(entry.name)
            }
        }

        if (indexJson == null) {
            Log.e(TAG, "No index.json found in zip")
            return
        }

        val indexObj = JsonParser.parseString(indexJson).asJsonObject
        val title = indexObj.get("title").asString
        val author = indexObj.get("author")?.asString ?: ""
        val description = indexObj.get("description")?.asString ?: ""
        val revision = indexObj.get("revision")?.asInt ?: 0

        val db = YomitanDatabaseHelper.getInstance(context)
        val dictionary = YomitanDictionary(title, author, description, revision)
        db.dictionaryDao.create(dictionary)
        val dictId = dictionary.id

        onProgress("Importing $title...", 0)

        for ((index, bankName) in bankEntries.withIndex()) {
            val entry = zipFile.getEntry(bankName)
            zipFile.getInputStream(entry).use { inputStream ->
                val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
                db.termDao.callBatchTasks {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val termArray = JsonParser.parseReader(reader).asJsonArray
                        val term = parseTerm(dictId, termArray)
                        db.termDao.create(term)
                    }
                    reader.endArray()
                    null
                }
            }
            onProgress("Importing bank ${index + 1}/${bankEntries.size}...", ((index + 1).toFloat() / bankEntries.size * 100).toInt())
        }

        zipFile.close()
        tempFile.delete()
    }

    private fun parseTerm(dictId: Int, arr: JsonArray): YomitanTerm {
        val kanji = arr.get(0).asString
        val reading = arr.get(1).asString
        val definitionTags = arr.get(2).asString
        val deinflectionRules = arr.get(3).asString
        val popularity = arr.get(4).asInt
        val meanings = arr.get(5).toString() // Keep as JSON string
        val dictionaryTags = arr.get(7).asString

        return YomitanTerm(dictId, kanji, reading, definitionTags, deinflectionRules, popularity, meanings, dictionaryTags)
    }
}
