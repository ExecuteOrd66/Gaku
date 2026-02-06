package ca.fuwafuwa.gaku.Search

import ca.fuwafuwa.gaku.legacy.jmdict.models.EntryOptimized
import ca.fuwafuwa.gaku.Deinflictor.DeinflectionInfo

data class JmSearchResult(
        val entry: EntryOptimized,
        val deinfInfo: DeinflectionInfo,
        val word: String
)



