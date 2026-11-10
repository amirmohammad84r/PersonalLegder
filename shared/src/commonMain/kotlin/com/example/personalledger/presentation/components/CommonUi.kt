package com.example.personalledger.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.LedgerColors
import com.example.personalledger.presentation.theme.ledgerOutlinedFieldColors

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "بازگشت" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillMaxWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Cancel + Save (or confirm) side by side — for dialogs and form footers. */
@Composable
fun CancelSaveActions(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveText: String = Strings.save,
    cancelText: String = Strings.cancel,
    saving: Boolean = false,
    saveEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !saving,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(cancelText)
        }
        PrimaryButton(
            text = if (saving) Strings.saving else saveText,
            onClick = onSave,
            loading = saving,
            enabled = saveEnabled || saving,
            fillMaxWidth = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val fieldModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        readOnly = readOnly || onClick != null,
        enabled = onClick == null,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
        colors = ledgerOutlinedFieldColors(),
    )
}

@Composable
fun TypeSegmentedControl(
    selected: TransactionType,
    onSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentChip(
            text = Strings.expense,
            selected = selected == TransactionType.EXPENSE,
            onClick = { onSelected(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f),
        )
        SegmentChip(
            text = Strings.income,
            selected = selected == TransactionType.INCOME,
            onClick = { onSelected(TransactionType.INCOME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun FilterChipsRow(
    selected: TransactionType?,
    onSelected: (TransactionType?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(Strings.filterAll, selected == null) { onSelected(null) }
        FilterChip(Strings.income, selected == TransactionType.INCOME) {
            onSelected(TransactionType.INCOME)
        }
        FilterChip(Strings.expense, selected == TransactionType.EXPENSE) {
            onSelected(TransactionType.EXPENSE)
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TransactionRow(
    title: String,
    subtitle: String,
    amountText: String,
    type: TransactionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (type == TransactionType.INCOME) LedgerColors.IncomeGreen else LedgerColors.ExpenseRose
    val icon = if (type == TransactionType.INCOME) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text(Strings.retry) }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.delete) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(Strings.delete) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel) }
        },
    )
}

@Composable
fun ItemOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "گزینه‌ها" },
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("ویرایش") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.delete) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

enum class MainTab {
    Home,
    Goals,
    Stats,
    Ai,
    Settings,
}

@Composable
fun LedgerBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomItem(
                    Strings.goals,
                    Icons.Default.Favorite,
                    selected == MainTab.Goals,
                    Modifier.weight(1f),
                ) { onSelect(MainTab.Goals) }
                BottomItem(
                    Strings.stats,
                    Icons.Default.DateRange,
                    selected == MainTab.Stats,
                    Modifier.weight(1f),
                ) { onSelect(MainTab.Stats) }
                HomeBottomItem(
                    selected = selected == MainTab.Home,
                    modifier = Modifier.weight(1.15f),
                    onClick = { onSelect(MainTab.Home) },
                )
                BottomItem(
                    Strings.ai,
                    Icons.Default.Star,
                    selected == MainTab.Ai,
                    Modifier.weight(1f),
                ) { onSelect(MainTab.Ai) }
                BottomItem(
                    Strings.settings,
                    Icons.Default.Settings,
                    selected == MainTab.Settings,
                    Modifier.weight(1f),
                ) { onSelect(MainTab.Settings) }
            }
            // Keep tabs above 3-button / gesture system navigation
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun HomeBottomItem(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val circleColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .semantics { contentDescription = Strings.home },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            Strings.home,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SettingsIconButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = Strings.settings },
    ) {
        Icon(Icons.Default.Settings, contentDescription = null)
    }
}

@Composable
fun AddIconButton(onClick: () -> Unit, label: String = Strings.add) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(Strings.searchHint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = MaterialTheme.shapes.medium,
        colors = ledgerOutlinedFieldColors(),
    )
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
