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

class AddExpenseViewModel(private val expenseRepository: ExpenseRepository, private val userRepository: UserRepository) : ViewModel(), LocationViewModel {
    var title by mutableStateOf("")
        private set
    var shopName by mutableStateOf("")
        private set
    override var latitude by mutableStateOf<Double?>(null)
        private set
    override var longitude by mutableStateOf<Double?>(null)
        private set
    var address by mutableStateOf("")
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

    var shopSuggestions by mutableStateOf<List<Expense>>(emptyList())
        private set

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
        category = expense.category
        shopSuggestions = emptyList()
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
                val expenses = expenseRepository.getExpensesFrom(0)
                val match = expenses.find { 
                    it.latitude != null && it.longitude != null &&
                    kotlin.math.abs(it.latitude - lat) < 0.0001 &&
                    kotlin.math.abs(it.longitude - lng) < 0.0001
                }
                
                match?.let {
                    if (shopName.isBlank()) shopName = it.shopName
                    if (address.isBlank() || address == "Wybrane na mapie") address = it.address ?: ""
                    if (category == "Jedzenie") category = it.category
                }
            } catch (e: Exception) {
            }
        }
    }
    fun updateAddress(input: String) { address = input }
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

        isLoading = true

        viewModelScope.launch {
            val userProfile = userRepository.getUserProfile()
            val currentFamilyId = userProfile?.familyId
            val expenseDateMillis = expenseDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (type == "Jednorazowy") {
                val expense = Expense(
                    title = title,
                    amount = parsedAmount,
                    type = type,
                    category = category,
                    shopName = shopName,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    dateInMillis = expenseDateMillis,
                    familyId = currentFamilyId
                )

                expenseRepository.addExpense(
                    expense = expense,
                    onSuccess = {
                        isLoading = false
                        title = ""
                        shopName = ""
                        latitude = null
                        longitude = null
                        address = ""
                        amount = ""
                        type = "Jednorazowy"
                        category = "Jedzenie"
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

                val recurringTemplate = RecurringExpense(
                    title = title,
                    amount = parsedAmount,
                    category = category,
                    shopName = shopName,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    frequencyDays = freq,
                    nextPaymentDateInMillis = nextDateMillis,
                    familyId = currentFamilyId,
                    isActive = true
                )

                val initialExpenseInstance = Expense(
                    title = title,
                    amount = parsedAmount,
                    type = "Stały",
                    category = category,
                    shopName = shopName,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    dateInMillis = expenseDateMillis,
                    familyId = currentFamilyId
                )

                expenseRepository.addRecurringExpenseWithInitialInstance(
                    recurring = recurringTemplate,
                    initialExpense = initialExpenseInstance,
                    onSuccess = {
                        isLoading = false
                        title = ""
                        shopName = ""
                        latitude = null
                        longitude = null
                        address = ""
                        amount = ""
                        type = "Jednorazowy"
                        category = "Jedzenie"
                        onSuccess()
                    },
                    onFailure = { e ->
                        isLoading = false
                        onError(e.message ?: "Błąd podczas zapisywania opłaty stałej")
                    }
                )
            }
        }
    }
    fun onReceiptScanned(shopName: String, scannedAmount: String, scannedDate: String) {
        if (shopName.isNotBlank()) {
            this.shopName = shopName
            if (this.title.isBlank()) {
                this.title = shopName
            }
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