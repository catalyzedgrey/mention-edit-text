package com.example.android.mentiontext.presentation

import android.graphics.Color
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.widget.doAfterTextChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.android.mentiontext.R
import com.example.android.mentiontext.data.Mention
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.regex.Pattern

@Composable
fun MentionEditTextBox(
    modifier: Modifier,
    postText: MutableState<String>,
    isMentionText: MutableState<Boolean>,
    scrollState: ScrollState,
    usersMap: MutableMap<Pattern, Mention>?,
    mentionsViewModel: MentionsViewModel = hiltViewModel(),
) {

    var mentionEd: WeakReference<MentionEditText>? = null
    var offsetY by remember { mutableStateOf(0f) }
    var symbolPosition by remember { mutableStateOf(-1) }
    val mentionsList = mutableListOf<Mention>()
    val coroutineScope = rememberCoroutineScope()

    val isStartingWithAtSymbol = remember { mutableStateOf(false) }
    val isStartingWithAtSymbolName = remember { mutableStateOf(false) }


    var mSearchQuery: String by remember {
        mutableStateOf("")
    }

    val mentionsListItems: LazyPagingItems<Mention> =
        mentionsViewModel.pagedMentions.collectAsLazyPagingItems()


    val addMention: (Mention, String) -> Unit = { mention, _ ->
        val fullName = mention.getFullName()
        when (symbolPosition) {
            -1, 0 -> {
                if (postText.value.isNotEmpty()) {
                    if (postText.value.last() == '@')
                        postText.value = postText.value.dropLast(1)
                    postText.value += "$fullName "
                }
            }

            else -> {
                try {
                    var tempString = postText.value
                    val startIndex = symbolPosition
                    val endIndex = symbolPosition + mSearchQuery.length
                    tempString = tempString.replaceRange(
                        startIndex = startIndex,
                        endIndex = endIndex,
                        replacement = "$fullName "
                    )
                    postText.value = tempString

                } catch (e: IndexOutOfBoundsException) {
                    Timber.e(e.stackTraceToString())
                }
            }
        }

        isStartingWithAtSymbolName.value = false

        mentionsList.add(mention)
        usersMap?.put(Pattern.compile(fullName), mention)
        mentionEd?.get()?.setPatterns(usersMap)
        mentionEd?.get()?.getMentionList()?.addAll(mentionsList)

        try {
            mentionEd?.get()?.setText(postText.value, TextView.BufferType.SPANNABLE)
        } catch (e: Exception) {
            Timber.e("MentionEditText: ${e.stackTraceToString()}")
        }
    }

    var popupHeight by remember {
        mutableStateOf(
            when (mentionsListItems.itemCount) {
                0 -> 0.dp
                1 -> 50.dp
                2 -> 100.dp
                3 -> 150.dp
                4 -> 200.dp
                else -> 230.dp
            }
        )
    }
    LaunchedEffect(
        key1 = mentionsListItems.itemSnapshotList.size,
        key2 = mentionsListItems.loadState
    ) {
        if (mentionsListItems.loadState.refresh != LoadState.Loading)
            popupHeight = when (mentionsListItems.itemCount) {
                0 -> 0.dp
                1 -> 50.dp
                2 -> 100.dp
                3 -> 150.dp
                4 -> 200.dp
                else -> 230.dp
            }
    }

    fun MentionEditText?.getCurrentWord(): String {
        val textSpan = this?.text
        val selection = this?.selectionStart
        val pattern = Pattern.compile("(^|\\s)@(\\w+?\\s)?(\\w+)?")
        val matcher = textSpan?.let { pattern.matcher(it) }
        var start = 0
        var end = 0
        var currentWord = ""
        while (matcher?.find() == true) {
            start = matcher.start()
            end = matcher.end()
            if (start <= (selection ?: 0) && (selection ?: 0) <= end) {
                currentWord = textSpan.subSequence(start, end).toString()

                break
            }
        }
        return currentWord // This is current word
    }

    fun MentionEditText.isCursorPositionStartingWithAtSymbol() {
        val isCursorPositionValid = ((selectionStart == selectionEnd) && !isSelected)

        val word = this.getCurrentWord()

        val isStartingWithAt = word.trimStart().getOrNull(0) == '@'

        if (isCursorPositionValid && isStartingWithAt) {

            mSearchQuery = word.trim()

            symbolPosition = text?.indexOf(mSearchQuery) ?: -1
            isStartingWithAtSymbol.value = true
            mentionsViewModel.searchMentions(mSearchQuery)

            offsetY = currentCursorLine

            coroutineScope.launch {
                if (selectionStart == selectionEnd
                    && !isSelected
                    && text?.length == selectionStart
                )
                    scrollState.scrollTo(bottom)
            }
        } else symbolPosition = -1
    }

    ConstraintLayout(
        modifier = modifier
    ) {
        val (androidView, mentionsPopup) = createRefs()

        AndroidView(
            factory = { context ->

                val mentionEditText: MentionEditText = LayoutInflater.from(context)
                    .inflate(R.layout.mention_layout, null, false) as MentionEditText
                arrayOf(InputFilter.LengthFilter(2200))
                mentionEditText.setOnKeyListener { _, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_DEL) {
                        isMentionText.value = false
                    }
                    false
                }

                mentionEditText.setText(postText.value)

                mentionEditText.doAfterTextChanged { query ->
                    postText.value = query.toString()
                    mentionEditText.isCursorPositionStartingWithAtSymbol()

                    offsetY = mentionEditText.currentCursorLine

                    coroutineScope.launch {
                        if (mentionEditText.selectionStart == mentionEditText.selectionEnd
                            && !mentionEditText.isSelected
                            && mentionEditText.text?.length == mentionEditText.selectionStart
                        )
                            scrollState.scrollTo(mentionEditText.bottom)
                    }
                }
                mentionEditText.setMentionTextColor(Color.parseColor("#FF3F3FD1"))
                mentionEditText.setOnMentionInputListener(object :
                    MentionEditText.OnMentionInputListener {
                    override fun onMentionCharacterInput(isMention: Boolean) {
                        // call when '@' character is inserted into EditText
                        isMentionText.value = isMention
                    }
                })
                mentionEditText
            },
            update = { mentionEditText ->
                mentionEd = WeakReference(mentionEditText)
            },
            modifier = Modifier
                .constrainAs(androidView) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
                .fillMaxWidth()
        )

        if (isStartingWithAtSymbol.value || isStartingWithAtSymbolName.value) {
            Box(
                modifier = modifier
                    .padding(top = 26.dp)
                    .offset(0.dp, offsetY.dp)
                    .constrainAs(mentionsPopup) {
                        top.linkTo(androidView.top)
                        start.linkTo(parent.start)
                    }
            ) {
                Popup(
                    properties = PopupProperties(focusable = false),
                    onDismissRequest = {
                        isStartingWithAtSymbolName.value = false
                        isStartingWithAtSymbol.value = false
                        isMentionText.value = false
                        mentionsListItems.refresh()
                    }
                ) {

                    Card(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp)
                    ) {
                        MentionsWindow(
                            popupHeight = popupHeight,
                            mentionsListItems = mentionsListItems,
                        ) { mention ->
                            addMention(mention, mSearchQuery)
                        }
                    }
                }
            }
        }
    }
}