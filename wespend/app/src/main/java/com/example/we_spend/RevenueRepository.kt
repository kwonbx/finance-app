package com.example.we_spend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId

class RevenueRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun addRevenue(revenue: Revenue, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))

        val documentRef = db.collection("users").document(userId).collection("revenues").document()

        val revenueWithId = revenue.copy(
            id = documentRef.id,
            userId = userId
        )

        documentRef.set(revenueWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    suspend fun getRevenuesFrom(startDateInMillis: Long): List<Revenue> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("revenues")
                .whereGreaterThanOrEqualTo("dateInMillis", startDateInMillis)
                .orderBy("dateInMillis", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.toObjects(Revenue::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFamilyRevenuesFrom(familyId: String, startDateInMillis: Long): List<Revenue> {
        return try {
            val snapshot = db.collectionGroup("revenues")
                .whereEqualTo("familyId", familyId)
                .whereGreaterThanOrEqualTo("dateInMillis", startDateInMillis)
                .orderBy("dateInMillis", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.toObjects(Revenue::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun deleteRevenue(revenue: Revenue, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("users").document(revenue.userId)
            .collection("revenues").document(revenue.id)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun addRecurringRevenue(recurring: RecurringRevenue, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak zalogowanego użytkownika"))

        val documentRef = db.collection("users").document(userId).collection("recurring_revenues").document()

        val recurringWithId = recurring.copy(id = documentRef.id, userId = userId)

        documentRef.set(recurringWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    suspend fun processRecurringRevenues() {
        val currentUserId = auth.currentUser?.uid ?: return
        val nowMillis = System.currentTimeMillis()

        try {
            val snapshot = db.collection("users").document(currentUserId)
                .collection("recurring_revenues")
                .whereEqualTo("isActive", true)
                .whereLessThanOrEqualTo("nextPaymentDateInMillis", nowMillis)
                .get()
                .await()

            if (snapshot.isEmpty) return

            val batch = db.batch()

            for (document in snapshot.documents) {
                val recurringTemplate = document.toObject(RecurringRevenue::class.java) ?: continue
                var nextDateMillis = recurringTemplate.nextPaymentDateInMillis
                val freqDays = recurringTemplate.frequencyDays

                if (freqDays <= 0) continue

                var instancesToCreate = 0
                var currentDate = Instant.ofEpochMilli(nextDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

                while (nextDateMillis <= nowMillis) {
                    val newRevenueRef = db.collection("users").document(currentUserId)
                        .collection("revenues").document()

                    val newRevenueInstance = Revenue(
                        id = newRevenueRef.id,
                        title = recurringTemplate.title,
                        amount = recurringTemplate.amount,
                        type = "Stały",
                        category = recurringTemplate.category,
                        dateInMillis = nextDateMillis,
                        userId = recurringTemplate.userId,
                        familyId = recurringTemplate.familyId,
                        recurringRevenueId = recurringTemplate.id
                    )

                    batch.set(newRevenueRef, newRevenueInstance)

                    currentDate = currentDate.plusDays(freqDays.toLong())
                    nextDateMillis = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    instancesToCreate++
                }

                if (instancesToCreate > 0) {
                    batch.update(document.reference, "nextPaymentDateInMillis", nextDateMillis)
                }
            }

            batch.commit().await()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addRecurringRevenueWithInitialInstance(
        recurring: RecurringRevenue,
        initialRevenue: Revenue,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        val batch = db.batch()

        val recurringRef = db.collection("users").document(userId).collection("recurring_revenues").document()
        val revenueRef = db.collection("users").document(userId).collection("revenues").document()

        val finalRecurring = recurring.copy(id = recurringRef.id, userId = userId)
        val finalRevenue = initialRevenue.copy(
            id = revenueRef.id,
            userId = userId,
            recurringRevenueId = recurringRef.id
        )

        batch.set(recurringRef, finalRecurring)
        batch.set(revenueRef, finalRevenue)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getRecurringRevenue(recurringId: String, onSuccess: (RecurringRevenue?) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("recurring_revenues").document(recurringId)
            .get()
            .addOnSuccessListener { doc -> onSuccess(doc.toObject(RecurringRevenue::class.java)) }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateRecurringRevenue(recurringId: String, newAmount: Double, newFrequency: Int, newNextDateMillis: Long, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("recurring_revenues").document(recurringId)
            .update(mapOf(
                "amount" to newAmount,
                "frequencyDays" to newFrequency,
                "nextPaymentDateInMillis" to newNextDateMillis
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deactivateRecurringRevenue(recurringId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("recurring_revenues").document(recurringId)
            .update("isActive", false)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}
