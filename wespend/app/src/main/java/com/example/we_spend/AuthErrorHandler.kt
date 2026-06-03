package com.example.we_spend

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun getPolishAuthErrorMessage(exception: Exception?): String {
    return when (exception) {
        is FirebaseAuthWeakPasswordException -> "Hasło jest za słabe. Musi mieć minimum 6 znaków."
        is FirebaseAuthUserCollisionException -> "Konto z tym adresem e-mail już istnieje."
        is FirebaseAuthInvalidCredentialsException -> "Nieprawidłowy e-mail lub hasło."
        is FirebaseAuthInvalidUserException -> "Nie znaleziono konta dla tego e-maila."
        is FirebaseAuthException -> "Wystąpił błąd: ${exception.errorCode}"
        else -> "Coś poszło nie tak. Spróbuj ponownie."
    }
}