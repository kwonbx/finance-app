package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FamilyViewModel(private val userRepository: UserRepository) : ViewModel() {
    var familyId by mutableStateOf<String?>(null)
        private set
    var inviteEmail by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(true)
        private set
    var familyMembers by mutableStateOf<List<User>>(emptyList())
        private set

    var isOwner by mutableStateOf(false)
        private set
    val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var ownerId by mutableStateOf<String?>(null)
        private set

    init {
        loadUserFamily()
    }

    fun loadUserFamily() {
        viewModelScope.launch {
            isLoading = true
            val user = userRepository.getUserProfile()
            familyId = user?.familyId

            if (familyId != null) {
                userRepository.getFamilyMembers(
                    familyId = familyId!!,
                    onSuccess = { members ->
                        familyMembers = members

                        userRepository.getFamilyOwner(familyId!!) { fetchedOwnerId ->
                            ownerId = fetchedOwnerId
                            isOwner = (fetchedOwnerId == currentUserId)
                            isLoading = false
                        }
                    },
                    onFailure = { isLoading = false }
                )
            } else {
                ownerId = null
                isOwner = false
                isLoading = false
            }
        }
    }

    fun removeMember(userId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        isLoading = true
        userRepository.removeFamilyMember(
            targetUserId = userId,
            onSuccess = {
                loadUserFamily()
                onSuccess()
            },
            onFailure = {
                isLoading = false
                onError(it.message ?: "Błąd podczas usuwania członka")
            }
        )
    }

    fun deleteEntireFamily(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamily = familyId ?: return
        isLoading = true
        userRepository.deleteFamily(
            familyId = currentFamily,
            members = familyMembers,
            onSuccess = {
                familyId = null
                isOwner = false
                ownerId = null
                familyMembers = emptyList()
                isLoading = false
                onSuccess()
            },
            onFailure = {
                isLoading = false
                onError(it.message ?: "Błąd podczas usuwania rodziny")
            }
        )
    }

    fun updateInviteEmail(email: String) {
        inviteEmail = email
    }

    fun createNewFamily(onSuccess: () -> Unit, onError: (String) -> Unit) {
        isLoading = true
        userRepository.createFamily(
            onSuccess = { newId ->
                loadUserFamily()
                onSuccess()
            },
            onFailure = {
                isLoading = false
                onError(it.message ?: "Błąd podczas tworzenia rodziny")
            }
        )
    }

    fun sendInvite(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamily = familyId
        if (currentFamily == null) {
            onError("Musisz najpierw utworzyć rodzinę.")
            return
        }
        if (inviteEmail.isBlank() || !inviteEmail.contains("@")) {
            onError("Podaj prawidłowy adres e-mail.")
            return
        }

        isLoading = true
        userRepository.sendInvitation(
            targetEmail = inviteEmail,
            familyId = currentFamily,
            onSuccess = {
                isLoading = false
                inviteEmail = ""
                onSuccess()
            },
            onError = { errorMsg ->
                isLoading = false
                onError(errorMsg)
            }
        )
    }

    fun leaveFamily(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamily = familyId ?: return
        isLoading = true
        userRepository.leaveFamily(
            familyId = currentFamily,
            members = familyMembers,
            onSuccess = {
                familyId = null
                isOwner = false
                familyMembers = emptyList()
                isLoading = false
                onSuccess()
            },
            onFailure = {
                isLoading = false
                onError(it.message ?: "Błąd podczas opuszczania rodziny")
            }
        )
    }

    class Factory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FamilyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FamilyViewModel(userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}