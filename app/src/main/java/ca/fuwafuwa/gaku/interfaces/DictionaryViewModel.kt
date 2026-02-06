package ca.fuwafuwa.gaku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.fuwafuwa.gaku.data.Definition
import ca.fuwafuwa.gaku.data.Term
import ca.fuwafuwa.gaku.data.repository.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DictionaryViewModel(private val repository: DictionaryRepository) : ViewModel() {

    // State for the list of words found (Headwords + Readings only)
    private val _searchResults = MutableStateFlow<List<Term>>(emptyList())
    val searchResults: StateFlow<List<Term>> = _searchResults

    // Cache definitions in memory so we don't re-query DB if user taps same word twice
    private val definitionCache = mutableMapOf<Long, List<Definition>>()

    fun search(query: String) {
        viewModelScope.launch {
            val results = repository.searchTerms(query)
            _searchResults.value = results
        }
    }

    /**
     * This is called when the RecyclerView/List binds a specific item,
     * or when the user expands a card.
     */
    fun loadDefinitionsForTerm(term: Term, onResult: (List<Definition>) -> Unit) {
        // 1. Check Memory Cache
        if (definitionCache.containsKey(term.id)) {
            onResult(definitionCache[term.id]!!)
            return
        }

        // 2. Load from DB
        viewModelScope.launch {
            val defs = repository.getDefinitions(term.id)

            // 3. Process Structured Content if necessary
            // Yomitan definitions can be strings OR JSON objects.
            // You might want to parse the JSON here before sending to UI.

            definitionCache[term.id] = defs
            onResult(defs)
        }
    }
}