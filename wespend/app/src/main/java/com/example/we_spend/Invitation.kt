package com.example.we_spend

data class Invitation(
    val id: String = "",
    val fromUserId: String = "",
    val toEmail: String = "",
    val familyId: String = "",
    val status: String = "pending",
    val timestamp: Long = 0L
)