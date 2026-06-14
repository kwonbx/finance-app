package com.example.we_spend

interface LocationViewModel {
    val latitude: Double?
    val longitude: Double?
    fun updateLocation(lat: Double?, lng: Double?, addr: String?)
}