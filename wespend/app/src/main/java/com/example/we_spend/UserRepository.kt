package com.example.we_spend

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

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

    fun sendInvitation(targetEmail: String, familyId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUserId = currentUser?.uid ?: return
        val currentUserEmail = currentUser.email

        if (currentUserEmail != null && targetEmail.equals(currentUserEmail, ignoreCase = true)) {
            onError("Nie możesz wysłać zaproszenia do samego siebie.")
            return
        }

        db.collection("users")
            .whereEqualTo("email", targetEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    onError("Nie znaleziono użytkownika o adresie $targetEmail.")
                    return@addOnSuccessListener
                }

                val targetUserDoc = documents.documents.first()

                val targetUserFamilyId = targetUserDoc.getString("familyId")
                if (!targetUserFamilyId.isNullOrEmpty()) {
                    onError("Ten użytkownik należy już do innej rodziny.")
                    return@addOnSuccessListener
                }

                db.collection("invitations")
                    .whereEqualTo("toEmail", targetEmail)
                    .whereEqualTo("familyId", familyId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener { inviteDocs ->

                        if (!inviteDocs.isEmpty) {
                            onError("Ten użytkownik ma już oczekujące zaproszenie do Twojej grupy.")
                            return@addOnSuccessListener
                        }

                        val newInviteRef = db.collection("invitations").document()

                        val invitation = Invitation(
                            id = newInviteRef.id,
                            fromUserId = currentUserId,
                            toEmail = targetEmail,
                            familyId = familyId,
                            status = "pending",
                            timestamp = System.currentTimeMillis()
                        )

                        newInviteRef.set(invitation)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { e -> onError(e.message ?: "Błąd wysyłania zaproszenia") }
                    }
                    .addOnFailureListener { e ->
                        onError("Błąd weryfikacji zaproszeń: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                onError("Błąd bazy danych: ${e.message}")
            }
    }

    fun respondToInvitation(
        invitationId: String,
        familyId: String,
        accept: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val newStatus = if (accept) "accepted" else "rejected"

        db.runBatch { batch ->
            val inviteRef = db.collection("invitations").document(invitationId)
            batch.update(inviteRef, "status", newStatus)

            if (accept) {
                val userRef = db.collection("users").document(currentUserId)
                batch.update(userRef, "familyId", familyId)
            }
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError("Błąd podczas przetwarzania zaproszenia: ${e.message}")
            }
    }

    fun createFamily(onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val newFamilyId = "FAM_" + UUID.randomUUID().toString().take(8)

        val batch = firestore.batch()

        val familyRef = firestore.collection("families").document(newFamilyId)
        batch.set(familyRef, hashMapOf("ownerId" to userId))

        val userRef = firestore.collection("users").document(userId)
        batch.update(userRef, "familyId", newFamilyId)

        batch.commit()
            .addOnSuccessListener { onSuccess(newFamilyId) }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getFamilyOwner(familyId: String, onResult: (String?) -> Unit) {
        firestore.collection("families").document(familyId).get()
            .addOnSuccessListener { doc -> onResult(doc.getString("ownerId")) }
            .addOnFailureListener { onResult(null) }
    }

    fun removeFamilyMember(targetUserId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("users").document(targetUserId)
            .update("familyId", null)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun deleteFamily(familyId: String, members: List<User>, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val batch = firestore.batch()

        val familyRef = firestore.collection("families").document(familyId)
        batch.delete(familyRef)

        members.forEach { member ->
            val userRef = firestore.collection("users").document(member.uid)
            batch.update(userRef, "familyId", null)
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun leaveFamily(familyId: String, members: List<User>, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("families").document(familyId).get()
            .addOnSuccessListener { doc ->
                val ownerId = doc.getString("ownerId")
                val isOwner = (ownerId == currentUserId)

                val batch = firestore.batch()

                val myUserRef = firestore.collection("users").document(currentUserId)
                batch.update(myUserRef, "familyId", null)

                if (isOwner) {
                    val remainingMembers = members.filter { it.uid != currentUserId }
                    val familyRef = firestore.collection("families").document(familyId)

                    if (remainingMembers.isNotEmpty()) {
                        val newOwnerId = remainingMembers.first().uid
                        batch.update(familyRef, "ownerId", newOwnerId)
                    } else {
                        batch.delete(familyRef)
                    }
                }

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getFamilyMembers(familyId: String, onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("users")
            .whereEqualTo("familyId", familyId)
            .get()
            .addOnSuccessListener { snapshot ->
                val members = snapshot.toObjects(User::class.java)
                onSuccess(members)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }
}