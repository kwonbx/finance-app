package com.example.we_spend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SavingGoalRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun addGoal(goal: SavingGoal, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        
        val collectionRef = if (goal.familyId != null) {
            db.collection("families").document(goal.familyId).collection("saving_goals")
        } else {
            db.collection("users").document(userId).collection("saving_goals")
        }

        val docRef = if (goal.id.isNotBlank()) {
            collectionRef.document(goal.id)
        } else {
            collectionRef.document()
        }

        val goalWithId = goal.copy(id = docRef.id, userId = userId)
        docRef.set(goalWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateGoal(goal: SavingGoal, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val docRef = if (goal.familyId != null) {
            db.collection("families").document(goal.familyId).collection("saving_goals").document(goal.id)
        } else {
            db.collection("users").document(goal.userId).collection("saving_goals").document(goal.id)
        }
        docRef.set(goal)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deleteGoal(goal: SavingGoal, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val docRef = if (goal.familyId != null) {
            db.collection("families").document(goal.familyId).collection("saving_goals").document(goal.id)
        } else {
            db.collection("users").document(goal.userId).collection("saving_goals").document(goal.id)
        }
        docRef.delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    suspend fun getGoals(familyId: String?): List<SavingGoal> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val userGoals = db.collection("users").document(userId).collection("saving_goals").get().await()
            val familyGoals = if (!familyId.isNullOrBlank()) {
                db.collection("families").document(familyId).collection("saving_goals").get().await()
            } else null
            
            val allGoals = userGoals.toObjects(SavingGoal::class.java).toMutableList()
            familyGoals?.let {
                allGoals.addAll(it.toObjects(SavingGoal::class.java))
            }
            allGoals.sortedByDescending { it.id } // Basic sorting, can be improved
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addTransaction(goal: SavingGoal, transaction: SavingGoalTransaction, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        val goalRef = if (goal.familyId != null) {
            db.collection("families").document(goal.familyId).collection("saving_goals").document(goal.id)
        } else {
            db.collection("users").document(goal.userId).collection("saving_goals").document(goal.id)
        }
        
        val transRef = goalRef.collection("transactions").document()
        val transWithId = transaction.copy(id = transRef.id, goalId = goal.id, userId = userId)
        
        db.runTransaction { firestoreTrans ->
            val snapshot = firestoreTrans.get(goalRef)
            val currentAmount = snapshot.getDouble("currentAmount") ?: 0.0
            val newAmount = currentAmount + transaction.amount
            
            firestoreTrans.update(goalRef, "currentAmount", newAmount)
            firestoreTrans.set(transRef, transWithId)
        }.addOnSuccessListener { onSuccess() }
         .addOnFailureListener { onFailure(it) }
    }

    suspend fun getTransactions(goal: SavingGoal): List<SavingGoalTransaction> {
        return try {
            val goalRef = if (goal.familyId != null) {
                db.collection("families").document(goal.familyId).collection("saving_goals").document(goal.id)
            } else {
                db.collection("users").document(goal.userId).collection("saving_goals").document(goal.id)
            }
            val snapshot = goalRef.collection("transactions")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await()
            snapshot.toObjects(SavingGoalTransaction::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
