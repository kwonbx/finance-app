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

class HomeViewModel(private val expenseRepository: ExpenseRepository, private val userRepository: UserRepository) : ViewModel() {
    var recentExpenses by mutableStateOf<List<Expense>>(emptyList())
        private set
    var weeklyTotal by mutableStateOf(0.0)
        private set
    var monthlyTotal by mutableStateOf(0.0)
        private set
    var monthlyLimit by mutableStateOf(0.0)
        private set
    var isLoading by mutableStateOf(true)
        private set

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading = true

            val user = userRepository.getUserProfile()
            monthlyLimit = user?.monthlyLimit ?: 0.0

            val now = LocalDate.now()
            val firstDayOfMonth = now.withDayOfMonth(1)
            val sevenDaysAgo = now.minusDays(7)

            val firstDayMillis = firstDayOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val sevenDaysAgoMillis = sevenDaysAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val currentMonthExpenses = expenseRepository.getExpensesFrom(firstDayMillis)

            monthlyTotal = currentMonthExpenses.sumOf { it.amount }
            weeklyTotal = currentMonthExpenses.filter { it.dateInMillis >= sevenDaysAgoMillis }.sumOf { it.amount }
            recentExpenses = currentMonthExpenses.take(5)
            isLoading = false
        }
    }

    fun addTestData() {
        val dummyData = listOf(
            Expense(title = "Biedronka - zakupy domowe", amount = 145.50, type = "Jednorazowy", category = "Jedzenie", dateInMillis = System.currentTimeMillis(), shopName = "Biedronka"),
            Expense(title = "Netflix", amount = 60.0, type = "Stały", category = "Rozrywka", dateInMillis = System.currentTimeMillis() - 86400000, shopName = "Netflix"), // Wczoraj
            Expense(title = "Bilet ZTM", amount = 110.0, type = "Stały", category = "Transport", dateInMillis = System.currentTimeMillis() - (86400000 * 3), shopName = "ZTM"), // 3 dni temu
            Expense(title = "Kawa na uczelni", amount = 15.0, type = "Jednorazowy", category = "Jedzenie", dateInMillis = System.currentTimeMillis() - (86400000 * 5), shopName = "Kawiarnia"), // 5 dni temu
            Expense(title = "Kino z chłopakiem", amount = 80.0, type = "Jednorazowy", category = "Rozrywka", dateInMillis = System.currentTimeMillis() - (86400000 * 10), shopName = "Multikino") // 10 dni temu
        )

        isLoading = true
        viewModelScope.launch {
            dummyData.forEach { expense ->
                expenseRepository.addExpense(expense, onSuccess = {}, onFailure = {})
            }
            loadData()
        }
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(expenseRepository, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}