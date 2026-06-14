package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SavingGoalViewModel(
    private val savingGoalRepository: SavingGoalRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    var goals by mutableStateOf<List<SavingGoal>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var familyId by mutableStateOf<String?>(null)
        private set

    init {
        loadGoals()
    }

    fun loadGoals() {
        viewModelScope.launch {
            isLoading = true
            val user = userRepository.getUserProfile()
            familyId = user?.familyId
            goals = savingGoalRepository.getGoals(familyId)
            isLoading = false
        }
    }

    fun addGoal(name: String, targetAmount: Double, deadlineInMillis: Long?, steps: List<SavingStep> = emptyList(), isShared: Boolean = false) {
        if (name.isBlank() || targetAmount <= 0) return
        val newGoal = SavingGoal(
            name = name,
            targetAmount = targetAmount,
            deadlineInMillis = deadlineInMillis,
            familyId = if (isShared) familyId else null,
            steps = steps
        )
        savingGoalRepository.addGoal(newGoal, { loadGoals() }, { /* Handle error */ })
    }

    fun updateGoal(goal: SavingGoal, newName: String, newTarget: Double, newSteps: List<SavingStep>, isShared: Boolean = false) {
        val newFamilyId = if (isShared) (familyId ?: goal.familyId) else null
        val updatedGoal = goal.copy(
            name = newName, 
            targetAmount = newTarget, 
            steps = newSteps,
            familyId = newFamilyId
        )
        
        if (goal.familyId != newFamilyId) {
            // Location changed, must move document
            savingGoalRepository.deleteGoal(goal, {
                savingGoalRepository.addGoal(updatedGoal, { loadGoals() }, { /* Handle error */ })
            }, { /* Handle error */ })
        } else {
            savingGoalRepository.updateGoal(updatedGoal, { loadGoals() }, { /* Handle error */ })
        }
    }

    fun addTransaction(goal: SavingGoal, amount: Double, note: String) {
        if (amount == 0.0) return
        val transaction = SavingGoalTransaction(amount = amount, note = note)
        savingGoalRepository.addTransaction(goal, transaction, { 
            loadGoals()
            loadTransactions(goal)
        }, { /* Handle error */ })
    }

    var transactionsByGoal by mutableStateOf<Map<String, List<SavingGoalTransaction>>>(emptyMap())
        private set

    fun loadTransactions(goal: SavingGoal) {
        if (goal.id.isBlank()) return
        viewModelScope.launch {
            val transactions = savingGoalRepository.getTransactions(goal)
            transactionsByGoal = transactionsByGoal + (goal.id to transactions)
        }
    }

    fun deleteGoal(goal: SavingGoal) {
        savingGoalRepository.deleteGoal(goal, { loadGoals() }, { /* Handle error */ })
    }

    class Factory(
        private val savingGoalRepository: SavingGoalRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SavingGoalViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SavingGoalViewModel(savingGoalRepository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
