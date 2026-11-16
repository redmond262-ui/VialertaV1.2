package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class RoadAlert(
    @DocumentId val id: String = "",
    val type: String = "pozo", // pozo, ruta_detonada, neblina, corte_obra, animales, vehiculo_averiado
    val depthCm: Int = 0,
    val roadName: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Float = -1f,
    val directionDesc: String = "",
    val affectsBothDirections: Boolean = false,
    val estimatedKm: Int = 0,
    val reporterUid: String = "",
    val reporterName: String = "",
    val confirmations: Int = 1,
    val notThereVotes: Int = 0,
    val status: String = "active", // active, resolved
    val roadCategory: String = "ruta", // ruta, rural, interurbano, urbano
    val connectsTowns: String = "", // ej: "Une Navarro con Las Heras"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SosRequest(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerDni: String = "",
    val dniVerified: Boolean = true,
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val licensePlate: String = "",
    val assistanceType: String = "pinchadura", // pinchadura, bateria, tren_delantero, combustible, temperatura, otro
    val routeDescription: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phone: String = "",
    val status: String = "active", // active, resolved
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HitchhikeRequest(
    @DocumentId val id: String = "",
    val userUid: String = "",
    val userName: String = "",
    val userDni: String = "",
    val roleCategory: String = "docente", // docente, estudiante, salud, policia, trabajador, general
    val pickupPoint: String = "",
    val destination: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val pairedPlate: String = "",
    val status: String = "waiting", // waiting, picked_up
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val dni: String = "",
    val isDniVerified: Boolean = false,
    val phone: String = "",
    val defaultVehicle: String = "",
    val defaultPlate: String = "",
    val trustRole: String = "general",
    val isProUser: Boolean = false,
    val proExpiresAtMillis: Long = 0L,
    val isBetaTester: Boolean = false,
    val xpTotal: Int = 0,
    val pointsRuta: Int = 0,
    val dailyPointsEarned: Int = 0,
    val lastPointsDateString: String = "",
    val referralCode: String = "",
    val referredByUid: String = "",
    val referredByCode: String = "",
    val referralCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object FeatureMode {
    const val HIDDEN = "hidden"
    const val BETA = "beta"
    const val PUBLIC = "public"
}

@IgnoreExtraProperties
data class AppFeatureFlags(
    @DocumentId val id: String = "features",
    val aventonMode: String = FeatureMode.BETA,
    val baulVialMode: String = FeatureMode.BETA,
    val reclamosMode: String = FeatureMode.PUBLIC,
    val proConversionMode: String = FeatureMode.BETA,
    val referralsMode: String = FeatureMode.PUBLIC,
    val preTripChecklistMode: String = FeatureMode.PUBLIC,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralRecord(
    @DocumentId val id: String = "",
    val refereeUid: String = "",
    val refereeName: String = "",
    val referrerUid: String = "",
    val code: String = "",
    val status: String = "pending", // pending | claimed
    val rewardGrantedToReferee: Boolean = false,
    val rewardGrantedToReferrer: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppBillingConfig(
    @DocumentId val id: String = "billing",
    val mercadoPagoAlias: String = "vialerta.oficial.mp",
    val proPriceArs: Int = 5000,
    val paymentLinkUrl: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OnlineDriver(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val routeName: String = "",
    val speedKmH: Float = 0f,
    val isDriving: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestampMillis: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BaulVialItem(
    @DocumentId val id: String = "",
    val type: String = "encontrado", // encontrado | perdido
    val category: String = "patente", // patente, documento, billetera, celular, llaves, equipaje, mascota, otro
    val title: String = "",
    val publicDescription: String = "",
    val roadName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val publisherUid: String = "",
    val publisherName: String = "",
    val verificationQuestion: String = "",
    val safeDropoffPlace: String = "",
    val claimantUid: String = "",
    val claimantName: String = "",
    val claimantAnswer: String = "",
    val claimantContact: String = "",
    val publisherContact: String = "",
    val claimStatus: String = "sin_reclamo", // sin_reclamo | pendiente | aprobado | rechazado
    val status: String = "activo", // activo | devuelto
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
