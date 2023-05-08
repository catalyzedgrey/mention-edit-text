package com.example.android.mentiontext.presentation

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.example.android.mentiontext.data.Mention
import com.example.android.mentiontext.domain.MentionsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.regex.Pattern
import javax.inject.Inject

@HiltViewModel
class MentionsViewModel @Inject constructor(
) : ViewModel() {

    var usersMap: MutableMap<Pattern, Mention> = mutableStateMapOf()

    private var lastQuery = ""

    val searchQueryFlow = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMentions = searchQueryFlow.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 10)) {
            MentionsSource(query)
        }.flow.cachedIn(viewModelScope)
    }

    fun searchMentions(query: String) {
        if (query == lastQuery && (query == "" || query == "@")) return
        lastQuery = query
        var searchQuery = ""

        val list = query.split("((\\s)|(?<=\\n)|(?=\\n))".toRegex())

        val isStartingWithAtSymbolName =
            (list.size >= 2
                    && (list[list.lastIndex - 1].startsWith("@")
                    || list[list.lastIndex - 1].startsWith("\n@"))
                    )

        val isStartingWithAtSymbol =
            list[list.lastIndex].startsWith("@") || list[list.lastIndex].startsWith("\n@")

        val topLevelCondition = isStartingWithAtSymbol || isStartingWithAtSymbolName

        val matcher = Pattern.compile("@").matcher(query)
        if (topLevelCondition && matcher.find()) {
            if (isStartingWithAtSymbolName) {
                searchQuery =
                    "${list[list.lastIndex - 1]} ${list[list.lastIndex]}".trim()

                if (searchQuery.isEmpty()) {
                    searchQueryFlow.value = ""
                } else if (searchQueryFlow.value != searchQuery) {
                    searchQueryFlow.value = searchQuery.removePrefix("@")
                }

            } else {
                if (list[list.lastIndex].length == 1)
                    searchQuery = ""
                else
                    searchQuery =
                        list[list.lastIndex].trim()

                if (searchQuery.isEmpty()) {
                    searchQueryFlow.value = ""
                } else if (searchQueryFlow.value != searchQuery) {
                    searchQueryFlow.value = searchQuery.removePrefix("@")
                }

            }
        }
    }
}
