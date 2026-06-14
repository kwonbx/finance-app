package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.*

class AnalyticsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val revenueRepository: RevenueRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    var isLoading by mutableStateOf(false)
    var expenses by mutableStateOf<List<Expense>>(emptyList())
    var revenues by mutableStateOf<List<Revenue>>(emptyList())

    fun loadData() {
        viewModelScope.launch {
            isLoading = true
            val user = userRepository.getUserProfile()
            val familyId = user?.familyId

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfMonth = calendar.timeInMillis

            val userExpenses = expenseRepository.getExpensesFrom(startOfMonth)
            val familyExpenses = if (!familyId.isNullOrEmpty()) {
                expenseRepository.getFamilyExpensesFrom(familyId, startOfMonth)
            } else {
                emptyList()
            }
            expenses = (userExpenses + familyExpenses).distinctBy { it.id }

            val userRevenues = revenueRepository.getRevenuesFrom(startOfMonth)
            val familyRevenues = if (!familyId.isNullOrEmpty()) {
                revenueRepository.getFamilyRevenuesFrom(familyId, startOfMonth)
            } else {
                emptyList()
            }
            revenues = (userRevenues + familyRevenues).distinctBy { it.id }

            isLoading = false
        }
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val revenueRepository: RevenueRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyticsViewModel(expenseRepository, revenueRepository, userRepository) as T
        }
    }
}
