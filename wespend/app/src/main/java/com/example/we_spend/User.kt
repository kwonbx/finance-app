package com.example.we_spend

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val monthlyLimit: Double = 0.0,
    val avatarUrl: String = "",
    val familyId: String? = null,
    val theme: String = "system"
)