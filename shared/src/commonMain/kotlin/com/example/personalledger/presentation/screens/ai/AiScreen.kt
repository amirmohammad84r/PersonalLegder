package com.example.personalledger.presentation.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatRole
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.ledgerOutlinedFieldColors

@Composable
fun AiScreen(viewModel: AiViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingBox()
        return
    }
    AiChatPane(state = state, viewModel = viewModel)
    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReset,
            title = { Text(Strings.aiResetTitle) },
            text = { Text(Strings.aiResetConfirm) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReset) {
                    Text(Strings.aiResetAction)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReset) {
                    Text(Strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun AiChatPane(
    state: AiUiState,
    viewModel: AiViewModel,
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.messages.size, state.asking) {
        val last = state.messages.lastIndex
        if (last >= 0) listState.animateScrollToItem(last)
    }

    // Keep header pinned; only shrink the message list when the keyboard opens.
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ScreenHeader(
            title = Strings.aiScreenTitle,
            subtitle = if (state.ready) Strings.aiReadySubtitle else Strings.aiUnavailableSubtitle,
            actions = {
                TextButton(onClick = viewModel::requestReset) {
                    Text(Strings.aiResetAction)
                }
            },
        )

        if (!state.ready) {
            LedgerCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = state.statusMessage.ifBlank { Strings.aiUnavailableHint },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = Strings.aiPrivacyNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.ready && state.messages.isEmpty() && !state.asking) {
                item {
                    LedgerCard {
                        Text(
                            Strings.aiEmptyChatHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
            if (state.asking) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(Strings.aiThinking)
                            TextButton(onClick = viewModel::cancelAsk) {
                                Text(Strings.aiCancelRequest)
                            }
                        }
                    }
                }
            }
            state.chatError?.let { error ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            TextButton(onClick = viewModel::clearChatError) {
                                Text(Strings.retry)
                            }
                        }
                    }
                }
            }
            state.resetError?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::updateDraft,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 120.dp),
                    placeholder = { Text(Strings.aiQuestionLabel) },
                    enabled = state.ready && !state.asking,
                    maxLines = 4,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            viewModel.sendQuestion()
                        },
                    ),
                    shape = MaterialTheme.shapes.medium,
                    colors = ledgerOutlinedFieldColors(),
                )
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        viewModel.sendQuestion()
                    },
                    enabled = state.ready && state.draft.isNotBlank() && !state.asking,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = Strings.aiAsk)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) Strings.aiYourQuestion else Strings.aiAnswer,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(4.dp))
                if (isUser) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                } else {
                    SafeAssistantText(message.content)
                }
            }
        }
    }
}
