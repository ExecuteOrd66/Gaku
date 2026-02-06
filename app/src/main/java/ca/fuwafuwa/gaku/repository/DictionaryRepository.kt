package ca.fuwafuwa.gaku.data.repository

import ca.fuwafuwa.gaku.data.AppDatabase
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryRepository(private val db: AppDatabase) {

    // Cache active dictionary IDs to avoid DB lookups on every keystroke
    private var activeDictIds: List<Long> = emptyList()

    suspend fun refreshActiveDictionaries() = withContext(Dispatchers.IO) {
        activeDictIds = db.dictionaryDao().getAllDictionaries()
            .filter { it.isEnabled }
            .map { it.id }
    }

    /**
     * The Main Search Function
     * 1. Takes raw text.
     * 2. (Optional) Deinflects it (e.g. "runs" -> "run").
     * 3. Queries DB for all variants.
     */
    suspend fun searchTerms(rawQuery: String): List<Term> = withContext(Dispatchers.IO) {
        if (activeDictIds.isEmpty()) refreshActiveDictionaries()

        // 1. Deinflection Logic
        // In a full app, you would port the 'japanese-transforms.js' logic here.
        // For now, we simulate a basic deinflector or just use the raw query.
        val potentialForms = simpleDeinflector(rawQuery)

        // 2. Query DB
        return@withContext db.termDao().findTermsByVariants(potentialForms, activeDictIds)
    }

    /**
     * Lazy Load Definitions
     * Called only when the user wants to see details for a specific term.
     */
    suspend fun getDefinitions(termId: Long): List<Definition> = withContext(Dispatchers.IO) {
        return@withContext db.definitionDao().getDefinitionsForTerm(termId)
    }

    /**
     * A placeholder for the Deinflector.
     * In the Yomitan JS code, this is the "Translator" class that uses "Deinflector".
     */
    private fun simpleDeinflector(text: String): List<String> {
        val results = mutableListOf(text)

        // Example: If text ends in "imasu", also search for "u"
        // You will eventually implement the full Yomitan logic here
        if (text.endsWith("みます")) results.add(text.replace("みます", "む"))
        if (text.endsWith("食べます")) results.add("食べる")

        return results
    }
}