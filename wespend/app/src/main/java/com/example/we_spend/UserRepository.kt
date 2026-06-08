package com.example.we_spend

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            val snapshot = db.collection("users").document(userId).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

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

    fun updateMonthlyLimit(newLimit: Double, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .update("monthlyLimit", newLimit)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun updateUserName(newName: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .update("name", newName)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun uploadAvatar(base64String: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure(Exception("Brak zalogowanego użytkownika"))
            return
        }

        firestore.collection("users").document(userId)
            .update("avatarUrl", base64String)
            .addOnSuccessListener {
                onSuccess(base64String)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }
}