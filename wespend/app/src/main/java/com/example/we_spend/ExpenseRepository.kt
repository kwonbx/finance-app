package com.example.we_spend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ExpenseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun addExpense(expense: Expense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))

        val documentRef = db.collection("users").document(userId).collection("expenses").document()

        val expenseWithId = expense.copy(
            id = documentRef.id,
            userId = userId
        )

        documentRef.set(expenseWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    suspend fun getExpensesFrom(startDateInMillis: Long): List<Expense> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("expenses")
                .whereGreaterThanOrEqualTo("dateInMillis", startDateInMillis)
                .orderBy("dateInMillis", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.toObjects(Expense::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFamilyExpensesFrom(familyId: String, startDateInMillis: Long): List<Expense> {
        return try {
            val snapshot = db.collectionGroup("expenses")
                .whereEqualTo("familyId", familyId)
                .whereGreaterThanOrEqualTo("dateInMillis", startDateInMillis)
                .orderBy("dateInMillis", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.toObjects(Expense::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}