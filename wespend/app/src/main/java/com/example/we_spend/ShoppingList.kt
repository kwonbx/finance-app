package com.example.we_spend

import com.google.firebase.firestore.PropertyName

data class ShoppingList(
    val id: String = "",
    val name: String = "",
    val shopName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = "",
    val familyId: String? = null,
    @get:PropertyName("isArchived")
    @set:PropertyName("isArchived")
    var isArchived: Boolean = false
)
