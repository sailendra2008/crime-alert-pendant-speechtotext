package com.avinash.crimealertpendant.data

data class EmergencyContact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val relationship: String = "Contact"
)
