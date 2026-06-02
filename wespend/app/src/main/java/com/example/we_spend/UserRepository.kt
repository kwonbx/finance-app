package com.example.we_spend

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun saveUserToDatabase(name: String, email: String, dateOfBirth: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userProfile = User(
                uid = currentUser.uid,
                name = name,
                email = email,
                dateOfBirth = dateOfBirth
            )

            db.collection("users")
                .document(currentUser.uid)
                .set(userProfile)
                .addOnSuccessListener {
                    Log.d("Firestore", "Użytkownik pomyślnie zapisany!")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.w("Firestore", "Błąd podczas zapisu", e)
                    onFailure(e)
                }
        } else {
            onFailure(Exception("Brak zalogowanego użytkownika"))
        }
    }
}