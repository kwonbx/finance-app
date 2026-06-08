package com.example.we_spend

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.edit

class LoginViewModel(private val auth: FirebaseAuth, private val sharedPrefs: SharedPreferences) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun updateEmail(input: String) { email = input }
    fun updatePassword(input: String) { password = input }

    fun loginUser(rememberMe: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onError("Wpisz e-mail i hasło")
            return
        }

        isLoading = true

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                isLoading = false

                if (task.isSuccessful) {
                    sharedPrefs.edit { putBoolean("REMEMBER_ME", rememberMe) }
                    onSuccess()
                } else {
                    onError(getPolishAuthErrorMessage(task.exception))
                }
            }
    }

    class Factory(private val auth: FirebaseAuth, private val sharedPrefs: SharedPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(auth, sharedPrefs) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}