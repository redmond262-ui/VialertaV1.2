package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val type: String,
    val depthCm: Int,
    val roadName: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val heading: Float = -1f,
    val directionDesc: String = "",
    val affectsBothDirections: Boolean = false,
    val estimatedKm: Int = 0,
    val reporterUid: String,
    val reporterName: String,
    val confirmations: Int,
    val status: String,
    val roadCategory: String = "ruta",
    val connectsTowns: String = "",
    val timestampMillis: Long
)

@Entity(tableName = "cached_sos")
data class SosEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val ownerName: String,
    val ownerDni: String,
    val dniVerified: Boolean,
    val vehicleModel: String,
    val vehicleColor: String,
    val licensePlate: String,
    val assistanceType: String,
    val routeDescription: String,
    val latitude: Double,
    val longitude: Double,
    val phone: String,
    val status: String,
    val timestampMillis: Long
)
