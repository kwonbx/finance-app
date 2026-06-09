package com.example.we_spend

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class ExpensesViewModel(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    private var allFetchedExpenses by mutableStateOf<List<Expense>>(emptyList())
    var familyMembers by mutableStateOf<List<User>>(emptyList())
        private set

    var isFamilyView by mutableStateOf(false)
        private set
    var timePeriodDays by mutableStateOf<Int?>(30)
        private set
    var selectedExpenseType by mutableStateOf("Wszystkie")
        private set
    var selectedCategories by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedUsers by mutableStateOf<Set<String>>(emptySet())
        private set
    var userName by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf("")
        private set

    private var currentUserId: String = ""
    private var currentUserFamilyId: String? = null

    val availableCategories = listOf("Jedzenie", "Transport", "Rozrywka", "Zdrowie", "Rachunki", "Inne")

    fun loadData() {
        viewModelScope.launch {
            isLoading = true

            allFetchedExpenses = emptyList()
            familyMembers = emptyList()

            val user = userRepository.getUserProfile()
            currentUserId = user?.uid ?: ""
            currentUserFamilyId = user?.familyId
            userName = user?.name ?: ""
            avatarUrl = user?.avatarUrl ?: ""

            if (currentUserFamilyId != null) {
                userRepository.getFamilyMembers(
                    familyId = currentUserFamilyId!!,
                    onSuccess = { members -> familyMembers = members },
                    onFailure = {  }
                )
            } else {
                isFamilyView = false
            }

            fetchExpensesFromDatabase()
        }
    }

    fun fetchExpensesFromDatabase() {
        viewModelScope.launch {
            isLoading = true
            val fromDateMillis = timePeriodDays?.let { days ->
                LocalDate.now().minusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } ?: 0L

            val expenses = if (isFamilyView && currentUserFamilyId != null) {
                expenseRepository.getFamilyExpensesFrom(currentUserFamilyId!!, fromDateMillis)
            } else {
                expenseRepository.getExpensesFrom(fromDateMillis)
            }

            allFetchedExpenses = expenses.sortedByDescending { it.dateInMillis }
            isLoading = false
        }
    }

    val filteredExpenses by derivedStateOf {
        allFetchedExpenses.filter { expense ->
            val matchesType = when (selectedExpenseType) {
                "Jednorazowy" -> expense.type == "Jednorazowy"
                "Stały" -> expense.type == "Stały"
                else -> true
            }
            val matchesCategory = selectedCategories.isEmpty() || selectedCategories.contains(expense.category)
            val matchesUser = !isFamilyView || selectedUsers.isEmpty() || selectedUsers.contains(expense.userId)

            matchesType && matchesCategory && matchesUser
        }
    }

    fun toggleFamilyView(showFamily: Boolean) {
        if (isFamilyView != showFamily) {
            isFamilyView = showFamily
            selectedUsers = emptySet()
            fetchExpensesFromDatabase()
        }
    }

    fun setTimePeriod(days: Int?) {
        if (timePeriodDays != days) {
            timePeriodDays = days
            fetchExpensesFromDatabase()
        }
    }

    fun toggleCategory(category: String) {
        selectedCategories = if (selectedCategories.contains(category)) {
            selectedCategories - category
        } else {
            selectedCategories + category
        }
    }

    fun toggleUserFilter(userId: String) {
        selectedUsers = if (selectedUsers.contains(userId)) {
            selectedUsers - userId
        } else {
            selectedUsers + userId
        }
    }

    fun setExpenseType(type: String) {
        selectedExpenseType = type
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ExpensesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ExpensesViewModel(expenseRepository, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}