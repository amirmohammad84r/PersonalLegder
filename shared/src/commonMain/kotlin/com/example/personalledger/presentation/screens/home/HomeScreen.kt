package com.example.personalledger.presentation.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.DashboardSummary
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.presentation.components.EmptyState
import com.example.personalledger.presentation.components.ErrorBox
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.components.TransactionRow
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
        HomeHeader(
            accounts = summary.accounts,
            selectedAccountId = summary.selectedAccountId,
            onAccountFilterChange = onAccountFilterChange,
        )
        Spacer(Modifier.height(8.dp))
        LedgerCard {
            Text(
                text = MoneyFormatter.format(summary.totalBalanceRial),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = summary.monthLabel.toPersianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryMiniCard(
                title = Strings.incomeThisMonth,
                amount = MoneyFormatter.format(summary.monthIncomeRial),
                accent = LedgerColors.IncomeGreen,
                modifier = Modifier.weight(1f),
            )
            SummaryMiniCard(
                title = Strings.expenseThisMonth,
                amount = MoneyFormatter.format(summary.monthExpenseRial),
                accent = LedgerColors.ExpenseRose,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
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
            EmptyState(
                title = Strings.emptyTransactions,
                subtitle = Strings.emptyTransactionsHint,
                actionLabel = Strings.addTransaction,
                onAction = onAddTransaction,
            )
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
private fun HomeHeader(
    accounts: List<Account>,
    selectedAccountId: String?,
    onAccountFilterChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (selectedAccountId) {
        null -> Strings.accountFilterAll
        else -> accounts.find { it.id == selectedAccountId }?.name ?: Strings.accountFilterAll
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = Strings.home,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Strings.account,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = Strings.selectAccount }
                        .clickable { expanded = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f),
                ) {
                    DropdownMenuItem(
                        text = { Text(Strings.accountFilterAll) },
                        onClick = {
                            onAccountFilterChange(null)
                            expanded = false
                        },
                    )
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                onAccountFilterChange(account.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMiniCard(
    title: String,
    amount: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = accent)
        Spacer(Modifier.height(6.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
