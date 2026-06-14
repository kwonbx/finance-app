package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class EditRecurringViewModel(private val expenseRepository: ExpenseRepository) : ViewModel() {
    var id by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    fun updateTitle(input: String) { title = input }
    var category by mutableStateOf("")
        private set
    fun updateCategory(input: String) { category = input }
    var amount by mutableStateOf("")
        private set
    var frequencyDays by mutableStateOf("")
        private set
    var isActive by mutableStateOf(true)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var nextPaymentDateFormatted by mutableStateOf("")
        private set

    private var baseDate: LocalDate? = null
    private var currentNextDateMillis: Long = 0L

    fun loadRecurringExpense(recurringId: String) {
        if (id == recurringId) return
        isLoading = true
        expenseRepository.getRecurringExpense(
            recurringId = recurringId,
            onSuccess = { recurring ->
                if (recurring != null) {
                    id = recurring.id
                    title = recurring.title
                    category = recurring.category
                    amount = String.format(Locale.US, "%.2f", recurring.amount)
                    frequencyDays = recurring.frequencyDays.toString()
                    isActive = recurring.isActive

                    // Obliczenie daty bazowej (ostatniej płatności)
                    val nextDate = Instant.ofEpochMilli(recurring.nextPaymentDateInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    baseDate = nextDate.minusDays(recurring.frequencyDays.toLong())
                    currentNextDateMillis = recurring.nextPaymentDateInMillis

                    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    nextPaymentDateFormatted = nextDate.format(formatter)
                }
                isLoading = false
            },
            onFailure = {
                isLoading = false
            }
        )
    }

    fun updateAmount(input: String) { amount = input }

    fun updateFrequencyDays(input: String) {
        frequencyDays = input
        val freq = input.toIntOrNull()
        if (freq != null && freq > 0 && baseDate != null) {
            val newNextDate = baseDate!!.plusDays(freq.toLong())
            currentNextDateMillis = newNextDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            nextPaymentDateFormatted = newNextDate.format(formatter)
        }
    }

    fun saveChanges(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
        val parsedFreq = frequencyDays.toIntOrNull()

        if (title.isBlank()) {
            onError("Podaj tytuł")
            return
        }
        if (parsedAmount == null || parsedAmount <= 0) {
            onError("Podaj poprawną kwotę")
            return
        }
        if (parsedFreq == null || parsedFreq <= 0) {
            onError("Podaj poprawną częstotliwość (liczba dni)")
            return
        }

        isLoading = true
        expenseRepository.updateRecurringExpense(
            recurringId = id,
            newTitle = title,
            newCategory = category,
            newAmount = parsedAmount,
            newFrequency = parsedFreq,
            newNextDateMillis = currentNextDateMillis, // Zapis nowej daty
            onSuccess = {
                isLoading = false
                onSuccess()
            },
            onFailure = { e ->
                isLoading = false
                onError(e.message ?: "Błąd podczas zapisu zmian")
            }
        )
    }

    fun stopRenewing(onSuccess: () -> Unit, onError: (String) -> Unit) {
        isLoading = true
        expenseRepository.deactivateRecurringExpense(
            recurringId = id,
            onSuccess = {
                isActive = false
                isLoading = false
                onSuccess()
            },
            onFailure = { e ->
                isLoading = false
                onError(e.message ?: "Błąd podczas wyłączania odnawiania")
            }
        )
    }

    class Factory(private val expenseRepository: ExpenseRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditRecurringViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EditRecurringViewModel(expenseRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}