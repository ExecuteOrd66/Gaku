package ca.fuwafuwa.gaku.data.repository

import android.content.Context
import ca.fuwafuwa.gaku.Deinflictor.DeinflectionInfo
import ca.fuwafuwa.gaku.Deinflictor.Deinflector
import ca.fuwafuwa.gaku.data.AppDatabase
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryRepository(private val db: AppDatabase, context: Context? = null) {

    // Cache active dictionary IDs to avoid DB lookups on every keystroke
    private var activeDictIds: List<Long> = emptyList()
    private val deinflector: Deinflector? = context?.let { Deinflector(it) }

    suspend fun refreshActiveDictionaries() = withContext(Dispatchers.IO) {
        activeDictIds = db.dictionaryDao().getAllDictionaries()
            .filter { it.isEnabled }
            .map { it.id }
    }

    suspend fun searchTerms(rawQuery: String): List<Term> = withContext(Dispatchers.IO) {
        if (activeDictIds.isEmpty()) refreshActiveDictionaries()

        val deinflectionResults = getPotentialForms(rawQuery)
        val potentialForms = deinflectionResults.map { it.word }.distinct()

        val matches = db.termDao().findTermsByVariants(potentialForms, activeDictIds)
        return@withContext filterMatchesByDeinflection(matches, deinflectionResults)
    }

    suspend fun getDefinitions(termId: Long): List<Definition> = withContext(Dispatchers.IO) {
        return@withContext db.definitionDao().getDefinitionsForTerm(termId)
    }

    private fun getPotentialForms(text: String): List<DeinflectionInfo> {
        val real = deinflector?.getPotentialDeinflections(text)
        if (!real.isNullOrEmpty()) return real

        // Fallback for environments where Deinflector cannot be initialized yet.
        return listOf(DeinflectionInfo(text, 0xFF, ""))
    }

    private fun filterMatchesByDeinflection(
        terms: List<Term>,
        deinflections: List<DeinflectionInfo>
    ): List<Term> {
        val byWord = deinflections.groupBy { it.word }

        return terms.filter { term ->
            val candidates = byWord[term.expression].orEmpty() + byWord[term.reading].orEmpty()
            if (candidates.isEmpty()) {
                false
            } else {
                candidates.any { deinf -> validateDeinflectionType(term.rules, deinf.type) }
            }
        }
    }

    private fun validateDeinflectionType(termRules: String, deinfType: Int): Boolean {
        if (deinfType == 0xFF || termRules.isBlank()) return true

        val normalizedRules = termRules.split(" ", ",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        if (normalizedRules.isEmpty()) return true

        val isV1 = normalizedRules.any { it == "v1" }
        val isV5 = normalizedRules.any { it.startsWith("v5") }
        val isAdjI = normalizedRules.any { it == "adj-i" }
        val isVk = normalizedRules.any { it == "vk" }
        val isVs = normalizedRules.any { it.startsWith("vs") }

        return ((deinfType and 1) != 0 && isV1) ||
            ((deinfType and 2) != 0 && isV5) ||
            ((deinfType and 4) != 0 && isAdjI) ||
            ((deinfType and 8) != 0 && isVk) ||
            ((deinfType and 16) != 0 && isVs)
    }
}
