package com.example.we_spend

data class SavingGoalTransaction(
    val id: String = "",
    val goalId: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = ""
)
