package com.example.personalledger.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.presentation.components.ErrorBox
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.components.TransactionRow
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings

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
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllTransactions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text(
            text = Strings.home,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))
        WalletCard(
            summary = summary,
            onAccountFilterChange = onAccountFilterChange,
            onAddTransaction = onAddTransaction,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = summary.monthLabel.toPersianDigits(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthSummaryCard(
                title = Strings.incomeThisMonth,
                amount = MoneyFormatter.format(summary.monthIncomeRial),
                modifier = Modifier.weight(1f),
            )
            MonthSummaryCard(
                title = Strings.expenseThisMonth,
                amount = MoneyFormatter.format(summary.monthExpenseRial),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Strings.recentThisMonth,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddTransaction) {
                Text(Strings.add, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (summary.recentTransactions.isEmpty()) {
            HomeEmptyTransactions(onAddTransaction = onAddTransaction)
        } else {
            LedgerCard {
                summary.recentTransactions.forEach { tx ->
                    TransactionRow(
                        title = tx.title,
                        subtitle = categoryNames[tx.categoryId].orEmpty(),
                        amountText = MoneyFormatter.formatSigned(
                            tx.amountRial,
                            income = tx.type == TransactionType.INCOME,
                        ),
                        type = tx.type,
                        onClick = { onOpenTransaction(tx.id) },
                    )
                }
            }
            TextButton(
                onClick = onSeeAllTransactions,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(Strings.seeAllTransactions, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WalletCard(
    summary: DashboardSummary,
    onAccountFilterChange: (String?) -> Unit,
    onAddTransaction: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (summary.selectedAccountId) {
        null -> Strings.accountFilterAll
        else -> summary.accounts.find { it.id == summary.selectedAccountId }?.name
            ?: Strings.accountFilterAll
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = Strings.selectedAccountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .semantics { contentDescription = Strings.selectAccount }
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { expanded = true }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = selectedLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
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
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .clickable(onClick = onAddTransaction)
                        .semantics { contentDescription = Strings.addTransaction },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = Strings.totalBalance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = MoneyFormatter.format(summary.totalBalanceRial, withCurrency = false),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = CurrencyConfig.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BalanceChip(
                    label = Strings.bankAccountsLabel,
                    amount = MoneyFormatter.format(summary.bankBalanceRial),
                    modifier = Modifier.weight(1f),
                )
                BalanceChip(
                    label = Strings.cashAccountsLabel,
                    amount = MoneyFormatter.format(summary.cashBalanceRial),
                    modifier = Modifier.weight(1f),
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
private fun BalanceChip(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MonthSummaryCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HomeEmptyTransactions(onAddTransaction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape = CircleShape,
                )
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = Strings.emptyTransactions,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = Strings.emptyTransactionsHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAddTransaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = Strings.addTransaction,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}
