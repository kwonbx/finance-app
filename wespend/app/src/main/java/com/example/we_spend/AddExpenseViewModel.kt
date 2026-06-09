package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AddExpenseViewModel(private val expenseRepository: ExpenseRepository, private val userRepository: UserRepository) : ViewModel() {
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
    var expenseDate by mutableStateOf(LocalDate.now())
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
        } else {
            recalculateNextPaymentDate()
        }
    }

    fun updateFrequencyDays(input: String) {
        frequencyDays = input
        recalculateNextPaymentDate()
    }

    fun updateNextPaymentDate(date: LocalDate) {
        nextPaymentDate = date
    }

    fun updateExpenseDate(date: LocalDate) {
        expenseDate = date
        recalculateNextPaymentDate()
    }

    private fun recalculateNextPaymentDate() {
        if (type == "Stały") {
            val days = frequencyDays.toIntOrNull()
            if (days != null && days > 0) {
                nextPaymentDate = expenseDate.plusDays(days.toLong())
            } else {
                nextPaymentDate = null
            }
        }
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
            nextDateMillis = nextPaymentDate!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        isLoading = true

        viewModelScope.launch {
            val userProfile = userRepository.getUserProfile()
            val currentFamilyId = userProfile?.familyId

            val expenseDateMillis = expenseDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val expense = Expense(
                title = title,
                amount = parsedAmount,
                type = type,
                category = category,
                dateInMillis = expenseDateMillis,
                frequencyDays = freq,
                nextPaymentDateInMillis = nextDateMillis,
                familyId = currentFamilyId
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
    }
    fun onReceiptScanned(shopName: String, scannedAmount: String, scannedDate: String) {
        if (shopName.isNotBlank()) {
            title = shopName
        }
        if (scannedAmount.isNotBlank()) {
            amount = scannedAmount
        }

        if (scannedDate.isNotBlank()) {
            try {
                val cleanDate = scannedDate.replace("-", ".")
                val formatter = if (cleanDate.matches("""^\d{4}\..*""".toRegex())) {
                    DateTimeFormatter.ofPattern("yyyy.MM.dd")
                } else {
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                }

                expenseDate = LocalDate.parse(cleanDate, formatter)
                recalculateNextPaymentDate()
            } catch (e: Exception) {
            }
        }
    }

    class Factory(private val expenseRepository: ExpenseRepository, private val userRepository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddExpenseViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AddExpenseViewModel(expenseRepository, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}