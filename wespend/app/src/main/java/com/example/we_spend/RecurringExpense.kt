package com.example.we_spend

import com.google.firebase.firestore.PropertyName

data class RecurringExpense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val shopName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val frequencyDays: Int = 0,
    val nextPaymentDateInMillis: Long = 0L,
    val userId: String = "",
    val familyId: String? = null,
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true
)