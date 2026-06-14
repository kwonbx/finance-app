package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MapViewModel(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var expenses by mutableStateOf<List<Expense>>(emptyList())
        private set

    fun loadData() {
        viewModelScope.launch {
            isLoading = true
            val user = userRepository.getUserProfile()
            val familyId = user?.familyId

            val userExpenses = expenseRepository.getExpensesFrom(0L) // Get all
            val familyExpenses = if (!familyId.isNullOrEmpty()) {
                expenseRepository.getFamilyExpensesFrom(familyId, 0L)
            } else {
                emptyList()
            }

            expenses = (userExpenses + familyExpenses)
                .filter { it.latitude != null && it.longitude != null }
                .distinctBy { it.id }
            isLoading = false
        }
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MapViewModel(expenseRepository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
