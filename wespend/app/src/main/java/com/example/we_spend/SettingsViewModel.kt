package com.example.we_spend

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

class SettingsViewModel(private val auth: FirebaseAuth, private val userRepository: UserRepository) : ViewModel() {
    var userName by mutableStateOf("")
        private set
    var monthlyLimit by mutableStateOf("")
        private set
    var newPassword by mutableStateOf("")
        private set
    var currentPassword by mutableStateOf("")
        private set
    var isNameLoading by mutableStateOf(false)
        private set
    var isLimitLoading by mutableStateOf(false)
        private set
    var isPasswordLoading by mutableStateOf(false)
        private set
    var avatarUrl by mutableStateOf("")
        private set
    var isAvatarLoading by mutableStateOf(false)
        private set

    fun updateUserNameInput(input: String) { userName = input }
    fun updateMonthlyLimitInput(input: String) { monthlyLimit = input }
    fun updateNewPasswordInput(input: String) { newPassword = input }
    fun updateCurrentPasswordInput(input: String) { currentPassword = input }

    fun loadUserData() {
        viewModelScope.launch {
            val user = userRepository.getUserProfile()
            user?.let {
                avatarUrl = it.avatarUrl
            }
        }
    }

    fun saveUserName(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (userName.isBlank()) {
            onError("Nazwa nie może być pusta")
            return
        }
        isNameLoading = true
        userRepository.updateUserName(
            newName = userName,
            onSuccess = {
                isNameLoading = false
                userName = ""
                onSuccess()
            },
            onFailure = {
                isNameLoading = false
                onError(it.message ?: "Błąd podczas zmiany nazwy")
            }
        )
    }

    fun saveMonthlyLimit(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val parsedLimit = monthlyLimit.replace(",", ".").toDoubleOrNull()
        if (parsedLimit == null || parsedLimit < 0) {
            onError("Podaj poprawny limit")
            return
        }
        isLimitLoading = true
        userRepository.updateMonthlyLimit(
            newLimit = parsedLimit,
            onSuccess = {
                isLimitLoading = false
                monthlyLimit = ""
                onSuccess()
            },
            onFailure = {
                isLimitLoading = false
                onError(it.message ?: "Błąd podczas zmiany limitu")
            }
        )
    }

    fun saveNewPassword(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser
        val email = user?.email

        if (user == null || email == null) {
            onError("Nie znaleziono zalogowanego użytkownika")
            return
        }
        if (currentPassword.isBlank()) {
            onError("Podaj stare hasło")
            return
        }
        if (newPassword.length < 6) {
            onError("Nowe hasło musi mieć co najmniej 6 znaków")
            return
        }

        isPasswordLoading = true

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
            if (reauthTask.isSuccessful) {
                user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                    isPasswordLoading = false
                    if (updateTask.isSuccessful) {
                        currentPassword = ""
                        newPassword = ""
                        onSuccess()
                    } else {
                        onError(updateTask.exception?.message ?: "Błąd podczas zmiany hasła")
                    }
                }
            } else {
                isPasswordLoading = false
                onError("Podano błędne stare hasło")
            }
        }
    }

    fun updateAvatar(contentResolver: ContentResolver, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        isAvatarLoading = true
        val base64String = uriToBase64(contentResolver, uri)

        if (base64String == null) {
            isAvatarLoading = false
            onError("Błąd podczas przetwarzania zdjęcia")
            return
        }

        userRepository.uploadAvatar(
            base64String = base64String,
            onSuccess = { newUrl ->
                avatarUrl = newUrl
                isAvatarLoading = false
                onSuccess()
            },
            onFailure = {
                isAvatarLoading = false
                onError(it.message ?: "Błąd podczas wgrywania zdjęcia")
            }
        )
    }

    private fun uriToBase64(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream?.close()

            val maxSize = 200
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val width = if (ratio > 1) maxSize else (maxSize * ratio).toInt()
            val height = if (ratio > 1) (maxSize / ratio).toInt() else maxSize
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()

            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    class Factory(
        private val auth: FirebaseAuth,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(auth, userRepository) as T
            }
            throw IllegalArgumentException("Nieznana klasa ViewModelu")
        }
    }
}