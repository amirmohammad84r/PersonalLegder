package com.example.personalledger.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.calendar.JalaliDateConverter
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Date picker that presents the user's [calendar] and returns a storage [LocalDate]
 * (Gregorian civil day / epoch days).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initialDate: LocalDate,
    calendar: CalendarSystem,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    when (calendar) {
        CalendarSystem.GREGORIAN -> GregorianMaterialDatePicker(
            initialDate = initialDate,
            calendar = calendar,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        CalendarSystem.JALALI -> JalaliSpinnerDatePicker(
            initialDate = initialDate,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GregorianMaterialDatePicker(
    initialDate: LocalDate,
    calendar: CalendarSystem,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val millis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { selected ->
                        val date = Instant.fromEpochMilliseconds(selected)
                            .toLocalDateTime(TimeZone.UTC)
                            .date
                        onConfirm(date)
                    }
                    onDismiss()
                },
            ) { Text(Strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel) }
        },
    ) {
        Column {
            val previewMillis = pickerState.selectedDateMillis ?: millis
            val previewDate = Instant.fromEpochMilliseconds(previewMillis)
                .toLocalDateTime(TimeZone.UTC)
                .date
            Text(
                text = "${Strings.date}: ${DateFormatter.format(previewDate, calendar)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JalaliSpinnerDatePicker(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = JalaliDateConverter.toJalali(initialDate)
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }

    val maxDay = AppCalendar.daysInMonth(year, month, CalendarSystem.JALALI)
    val safeDay = minOf(day, maxDay)

    val years = remember(initial.year) {
        ((initial.year - 40)..(initial.year + 20)).toList()
    }
    val months = (1..12).toList()
    val days = (1..maxDay).toList()

    val preview = AppCalendar.toLocalDate(year, month, safeDay, CalendarSystem.JALALI)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.date) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = preview?.let {
                        DateFormatter.formatFull(it, CalendarSystem.JALALI)
                    } ?: Strings.errorGeneric,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntDropdown(
                        label = Strings.calendarYear,
                        value = year,
                        options = years,
                        format = { it.toString().toPersianDigits() },
                        onSelect = { year = it },
                        modifier = Modifier.weight(1.2f),
                    )
                    IntDropdown(
                        label = Strings.calendarMonth,
                        value = month,
                        options = months,
                        format = { JalaliDateConverter.monthName(it) },
                        onSelect = { month = it },
                        modifier = Modifier.weight(1.4f),
                    )
                    IntDropdown(
                        label = Strings.calendarDay,
                        value = safeDay,
                        options = days,
                        format = { it.toString().toPersianDigits() },
                        onSelect = { day = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    preview?.let(onConfirm)
                    onDismiss()
                },
                enabled = preview != null,
            ) { Text(Strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntDropdown(
    label: String,
    value: Int,
    options: List<Int>,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = format(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(format(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
