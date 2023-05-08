package com.example.android.mentiontext.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.items
import com.example.android.mentiontext.data.Mention

@Composable
fun MentionsWindow(
    modifier: Modifier = Modifier,
    popupHeight: Dp,
    mentionsListItems: LazyPagingItems<Mention>?,
    onItemClicked: (mention: Mention) -> Unit
) {
    var mentionsLoading by remember { mutableStateOf(true) }

    LazyColumn(modifier = modifier.heightIn(max = popupHeight)) {

        items(5) {
            if (mentionsListItems == null || mentionsListItems.itemCount == 0 || mentionsLoading) {
                MentionItem(
                    isShimmerEffect = true,
                    modifier = Modifier,
                    pictureUrl = "",
                    name = "",
                    username = "",
                    isUser = true,
                )
            }
        }

        mentionsListItems?.let {
            items(mentionsListItems) { mention ->
                MentionItem(
                    isShimmerEffect = false,
                    modifier = Modifier.clickable(onClick = { //handle onClick
                        if (mention != null) {
                            onItemClicked(mention)
                        }
                    }),
                    pictureUrl = mention?.pictureUrl ?: "",
                    name = mention?.getFullName() ?: "",
                    isUser = true,
                )

            }
        }

        mentionsListItems?.apply {
            when {
                loadState.refresh is LoadState.NotLoading -> {
                    mentionsLoading = false
                }

                loadState.append is LoadState.NotLoading -> {
                    mentionsLoading = false
                }
            }
        }
    }

}