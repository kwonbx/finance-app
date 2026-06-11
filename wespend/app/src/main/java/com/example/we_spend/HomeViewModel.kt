package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(
    private val expenseRepository: ExpenseRepository,
    private val revenueRepository: RevenueRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    var recentExpenses by mutableStateOf<List<Expense>>(emptyList())
        private set
    var recentRevenues by mutableStateOf<List<Revenue>>(emptyList())
        private set
    var weeklyTotal by mutableStateOf(0.0)
        private set
    var monthlyTotal by mutableStateOf(0.0)
        private set
    var monthlyRevenue by mutableStateOf(0.0)
        private set
    var weeklyRevenue by mutableStateOf(0.0)
        private set
    var monthlyLimit by mutableStateOf(0.0)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var userName by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf("")
        private set
    var pendingInvitations by mutableStateOf<List<Invitation>>(emptyList())
        private set
    private var invitationListener: ListenerRegistration? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading = true

            expenseRepository.processRecurringExpenses()
            revenueRepository.processRecurringRevenues()

            val user = userRepository.getUserProfile()
            monthlyLimit = user?.monthlyLimit ?: 0.0
            userName = user?.name ?: ""
            avatarUrl = user?.avatarUrl ?: ""
            val userEmail = user?.email ?: ""

            if (userEmail.isNotEmpty()) {
                startListeningForInvitations(userEmail)
            }

            val now = LocalDate.now()
            val firstDayOfMonth = now.withDayOfMonth(1)
            val sevenDaysAgo = now.minusDays(7)

            val firstDayMillis = firstDayOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val sevenDaysAgoMillis = sevenDaysAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val currentMonthExpenses = expenseRepository.getExpensesFrom(firstDayMillis)
            val currentMonthRevenues = revenueRepository.getRevenuesFrom(firstDayMillis)

            monthlyTotal = currentMonthExpenses.sumOf { it.amount }
            weeklyTotal = currentMonthExpenses.filter { it.dateInMillis >= sevenDaysAgoMillis }.sumOf { it.amount }

            monthlyRevenue = currentMonthRevenues.sumOf { it.amount }
            weeklyRevenue = currentMonthRevenues.filter { it.dateInMillis >= sevenDaysAgoMillis }.sumOf { it.amount }

            recentExpenses = currentMonthExpenses.take(5)
            recentRevenues = currentMonthRevenues.take(5)
            isLoading = false
        }
    }

    private fun startListeningForInvitations(email: String) {
        invitationListener?.remove()

        invitationListener = listenForPendingInvitations(
            currentUserEmail = email,
            onUpdate = { invitations ->
                pendingInvitations = invitations
            },
            onError = { error ->
                println("Błąd nasłuchiwania: $error")
            }
        )
    }

    fun listenForPendingInvitations(
        currentUserEmail: String,
        onUpdate: (List<Invitation>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("invitations")
            .whereEqualTo("toEmail", currentUserEmail)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    onError("Błąd nasłuchiwania: ${exception.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val invitations = snapshot.toObjects(Invitation::class.java)
                    onUpdate(invitations)
                }
            }
    }

    fun respondToInvite(invitation: Invitation, accept: Boolean) {
        isLoading = true
        userRepository.respondToInvitation(
            invitationId = invitation.id,
            familyId = invitation.familyId,
            accept = accept,
            onSuccess = {
                if (accept) {
                    loadData()
                } else {
                    isLoading = false
                }
            },
            onError = { error ->
                isLoading = false
                println("Błąd odpowiedzi na zaproszenie: $error")
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        invitationListener?.remove()
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val revenueRepository: RevenueRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(expenseRepository, revenueRepository, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}