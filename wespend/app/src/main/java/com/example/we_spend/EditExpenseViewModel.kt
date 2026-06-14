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

class EditExpenseViewModel(private val expenseRepository: ExpenseRepository) : ViewModel(), LocationViewModel {
    var title by mutableStateOf("")
    var shopName by mutableStateOf("")
    var amount by mutableStateOf("")
    var selectedCategory by mutableStateOf("")
    var address by mutableStateOf("")
    override var latitude by mutableStateOf<Double?>(null)
    override var longitude by mutableStateOf<Double?>(null)
    var expenseDate by mutableStateOf(LocalDate.now())
    var isLoading by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var recurringExpenseId by mutableStateOf<String?>(null)
    var shopSuggestions by mutableStateOf<List<Expense>>(emptyList())

    private var currentExpense: Expense? = null

    val categories = listOf("Jedzenie", "Transport", "Rozrywka", "Zdrowie", "Rachunki", "Inne")

    fun updateTitle(input: String) { title = input }
    fun updateShopName(input: String) { 
        shopName = input 
        if (input.length >= 2) {
            checkShopNameHistory(input)
        } else {
            shopSuggestions = emptyList()
        }
    }

    private fun checkShopNameHistory(name: String) {
        viewModelScope.launch {
            try {
                val expenses = expenseRepository.getExpensesFrom(0)
                shopSuggestions = expenses
                    .filter { it.shopName.contains(name, ignoreCase = true) }
                    .distinctBy { it.shopName + it.address }
                    .take(3)
            } catch (e: Exception) {
            }
        }
    }

    fun applySuggestion(expense: Expense) {
        shopName = expense.shopName
        address = expense.address ?: ""
        latitude = expense.latitude
        longitude = expense.longitude
        selectedCategory = expense.category
        shopSuggestions = emptyList()
    }
    fun updateAmount(input: String) { amount = input }
    fun updateCategory(input: String) { selectedCategory = input }
    fun updateAddress(input: String) { address = input }

    fun loadExpense(expenseId: String) {
        if (currentExpense?.id == expenseId) return
        isLoading = true
        viewModelScope.launch {
            // Since we don't have a direct getExpenseById in repository, we might need one or find it in history
            // For now, I'll assume we pass the ID and the repository has a way to get it
            // I will add getExpenseById to ExpenseRepository if it doesn't exist
            expenseRepository.getExpense(expenseId, 
                onSuccess = { expense ->
                    expense?.let {
                        currentExpense = it
                        title = it.title
                        shopName = it.shopName
                        amount = it.amount.toString()
                        selectedCategory = it.category
                        address = it.address ?: ""
                        latitude = it.latitude
                        longitude = it.longitude
                        expenseDate = Instant.ofEpochMilli(it.dateInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        recurringExpenseId = it.recurringExpenseId
                    }
                    isLoading = false
                },
                onFailure = {
                    isLoading = false
                }
            )
        }
    }

    override fun updateLocation(lat: Double?, lng: Double?, addr: String?) {
        latitude = lat
        longitude = lng
        if (!addr.isNullOrBlank()) {
            address = addr
        } else if (lat != null && lng != null) {
            address = "Wybrane na mapie"
        }
        
        if (lat != null && lng != null) {
            checkNearbyExpenses(lat, lng)
        }
    }

    private fun checkNearbyExpenses(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                // Search in history for same location
                val expenses = expenseRepository.getExpensesFrom(0)
                val match = expenses.find { 
                    it.latitude != null && it.longitude != null &&
                    kotlin.math.abs(it.latitude - lat) < 0.0001 &&
                    kotlin.math.abs(it.longitude - lng) < 0.0001
                }
                
                match?.let {
                    if (shopName.isBlank()) shopName = it.shopName
                    // Only override if current address is vague
                    if (address.isBlank() || address == "Wybrane na mapie") {
                        address = it.address ?: ""
                    }
                    if (selectedCategory.isBlank() || selectedCategory == "Inne") {
                        selectedCategory = it.category
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun saveChanges(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
        if (title.isBlank() || parsedAmount == null) {
            onError("Podaj poprawny tytuł i kwotę")
            return
        }

        val expense = currentExpense?.copy(
            title = title,
            shopName = shopName,
            amount = parsedAmount,
            category = selectedCategory,
            address = address,
            latitude = latitude,
            longitude = longitude,
            dateInMillis = expenseDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        ) ?: return

        isSaving = true
        viewModelScope.launch {
            expenseRepository.updateExpense(expense,
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

    class Factory(private val expenseRepository: ExpenseRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditExpenseViewModel(expenseRepository) as T
        }
    }
}
