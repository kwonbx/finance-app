package com.example.we_spend

data class SavingGoal(
    val id: String = "",
    val name: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val deadlineInMillis: Long? = null,
    val userId: String = "",
    val familyId: String? = null,
    val steps: List<SavingStep> = emptyList()
)

data class SavingStep(
    val id: String = "",
    val name: String = "",
    val targetAmount: Double = 0.0
)
