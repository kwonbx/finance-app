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
    var userName by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf("")
        private set

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading = true

            val user = userRepository.getUserProfile()
            monthlyLimit = user?.monthlyLimit ?: 0.0
            userName = user?.name ?: ""
            avatarUrl = user?.avatarUrl ?: ""

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