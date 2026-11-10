package com.example.personalledger.presentation.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatRole
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LedgerTextField
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.PrimaryButton
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.ledgerOutlinedFieldColors

@Composable
fun AiScreen(viewModel: AiViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingBox()
        return
    }
    when (state.pane) {
        AiPane.SETUP -> AiSetupPane(state = state, viewModel = viewModel)
        AiPane.CHAT -> AiChatPane(state = state, viewModel = viewModel)
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSetupPane(
    state: AiUiState,
    viewModel: AiViewModel,
) {
    val form = state.setup
    val showBack = state.connection?.validated == true
    val selectedLabel = form.selectedModel?.let { "${it.displayName} (${it.id})" }
        ?: form.modelId.takeIf { it.isNotBlank() }
        ?: Strings.aiSelectedModelNone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(
            title = Strings.aiScreenTitle,
            subtitle = Strings.aiSetupSubtitle,
            onBack = if (showBack) viewModel::closeConnectionSettingsIfConnected else null,
            actions = {
                if (showBack) {
                    TextButton(onClick = viewModel::requestReset) {
                        Text(Strings.aiResetAction)
                    }
                }
            },
        )
        Spacer(Modifier.height(12.dp))

        LedgerCard {
            Text(
                text = Strings.aiPrivacyNotice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = Strings.aiDirectConnectionLimits,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = Strings.aiProviderLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.supportedProviders.forEach { provider ->
                FilterChip(
                    selected = form.providerId == provider,
                    onClick = { viewModel.selectProvider(provider) },
                    label = { Text(provider.displayName) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = form.providerId.accountHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        AiApiKeyField(
            value = form.apiKeyInput,
            visible = form.apiKeyVisible,
            onValueChange = viewModel::updateApiKey,
            onToggleVisibility = viewModel::toggleApiKeyVisibility,
            isError = form.fieldError == Strings.aiApiKeyRequired,
            supporting = when {
                form.fieldError == Strings.aiApiKeyRequired -> form.fieldError
                else -> Strings.aiApiKeyHint
            },
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = Strings.aiModelLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        LedgerTextField(
            value = selectedLabel,
            onValueChange = {},
            label = Strings.aiPickModelTitle,
            readOnly = true,
            onClick = viewModel::openModelPicker,
            trailingIcon = {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            },
            isError = form.fieldError == Strings.aiModelMustSelectFromList,
            supportingText = form.fieldError
                ?.takeIf { it == Strings.aiModelMustSelectFromList },
        )
        if (form.models.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (form.modelsLive) Strings.aiModelsLive else Strings.aiModelsStatic,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = Strings.aiTestAndSave,
            onClick = viewModel::testAndSaveConnection,
            loading = form.testStatus == ConnectionTestStatus.TESTING,
            enabled = form.testStatus != ConnectionTestStatus.TESTING,
        )
        form.testMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            val color = when (form.testStatus) {
                ConnectionTestStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                ConnectionTestStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(message, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        state.resetError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (!showBack) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::requestReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(Strings.aiResetAction)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (form.showModelPicker) {
        ModelPickerSheet(
            form = form,
            onDismiss = viewModel::dismissModelPicker,
            onQueryChange = viewModel::updateModelQuery,
            onRetry = viewModel::loadModels,
            onSelect = viewModel::selectModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    form: AiSetupFormState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "${Strings.aiPickModelTitle} · ${form.providerId.displayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.modelQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(Strings.aiSearchModels) },
                shape = MaterialTheme.shapes.medium,
                colors = ledgerOutlinedFieldColors(),
            )
            Spacer(Modifier.height(8.dp))

            when {
                form.modelsLoading -> {
                    Spacer(Modifier.height(24.dp))
                    Text(Strings.loading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                form.modelsError != null && form.models.isEmpty() -> {
                    Spacer(Modifier.height(12.dp))
                    Text(form.modelsError, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text(Strings.aiRetryLoadModels)
                    }
                }
                form.models.isEmpty() -> {
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.aiNoModels, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text(Strings.aiLoadModels)
                    }
                }
                form.filteredModels.isEmpty() -> {
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.aiNoModelMatches, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(form.filteredModels, key = { it.id }) { model ->
                            ModelPickerRow(
                                model = model,
                                selected = model.id == form.modelId,
                                onClick = { onSelect(model.id) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModelPickerRow(
    model: AiModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = model.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiApiKeyField(
    value: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    isError: Boolean,
    supporting: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(Strings.aiApiKeyLabel) },
        singleLine = true,
        isError = isError,
        supportingText = { Text(supporting) },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = onToggleVisibility) {
                Text(if (visible) Strings.aiHideKey else Strings.aiShowKey)
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = ledgerOutlinedFieldColors(),
    )
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

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = Strings.aiScreenTitle,
            subtitle = state.connection?.let {
                "${it.providerId.displayName} · ${it.modelId}"
            },
            actions = {
                IconButton(onClick = viewModel::openConnectionSettings) {
                    Icon(Icons.Default.Settings, contentDescription = Strings.aiConnectionSettings)
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty() && !state.asking) {
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
        }

        Surface(tonalElevation = 2.dp) {
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
                        .heightIn(min = 52.dp),
                    placeholder = { Text(Strings.aiQuestionLabel) },
                    enabled = !state.asking,
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
                    enabled = state.draft.isNotBlank() && !state.asking,
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
                Spacer(Modifier.height(4.dp))
                if (isUser) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                } else {
                    SafeAssistantText(message.content)
                }
            }
        }
    }
}
