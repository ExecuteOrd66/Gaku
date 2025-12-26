package ca.fuwafuwa.gaku.Search

import ca.fuwafuwa.gaku.Database.JmDictDatabase.Models.EntryOptimized
import ca.fuwafuwa.gaku.Deinflictor.DeinflectionInfo

data class JmSearchResult(
        val entry: EntryOptimized,
        val deinfInfo: DeinflectionInfo,
        val word: String
)



