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

class RevenuesViewModel(
    private val revenueRepository: RevenueRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    private var allFetchedRevenues by mutableStateOf<List<Revenue>>(emptyList())
    var familyMembers by mutableStateOf<List<User>>(emptyList())
        private set

    var isFamilyView by mutableStateOf(false)
        private set
    var timePeriodDays by mutableStateOf<Int?>(30)
        private set
    var selectedRevenueType by mutableStateOf("Wszystkie")
        private set
    var selectedCategories by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedUsers by mutableStateOf<Set<String>>(emptySet())
        private set
    var userName by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf("")
        private set

    var searchQuery by mutableStateOf("")
        private set

    private var currentUserId: String = ""
    private var currentUserFamilyId: String? = null

    val availableCategories = listOf("Wynagrodzenie", "Premia", "Prezent", "Inwestycje", "Sprzedaż", "Inne")

    fun loadData() {
        viewModelScope.launch {
            isLoading = true

            revenueRepository.processRecurringRevenues()

            allFetchedRevenues = emptyList()
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

            fetchRevenuesFromDatabase()
        }
    }

    fun fetchRevenuesFromDatabase() {
        viewModelScope.launch {
            isLoading = true
            val fromDateMillis = timePeriodDays?.let { days ->
                LocalDate.now().minusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } ?: 0L

            val revenues = if (isFamilyView && currentUserFamilyId != null) {
                revenueRepository.getFamilyRevenuesFrom(currentUserFamilyId!!, fromDateMillis)
            } else {
                revenueRepository.getRevenuesFrom(fromDateMillis)
            }

            allFetchedRevenues = revenues.sortedByDescending { it.dateInMillis }
            isLoading = false
        }
    }

    val filteredRevenues by derivedStateOf {
        allFetchedRevenues.filter { revenue ->
            val matchesType = when (selectedRevenueType) {
                "Jednorazowy" -> revenue.type == "Jednorazowy"
                "Stały" -> revenue.type == "Stały"
                else -> true
            }
            val matchesCategory = selectedCategories.isEmpty() || selectedCategories.contains(revenue.category)
            val matchesUser = !isFamilyView || selectedUsers.isEmpty() || selectedUsers.contains(revenue.userId)

            val matchesSearch = revenue.title.contains(searchQuery, ignoreCase = true)

            matchesType && matchesCategory && matchesUser && matchesSearch
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun toggleFamilyView(showFamily: Boolean) {
        if (isFamilyView != showFamily) {
            isFamilyView = showFamily
            selectedUsers = emptySet()
            fetchRevenuesFromDatabase()
        }
    }

    fun setTimePeriod(days: Int?) {
        if (timePeriodDays != days) {
            timePeriodDays = days
            fetchRevenuesFromDatabase()
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

    fun setRevenueType(type: String) {
        selectedRevenueType = type
    }

    fun deleteRevenue(revenue: Revenue, onSuccess: () -> Unit, onError: (String) -> Unit) {
        isLoading = true
        revenueRepository.deleteRevenue(
            revenue = revenue,
            onSuccess = {
                fetchRevenuesFromDatabase()
                onSuccess()
            },
            onFailure = {
                isLoading = false
                onError(it.message ?: "Błąd podczas usuwania przychodu")
            }
        )
    }

    class Factory(
        private val revenueRepository: RevenueRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RevenuesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RevenuesViewModel(revenueRepository, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}
