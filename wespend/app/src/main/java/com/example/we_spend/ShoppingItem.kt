package com.example.we_spend

import com.google.firebase.firestore.PropertyName

data class ShoppingItem(
    val id: String = "",
    val listId: String = "",
    val name: String = "",
    @get:PropertyName("isChecked")
    @set:PropertyName("isChecked")
    @field:PropertyName("isChecked")
    var isChecked: Boolean = false,
    val quantity: String = "",
    val count: Int = 1,
    val price: Double = 0.0,
    val category: String = "Inne",
    val userId: String = "",
    val familyId: String? = null
)
