package com.example.we_spend

data class Revenue(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val category: String = "",
    val dateInMillis: Long = 0L,
    val userId: String = "",
    val familyId: String? = null,
    val recurringRevenueId: String? = null
)
