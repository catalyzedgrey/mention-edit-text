package com.example.android.mentiontext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.android.mentiontext.data.Mention
import com.example.android.mentiontext.presentation.MentionEditTextBox
import com.example.android.mentiontext.presentation.MentionsViewModel
import com.example.android.mentiontext.ui.theme.MentionTextTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.regex.Pattern

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MentionTextTheme {
                val postText = remember { mutableStateOf("") }
                val isMentionText = remember { mutableStateOf(false) }
                val viewModel: MentionsViewModel = hiltViewModel()

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)) {
                    MentionEditTextBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        postText = postText,
                        isMentionText = isMentionText,
                        scrollState = rememberScrollState(),
                        usersMap = viewModel.usersMap
                    )

                }
            }
        }
    }
}