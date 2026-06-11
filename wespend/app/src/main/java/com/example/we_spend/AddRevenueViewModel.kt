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

class AddRevenueViewModel(private val revenueRepository: RevenueRepository, private val userRepository: UserRepository) : ViewModel() {
    var title by mutableStateOf("")
        private set
    var amount by mutableStateOf("")
        private set
    var type by mutableStateOf("Jednorazowy")
        private set
    var category by mutableStateOf("Wynagrodzenie")
        private set
    var frequencyDays by mutableStateOf("")
        private set
    var nextPaymentDate by mutableStateOf<LocalDate?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var revenueDate by mutableStateOf(LocalDate.now())
        private set

    val categories = listOf("Wynagrodzenie", "Premia", "Prezent", "Inwestycje", "Sprzedaż", "Inne")
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

    fun updateRevenueDate(date: LocalDate) {
        revenueDate = date
        recalculateNextPaymentDate()
    }

    private fun recalculateNextPaymentDate() {
        if (type == "Stały") {
            val days = frequencyDays.toIntOrNull()
            if (days != null && days > 0) {
                nextPaymentDate = revenueDate.plusDays(days.toLong())
            } else {
                nextPaymentDate = null
            }
        }
    }

    fun saveRevenue(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
        if (title.isBlank() || parsedAmount == null) {
            onError("Podaj poprawny tytuł i kwotę")
            return
        }

        isLoading = true

        viewModelScope.launch {
            val userProfile = userRepository.getUserProfile()
            val currentFamilyId = userProfile?.familyId
            val revenueDateMillis = revenueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (type == "Jednorazowy") {
                val revenue = Revenue(
                    title = title,
                    amount = parsedAmount,
                    type = type,
                    category = category,
                    dateInMillis = revenueDateMillis,
                    familyId = currentFamilyId
                )

                revenueRepository.addRevenue(
                    revenue = revenue,
                    onSuccess = {
                        isLoading = false
                        title = ""
                        amount = ""
                        type = "Jednorazowy"
                        category = "Wynagrodzenie"
                        onSuccess()
                    },
                    onFailure = { e ->
                        isLoading = false
                        onError(e.message ?: "Błąd podczas zapisywania")
                    }
                )
            } else {
                val freq = frequencyDays.toIntOrNull()
                if (freq == null || freq <= 0) {
                    isLoading = false
                    onError("Podaj poprawną częstotliwość w dniach")
                    return@launch
                }
                val nextDateMillis = nextPaymentDate!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val recurringTemplate = RecurringRevenue(
                    title = title,
                    amount = parsedAmount,
                    category = category,
                    frequencyDays = freq,
                    nextPaymentDateInMillis = nextDateMillis,
                    familyId = currentFamilyId,
                    isActive = true
                )

                val initialRevenueInstance = Revenue(
                    title = title,
                    amount = parsedAmount,
                    type = "Stały",
                    category = category,
                    dateInMillis = revenueDateMillis,
                    familyId = currentFamilyId
                )

                revenueRepository.addRecurringRevenueWithInitialInstance(
                    recurring = recurringTemplate,
                    initialRevenue = initialRevenueInstance,
                    onSuccess = {
                        isLoading = false
                        title = ""
                        amount = ""
                        type = "Jednorazowy"
                        category = "Wynagrodzenie"
                        onSuccess()
                    },
                    onFailure = { e ->
                        isLoading = false
                        onError(e.message ?: "Błąd podczas zapisywania wpływu stałego")
                    }
                )
            }
        }
    }

    class Factory(private val revenueRepository: RevenueRepository, private val userRepository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddRevenueViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AddRevenueViewModel(revenueRepository, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}
