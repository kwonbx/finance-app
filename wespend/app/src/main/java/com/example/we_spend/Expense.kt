package com.example.we_spend

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val category: String = "",
    val shopName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val dateInMillis: Long = 0L,
    val userId: String = "",
    val familyId: String? = null,
    val recurringExpenseId: String? = null
)