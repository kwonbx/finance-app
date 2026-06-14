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

    var selectedCalendar by mutableStateOf(Calendar.getInstance())
    var earliestDateMillis by mutableStateOf<Long?>(null)

    fun loadData(calendar: Calendar = selectedCalendar) {
        selectedCalendar = calendar
        viewModelScope.launch {
            isLoading = true
            val user = userRepository.getUserProfile()
            val familyId = user?.familyId

            if (earliestDateMillis == null) {
                val expenseEarliest = expenseRepository.getEarliestExpenseDate(familyId)
                val revenueEarliest = revenueRepository.getEarliestRevenueDate(familyId)
                earliestDateMillis = listOfNotNull(expenseEarliest, revenueEarliest).minOrNull()
            }

            val cal = calendar.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfMonth = cal.timeInMillis

            cal.add(Calendar.MONTH, 1)
            val endOfMonth = cal.timeInMillis

            val userExpenses = expenseRepository.getExpensesBetween(startOfMonth, endOfMonth)
            val familyExpenses = if (!familyId.isNullOrEmpty()) {
                expenseRepository.getFamilyExpensesBetween(familyId, startOfMonth, endOfMonth)
            } else {
                emptyList<Expense>()
            }
            expenses = (userExpenses + familyExpenses).distinctBy { it.id }

            val userRevenues = revenueRepository.getRevenuesBetween(startOfMonth, endOfMonth)
            val familyRevenues = if (!familyId.isNullOrEmpty()) {
                revenueRepository.getFamilyRevenuesBetween(familyId, startOfMonth, endOfMonth)
            } else {
                emptyList<Revenue>()
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
