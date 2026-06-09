package com.example.we_spend

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val category: String = "",
    val dateInMillis: Long = 0L,
    val frequencyDays: Int? = null,
    val nextPaymentDateInMillis: Long? = null,
    val userId: String = "",
    val familyId: String? = null
)