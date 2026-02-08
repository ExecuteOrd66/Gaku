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
        // Fix: Definitions are now embedded in the Term object, no DB call needed.
        onResult(term.definitions)
    }
}