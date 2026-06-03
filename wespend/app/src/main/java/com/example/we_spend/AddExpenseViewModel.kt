package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.LocalDate
import java.time.ZoneId

class AddExpenseViewModel(private val expenseRepository: ExpenseRepository) : ViewModel() {
    var title by mutableStateOf("")
        private set
    var amount by mutableStateOf("")
        private set
    var type by mutableStateOf("Jednorazowy")
        private set
    var category by mutableStateOf("Jedzenie")
        private set
    var frequencyDays by mutableStateOf("")
        private set
    var nextPaymentDate by mutableStateOf<LocalDate?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    val categories = listOf("Jedzenie", "Transport", "Rozrywka", "Zdrowie", "Rachunki", "Inne")
    fun updateTitle(input: String) { title = input }
    fun updateAmount(input: String) { amount = input }
    fun updateCategory(input: String) { category = input }

    fun updateType(input: String) {
        type = input
        if (input == "Jednorazowy") {
            frequencyDays = ""
            nextPaymentDate = null
        }
    }

    fun updateFrequencyDays(input: String) {
        frequencyDays = input
        val days = input.toIntOrNull()
        if (days != null && days > 0) {
            nextPaymentDate = LocalDate.now().plusDays(days.toLong())
        } else {
            nextPaymentDate = null
        }
    }

    fun updateNextPaymentDate(date: LocalDate) {
        nextPaymentDate = date
    }

    fun saveExpense(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
        if (title.isBlank() || parsedAmount == null) {
            onError("Podaj poprawny tytuł i kwotę")
            return
        }

        var freq: Int? = null
        var nextDateMillis: Long? = null

        if (type == "Stały") {
            freq = frequencyDays.toIntOrNull()
            if (freq == null || freq <= 0) {
                onError("Podaj poprawną częstotliwość w dniach")
                return
            }
            if (nextPaymentDate == null) {
                onError("Wybierz datę następnej płatności")
                return
            }
            nextDateMillis = nextPaymentDate!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        isLoading = true

        val expense = Expense(
            title = title,
            amount = parsedAmount,
            type = type,
            category = category,
            dateInMillis = System.currentTimeMillis(),
            frequencyDays = freq,
            nextPaymentDateInMillis = nextDateMillis
        )

        expenseRepository.addExpense(
            expense = expense,
            onSuccess = {
                isLoading = false
                onSuccess()
            },
            onFailure = { e ->
                isLoading = false
                onError(e.message ?: "Błąd podczas zapisywania")
            }
        )
    }

    class Factory(private val expenseRepository: ExpenseRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddExpenseViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AddExpenseViewModel(expenseRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}