package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class EditRevenueViewModel(private val revenueRepository: RevenueRepository) : ViewModel() {
    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var selectedCategory by mutableStateOf("")
    var revenueDate by mutableStateOf(LocalDate.now())
    var isLoading by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var recurringRevenueId by mutableStateOf<String?>(null)

    private var currentRevenue: Revenue? = null

    val categories = listOf("Wynagrodzenie", "Premia", "Prezent", "Inwestycje", "Sprzedaż", "Inne")

    fun updateTitle(input: String) { title = input }
    fun updateAmount(input: String) { amount = input }
    fun updateCategory(input: String) { selectedCategory = input }

    fun loadRevenue(revenueId: String) {
        if (currentRevenue?.id == revenueId) return
        isLoading = true
        viewModelScope.launch {
            revenueRepository.getRevenue(revenueId,
                onSuccess = { revenue ->
                    revenue?.let {
                        currentRevenue = it
                        title = it.title
                        amount = String.format(Locale.US, "%.2f", it.amount)
                        selectedCategory = it.category
                        revenueDate = Instant.ofEpochMilli(it.dateInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        recurringRevenueId = it.recurringRevenueId
                    }
                    isLoading = false
                },
                onFailure = {
                    isLoading = false
                }
            )
        }
    }

    fun saveChanges(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
        if (title.isBlank() || parsedAmount == null) {
            onError("Podaj poprawny tytuł i kwotę")
            return
        }

        val revenue = currentRevenue?.copy(
            title = title,
            amount = parsedAmount,
            category = selectedCategory,
            dateInMillis = revenueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        ) ?: return

        isSaving = true
        viewModelScope.launch {
            revenueRepository.updateRevenue(revenue,
                onSuccess = {
                    isSaving = false
                    onSuccess()
                },
                onFailure = {
                    isSaving = false
                    onError(it.message ?: "Błąd zapisu")
                }
            )
        }
    }

    class Factory(private val revenueRepository: RevenueRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditRevenueViewModel(revenueRepository) as T
        }
    }
}
