package com.example.we_spend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId

class ExpenseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    companion object {
        private var isProcessingExpenses = false
    }

    fun addExpense(expense: Expense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        val documentRef = db.collection("users").document(userId).collection("expenses").document()
        val expenseWithId = expense.copy(id = documentRef.id, userId = userId)

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

    suspend fun getExpensesBetween(startMillis: Long, endMillis: Long): List<Expense> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("expenses")
                .whereGreaterThanOrEqualTo("dateInMillis", startMillis)
                .whereLessThan("dateInMillis", endMillis)
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

    suspend fun getFamilyExpensesBetween(familyId: String, startMillis: Long, endMillis: Long): List<Expense> {
        return try {
            val snapshot = db.collectionGroup("expenses")
                .whereEqualTo("familyId", familyId)
                .whereGreaterThanOrEqualTo("dateInMillis", startMillis)
                .whereLessThan("dateInMillis", endMillis)
                .orderBy("dateInMillis", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.toObjects(Expense::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getEarliestExpenseDate(familyId: String? = null): Long? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            val userEarliest = db.collection("users")
                .document(userId)
                .collection("expenses")
                .orderBy("dateInMillis", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .await()
                .documents.firstOrNull()?.getLong("dateInMillis")

            val familyEarliest = if (!familyId.isNullOrEmpty()) {
                db.collectionGroup("expenses")
                    .whereEqualTo("familyId", familyId)
                    .orderBy("dateInMillis", Query.Direction.ASCENDING)
                    .limit(1)
                    .get()
                    .await()
                    .documents.firstOrNull()?.getLong("dateInMillis")
            } else null

            listOfNotNull(userEarliest, familyEarliest).minOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun deleteExpense(expense: Expense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("users").document(expense.userId)
            .collection("expenses").document(expense.id)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getExpense(expenseId: String, onSuccess: (Expense?) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("expenses").document(expenseId)
            .get()
            .addOnSuccessListener { doc -> onSuccess(doc.toObject(Expense::class.java)) }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateExpense(expense: Expense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("expenses").document(expense.id)
            .set(expense)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun addRecurringExpense(recurring: RecurringExpense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak zalogowanego użytkownika"))
        val documentRef = db.collection("users").document(userId).collection("recurring_expenses").document()
        val recurringWithId = recurring.copy(id = documentRef.id, userId = userId)

        documentRef.set(recurringWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }
    suspend fun processRecurringExpenses() {
        if (isProcessingExpenses) return
        isProcessingExpenses = true

        try {
            val currentUserId = auth.currentUser?.uid ?: return
            val nowMillis = System.currentTimeMillis()

            val snapshot = db.collection("users").document(currentUserId)
                .collection("recurring_expenses")
                .whereEqualTo("isActive", true)
                .whereLessThanOrEqualTo("nextPaymentDateInMillis", nowMillis)
                .get()
                .await()

            if (snapshot.isEmpty) return

            val batch = db.batch()

            for (document in snapshot.documents) {
                val recurringTemplate = document.toObject(RecurringExpense::class.java) ?: continue
                var nextDateMillis = recurringTemplate.nextPaymentDateInMillis
                val freqDays = recurringTemplate.frequencyDays

                if (freqDays <= 0) continue

                var instancesToCreate = 0
                var currentDate = Instant.ofEpochMilli(nextDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

                while (nextDateMillis <= nowMillis) {
                    val newExpenseRef = db.collection("users").document(currentUserId)
                        .collection("expenses").document()

                    val newExpenseInstance = Expense(
                        id = newExpenseRef.id,
                        title = recurringTemplate.title,
                        amount = recurringTemplate.amount,
                        type = "Stały",
                        category = recurringTemplate.category,
                        dateInMillis = nextDateMillis,
                        userId = recurringTemplate.userId,
                        familyId = recurringTemplate.familyId,
                        recurringExpenseId = recurringTemplate.id
                    )

                    batch.set(newExpenseRef, newExpenseInstance)

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
        } finally {
            isProcessingExpenses = false
        }
    }

    fun addRecurringExpenseWithInitialInstance(
        recurring: RecurringExpense,
        initialExpense: Expense,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        val batch = db.batch()

        val recurringRef = db.collection("users").document(userId).collection("recurring_expenses").document()
        val expenseRef = db.collection("users").document(userId).collection("expenses").document()

        val finalRecurring = recurring.copy(id = recurringRef.id, userId = userId)
        val finalExpense = initialExpense.copy(
            id = expenseRef.id,
            userId = userId,
            recurringExpenseId = recurringRef.id
        )

        batch.set(recurringRef, finalRecurring)
        batch.set(expenseRef, finalExpense)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getRecurringExpense(recurringId: String, onSuccess: (RecurringExpense?) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("recurring_expenses").document(recurringId)
            .get()
            .addOnSuccessListener { doc -> onSuccess(doc.toObject(RecurringExpense::class.java)) }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateRecurringExpense(recurringId: String, newTitle: String, newCategory: String, newAmount: Double, newFrequency: Int, newNextDateMillis: Long, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("recurring_expenses").document(recurringId)
            .update(mapOf(
                "title" to newTitle,
                "category" to newCategory,
                "amount" to newAmount,
                "frequencyDays" to newFrequency,
                "nextPaymentDateInMillis" to newNextDateMillis
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deactivateRecurringExpense(recurringId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onFailure(Exception("Brak użytkownika"))
        db.collection("users").document(userId)
            .collection("recurring_expenses").document(recurringId)
            .update("isActive", false)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}