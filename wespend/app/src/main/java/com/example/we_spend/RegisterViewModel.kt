package com.example.we_spend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth

class RegisterViewModel(private val auth: FirebaseAuth, private val userRepository: UserRepository) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var dateOfBirth by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun updateName(input: String) { name = input }
    fun updateEmail(input: String) { email = input }
    fun updatePassword(input: String) { password = input }
    fun updateDateOfBirth(input: String) { dateOfBirth = input }

    fun registerUser(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (email.isBlank() || password.isBlank() || name.isBlank() || dateOfBirth.isBlank()) {
            onError("Wypełnij wszystkie wymagane pola")
            return
        }

        isLoading = true

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    userRepository.saveUserToDatabase(
                        name = name,
                        email = email,
                        dateOfBirth = dateOfBirth,
                        onSuccess = {
                            isLoading = false
                            onSuccess()
                        },
                        onFailure = { e ->
                            isLoading = false
                            onError(e.message ?: "Błąd zapisu danych profilowych")
                        }
                    )
                } else {
                    isLoading = false
                    onError(task.exception?.localizedMessage ?: "Błąd rejestracji")
                }
            }
    }

    class Factory(
        private val auth: FirebaseAuth,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RegisterViewModel(auth, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}