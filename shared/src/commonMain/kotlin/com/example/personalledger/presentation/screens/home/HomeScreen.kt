package com.example.personalledger.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.CurrencyConfig
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.DashboardSummary
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.presentation.components.ErrorBox
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.LedgerColors

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    categoryNames: Map<String, String>,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllTransactions: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        HomeUiState.Loading -> LoadingBox()
        is HomeUiState.Error -> ErrorBox(s.message)
        is HomeUiState.Ready -> HomeContent(
            summary = s.summary,
            categoryNames = categoryNames,
            onAccountFilterChange = viewModel::onAccountFilterChange,
            onShiftMonth = viewModel::shiftMonth,
            onAddTransaction = onAddTransaction,
            onOpenTransaction = onOpenTransaction,
            onSeeAllTransactions = onSeeAllTransactions,
        )
    }
}

@Composable
private fun HomeContent(
    summary: DashboardSummary,
    categoryNames: Map<String, String>,
    onAccountFilterChange: (String?) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllTransactions: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding)
                .padding(bottom = 88.dp),
        ) {
            Text(
                text = Strings.home,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            MonthSwitcher(
                label = summary.monthLabel.toPersianDigits(),
                onPrevious = { onShiftMonth(-1) },
                onNext = { onShiftMonth(1) },
            )
            Spacer(Modifier.height(14.dp))
            BalanceCard(
                summary = summary,
                onAccountFilterChange = onAccountFilterChange,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonthStatCard(
                    title = Strings.incomeThisMonth,
                    amount = MoneyFormatter.format(summary.monthIncomeRial),
                    icon = Icons.Default.KeyboardArrowUp,
                    accent = LedgerColors.IncomeGreen,
                    modifier = Modifier.weight(1f),
                )
                MonthStatCard(
                    title = Strings.expenseThisMonth,
                    amount = MoneyFormatter.format(summary.monthExpenseRial),
                    icon = Icons.Default.KeyboardArrowDown,
                    accent = LedgerColors.ExpenseRose,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Strings.recentTransactions,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSeeAllTransactions) {
                    Text(Strings.seeAllTransactions, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (summary.recentTransactions.isEmpty()) {
                Text(
                    text = Strings.emptyTransactions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    summary.recentTransactions.forEach { tx ->
                        HomeTransactionCard(
                            transaction = tx,
                            categoryName = categoryNames[tx.categoryId].orEmpty(),
                            onClick = { onOpenTransaction(tx.id) },
                        )
                    }
                }
            }
        }

        Button(
            onClick = onAddTransaction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth(0.62f)
                .height(54.dp),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = Strings.addTransactionFab,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun MonthSwitcher(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.semantics { contentDescription = Strings.previousMonth },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = Strings.nextMonth },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BalanceCard(
    summary: DashboardSummary,
    onAccountFilterChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (summary.selectedAccountId) {
        null -> Strings.accountFilterAll
        else -> summary.accounts.find { it.id == summary.selectedAccountId }?.name
            ?: Strings.accountFilterAll
    }
    val activeLabel =
        "${Strings.activeAccountsPrefix} ${summary.activeAccountCount.toString().toPersianDigits()}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .semantics { contentDescription = Strings.selectAccount }
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { expanded = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                AccountFilterMenu(
                    expanded = expanded,
                    accounts = summary.accounts,
                    onDismiss = { expanded = false },
                    onSelect = {
                        onAccountFilterChange(it)
                        expanded = false
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = MoneyFormatter.format(summary.totalBalanceRial, withCurrency = false),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = CurrencyConfig.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = Strings.totalBalance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = activeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountFilterMenu(
    expanded: Boolean,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.75f),
    ) {
        DropdownMenuItem(
            text = { Text(Strings.accountFilterAll) },
            onClick = { onSelect(null) },
        )
        accounts.forEach { account ->
            DropdownMenuItem(
                text = { Text(account.name) },
                onClick = { onSelect(account.id) },
            )
        }
    }
}

@Composable
private fun MonthStatCard(
    title: String,
    amount: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeTransactionCard(
    transaction: Transaction,
    categoryName: String,
    onClick: () -> Unit,
) {
    val income = transaction.type == TransactionType.INCOME
    val accent = if (income) LedgerColors.IncomeGreen else LedgerColors.ExpenseRose
    val icon = if (income) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
    val amountText = buildString {
        append(if (income) "+" else "−")
        append(MoneyFormatter.format(transaction.amountRial, withCurrency = false))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = categoryName,
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
                color = accent,
            )
        }
    }
}
