package com.example.we_spend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ShoppingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getBaseRef(familyId: String?) = if (!familyId.isNullOrBlank()) {
        db.collection("families").document(familyId)
    } else {
        val userId = auth.currentUser?.uid ?: throw Exception("Brak użytkownika")
        db.collection("users").document(userId)
    }

    // LISTS
    suspend fun getLists(familyId: String?): List<ShoppingList> {
        return try {
            val snapshot = getBaseRef(familyId).collection("shopping_lists").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
            }.filter { !it.isArchived }
             .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addList(list: ShoppingList) {
        val currentUser = auth.currentUser ?: throw Exception("Brak zalogowanego użytkownika")
        val userId = currentUser.uid
        
        val baseRef = if (!list.familyId.isNullOrBlank()) {
            db.collection("families").document(list.familyId)
        } else {
            db.collection("users").document(userId)
        }
        
        val ref = baseRef.collection("shopping_lists").document()
        // We don't need to copy the id into the body if we use doc.id on read, 
        // but keeping it for consistency.
        val listWithId = list.copy(id = ref.id, userId = userId)
        
        ref.set(listWithId).await()
    }

    suspend fun updateList(list: ShoppingList) {
        if (list.id.isBlank()) return
        getBaseRef(list.familyId).collection("shopping_lists").document(list.id)
            .set(list).await()
    }

    suspend fun deleteList(list: ShoppingList) {
        if (list.id.isBlank()) return
        getBaseRef(list.familyId).collection("shopping_lists").document(list.id)
            .delete().await()
    }

    // ITEMS
    suspend fun getItems(listId: String, familyId: String?): List<ShoppingItem> {
        if (listId.isBlank()) return emptyList()
        return try {
            val snapshot = getBaseRef(familyId).collection("shopping_lists").document(listId)
                .collection("items").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addItem(item: ShoppingItem) {
        val userId = auth.currentUser?.uid ?: throw Exception("Brak użytkownika")
        if (item.listId.isBlank()) throw Exception("Brak identyfikatora listy")
        
        val ref = getBaseRef(item.familyId).collection("shopping_lists").document(item.listId)
            .collection("items").document()
        val itemWithId = item.copy(id = ref.id, userId = userId)
        ref.set(itemWithId).await()
    }

    suspend fun updateItem(item: ShoppingItem) {
        if (item.id.isBlank() || item.listId.isBlank()) return
        getBaseRef(item.familyId).collection("shopping_lists").document(item.listId)
            .collection("items").document(item.id)
            .set(item).await()
    }

    suspend fun deleteItem(item: ShoppingItem) {
        if (item.id.isBlank() || item.listId.isBlank()) return
        getBaseRef(item.familyId).collection("shopping_lists").document(item.listId)
            .collection("items").document(item.id)
            .delete().await()
    }

    suspend fun getItemPriceHistory(itemName: String, familyId: String?): List<ShoppingItem> {
        if (itemName.isBlank()) return emptyList()
        return try {
            val snapshot = db.collectionGroup("items")
                .whereEqualTo("name", itemName)
                .get().await()
            
            val allItems = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ShoppingItem::class.java)?.copy(id = doc.id)
            }

            if (!familyId.isNullOrBlank()) {
                allItems.filter { it.familyId == familyId }
            } else {
                val userId = auth.currentUser?.uid
                allItems.filter { it.userId == userId && it.familyId.isNullOrBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
