package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.local.AlertDao
import com.example.data.local.AlertEntity
import com.example.data.local.ViaAlertaDatabase
import com.example.data.model.RoadAlert
import com.example.data.model.RoadLevels
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

import com.example.util.DriverPreferences
import com.example.util.OfflineAlertQueue
import com.example.util.PendingOfflineAlert
import com.example.util.getSafeFirestoreInstance

class RoadAlertRepository(
    private val db: FirebaseFirestore,
    private val alertDao: AlertDao? = null,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        getSafeFirestoreInstance(context),
        try { ViaAlertaDatabase.getInstance(context).alertDao() } catch (t: Throwable) { null },
        context.applicationContext
    )

    private val auth = Firebase.auth
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val userRepo by lazy { UserRepository(db) }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeActiveAlerts(): Flow<List<RoadAlert>> = flow {
        val path = "alerts"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "active")
                .snapshots()
                .map { snapshot ->
                    val rawAlerts = snapshot.toObjects(RoadAlert::class.java)
                    val now = System.currentTimeMillis()
                    val (expired, alerts) = rawAlerts.partition { isAlertExpired(it, now) }
                    if (expired.isNotEmpty() && auth.currentUser != null) {
                        ioScope.launch {
                            expired.forEach { oldAlert ->
                                try {
                                    db.collection(path).document(oldAlert.id).update(
                                        mapOf(
                                            "status" to "resolved",
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    ).await()
                                    alertDao?.updateAlertStatus(oldAlert.id, "resolved")
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                    // Cache to Room for offline route stretches
                    alertDao?.let { dao ->
                        ioScope.launch {
                            val entities = alerts.map { alert ->
                                AlertEntity(
                                    id = alert.id,
                                    type = alert.type,
                                    depthCm = alert.depthCm,
                                    roadName = alert.roadName,
                                    description = alert.description,
                                    latitude = alert.latitude,
                                    longitude = alert.longitude,
                                    heading = alert.heading,
                                    directionDesc = alert.directionDesc,
                                    affectsBothDirections = alert.affectsBothDirections,
                                    estimatedKm = alert.estimatedKm,
                                    reporterUid = alert.reporterUid,
                                    reporterName = alert.reporterName,
                                    confirmations = alert.confirmations,
                                    status = alert.status,
                                    roadCategory = alert.roadCategory,
                                    connectsTowns = alert.connectsTowns,
                                    timestampMillis = alert.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            dao.insertAlerts(entities)
                        }
                    }
                    alerts
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    // If offline and we have local cache, emit from Room
                    if (alertDao != null) {
                        try {
                            emitAll(
                                alertDao.getActiveAlerts().map { list ->
                                    list.map { entity ->
                                        RoadAlert(
                                            id = entity.id,
                                            type = entity.type,
                                            depthCm = entity.depthCm,
                                            roadName = entity.roadName,
                                            description = entity.description,
                                            latitude = entity.latitude,
                                            longitude = entity.longitude,
                                            heading = entity.heading,
                                            directionDesc = entity.directionDesc,
                                            affectsBothDirections = entity.affectsBothDirections,
                                            estimatedKm = entity.estimatedKm,
                                            reporterUid = entity.reporterUid,
                                            reporterName = entity.reporterName,
                                            confirmations = entity.confirmations,
                                            status = entity.status,
                                            roadCategory = entity.roadCategory,
                                            connectsTowns = entity.connectsTowns
                                        )
                                    }
                                }
                            )
                        } catch (t: Throwable) {
                            emit(emptyList())
                        }
                    } else {
                        emit(emptyList())
                    }
                }
        )
    }

    suspend fun createAlert(
        type: String,
        depthCm: Int = 0,
        roadName: String = "",
        description: String = "",
        latitude: Double,
        longitude: Double,
        heading: Float = -1f,
        directionDesc: String = "",
        affectsBothDirections: Boolean = false,
        estimatedKm: Int = 0,
        reporterName: String = "",
        roadCategory: String = "ruta",
        connectsTowns: String = "",
        reporterLevel: Int = 1
    ): Result<String> {
        return try {
            val uid = requireUserId()
            if (DriverPreferences.isDuplicateNearbyReport(appContext, latitude, longitude, type)) {
                return Result.failure(IllegalStateException("Ya reportaste este peligro recién en el mismo punto."))
            }

            val now = System.currentTimeMillis()
            val expiresAtMillis = computeInitialExpiresAtMillis(type, now)
            val safeLevel = reporterLevel.coerceIn(1, 6)

            val docRef = db.collection("alerts").document()
            val payload = mutableMapOf<String, Any>(
                "type" to type,
                "depthCm" to depthCm,
                "roadName" to roadName,
                "description" to description,
                "latitude" to latitude,
                "longitude" to longitude,
                "heading" to heading,
                "directionDesc" to directionDesc,
                "affectsBothDirections" to affectsBothDirections,
                "estimatedKm" to estimatedKm,
                "reporterUid" to uid,
                "reporterName" to reporterName,
                "confirmations" to 1,
                "notThereVotes" to 0,
                "status" to "active",
                "roadCategory" to roadCategory,
                "connectsTowns" to connectsTowns,
                "expiresAtMillis" to expiresAtMillis,
                "authorBonusAwarded" to false,
                "reporterLevel" to safeLevel,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val shouldQueueOffline = !OfflineAlertQueue.isNetworkAvailable(appContext)
            if (shouldQueueOffline) {
                return queueAlertLocally(
                    type = type,
                    depthCm = depthCm,
                    roadName = roadName,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    heading = heading,
                    directionDesc = directionDesc,
                    affectsBothDirections = affectsBothDirections,
                    estimatedKm = estimatedKm,
                    reporterUid = uid,
                    reporterName = reporterName,
                    roadCategory = roadCategory,
                    connectsTowns = connectsTowns,
                    reporterLevel = safeLevel,
                    now = now
                )
            }

            try {
                withTimeout(4500L) {
                    docRef.set(payload).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (netErr: Exception) {
                val isPermissionError = (netErr as? FirebaseFirestoreException)?.code ==
                    FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (!isPermissionError && appContext != null) {
                    return queueAlertLocally(
                        type = type,
                        depthCm = depthCm,
                        roadName = roadName,
                        description = description,
                        latitude = latitude,
                        longitude = longitude,
                        heading = heading,
                        directionDesc = directionDesc,
                        affectsBothDirections = affectsBothDirections,
                        estimatedKm = estimatedKm,
                        reporterUid = uid,
                        reporterName = reporterName,
                        roadCategory = roadCategory,
                        connectsTowns = connectsTowns,
                        reporterLevel = safeLevel,
                        now = now
                    )
                }
                throw netErr
            }

            DriverPreferences.recordCreatedReport(appContext, latitude, longitude, type)

            // Also cache locally
            alertDao?.insertAlert(
                AlertEntity(
                    id = docRef.id,
                    type = type,
                    depthCm = depthCm,
                    roadName = roadName,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    heading = heading,
                    directionDesc = directionDesc,
                    affectsBothDirections = affectsBothDirections,
                    estimatedKm = estimatedKm,
                    reporterUid = uid,
                    reporterName = reporterName,
                    confirmations = 1,
                    status = "active",
                    roadCategory = roadCategory,
                    connectsTowns = connectsTowns,
                    timestampMillis = now
                )
            )

            // Gamification: Award 30 Puntos Ruta + 1 created report (respects 200 pts/day cap)
            try {
                userRepo.awardPointsRuta(uid, 30, createdDelta = 1)
            } catch (_: Throwable) {}

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "alerts")
            Result.failure(e)
        }
    }

    private suspend fun queueAlertLocally(
        type: String,
        depthCm: Int,
        roadName: String,
        description: String,
        latitude: Double,
        longitude: Double,
        heading: Float,
        directionDesc: String,
        affectsBothDirections: Boolean,
        estimatedKm: Int,
        reporterUid: String,
        reporterName: String,
        roadCategory: String,
        connectsTowns: String,
        reporterLevel: Int,
        now: Long
    ): Result<String> {
        val localId = "offline_${now}_${(1000..9999).random()}"
        OfflineAlertQueue.enqueue(
            appContext,
            PendingOfflineAlert(
                localId = localId,
                type = type,
                depthCm = depthCm,
                roadName = roadName,
                description = description,
                latitude = latitude,
                longitude = longitude,
                heading = heading,
                directionDesc = directionDesc,
                affectsBothDirections = affectsBothDirections,
                estimatedKm = estimatedKm,
                reporterName = reporterName,
                roadCategory = roadCategory,
                connectsTowns = connectsTowns,
                reporterLevel = reporterLevel,
                createdAtMillis = now
            )
        )
        DriverPreferences.recordCreatedReport(appContext, latitude, longitude, type, now)
        alertDao?.insertAlert(
            AlertEntity(
                id = localId,
                type = type,
                depthCm = depthCm,
                roadName = roadName,
                description = "$description (cola offline)".trim(),
                latitude = latitude,
                longitude = longitude,
                heading = heading,
                directionDesc = directionDesc,
                affectsBothDirections = affectsBothDirections,
                estimatedKm = estimatedKm,
                reporterUid = reporterUid,
                reporterName = reporterName,
                confirmations = 1,
                status = "active",
                roadCategory = roadCategory,
                connectsTowns = connectsTowns,
                timestampMillis = now
            )
        )
        return Result.success("$OFFLINE_QUEUED_PREFIX$localId")
    }

    suspend fun flushOfflineQueue(reporterLevel: Int = 1): Int {
        val ctx = appContext ?: return 0
        val uid = auth.currentUser?.uid ?: return 0
        if (!OfflineAlertQueue.isNetworkAvailable(ctx)) return 0
        val pending = OfflineAlertQueue.getPending(ctx)
        if (pending.isEmpty()) return 0

        var syncedCount = 0
        for (item in pending) {
            try {
                val now = System.currentTimeMillis()
                val expiresAtMillis = computeInitialExpiresAtMillis(item.type, now)
                val safeLevel = item.reporterLevel.coerceAtLeast(reporterLevel).coerceIn(1, 6)
                val docRef = db.collection("alerts").document()
                val payload = mutableMapOf<String, Any>(
                    "type" to item.type,
                    "depthCm" to item.depthCm,
                    "roadName" to item.roadName,
                    "description" to item.description,
                    "latitude" to item.latitude,
                    "longitude" to item.longitude,
                    "heading" to item.heading,
                    "directionDesc" to item.directionDesc,
                    "affectsBothDirections" to item.affectsBothDirections,
                    "estimatedKm" to item.estimatedKm,
                    "reporterUid" to uid,
                    "reporterName" to item.reporterName,
                    "confirmations" to 1,
                    "notThereVotes" to 0,
                    "status" to "active",
                    "roadCategory" to item.roadCategory,
                    "connectsTowns" to item.connectsTowns,
                    "expiresAtMillis" to expiresAtMillis,
                    "authorBonusAwarded" to false,
                    "reporterLevel" to safeLevel,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                withTimeout(5000L) {
                    docRef.set(payload).await()
                }
                OfflineAlertQueue.removeByLocalId(ctx, item.localId)
                alertDao?.deleteAlert(item.localId)
                alertDao?.insertAlert(
                    AlertEntity(
                        id = docRef.id,
                        type = item.type,
                        depthCm = item.depthCm,
                        roadName = item.roadName,
                        description = item.description,
                        latitude = item.latitude,
                        longitude = item.longitude,
                        heading = item.heading,
                        directionDesc = item.directionDesc,
                        affectsBothDirections = item.affectsBothDirections,
                        estimatedKm = item.estimatedKm,
                        reporterUid = uid,
                        reporterName = item.reporterName,
                        confirmations = 1,
                        status = "active",
                        roadCategory = item.roadCategory,
                        connectsTowns = item.connectsTowns,
                        timestampMillis = item.createdAtMillis
                    )
                )
                try {
                    userRepo.awardPointsRuta(uid, 30, createdDelta = 1)
                } catch (_: Throwable) {}
                syncedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Keep remaining queued items intact for the next retry
                break
            }
        }
        return syncedCount
    }

    suspend fun confirmAlert(
        alertId: String,
        currentConfirmations: Int,
        reporterUid: String = "",
        alertCreatedMillis: Long = 0L,
        alertType: String = ""
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        if (!DriverPreferences.canConfirmAlert(
                context = appContext,
                alertId = alertId,
                reporterUid = reporterUid,
                currentUid = currentUid,
                alertCreatedMillis = alertCreatedMillis
            )
        ) {
            return Result.failure(
                IllegalStateException("Ya confirmaste esta alerta hace menos de 4 horas (o es un reporte propio reciente).")
            )
        }

        val docRef = db.collection("alerts").document(alertId)
        return try {
            val updates = mutableMapOf<String, Any>(
                "confirmations" to currentConfirmations + 1,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isTemporaryHazardType(alertType)) {
                updates["expiresAtMillis"] = computeInitialExpiresAtMillis(alertType)
            }
            docRef.update(updates).await()

            DriverPreferences.recordAlertConfirmation(appContext, alertId)

            // Gamification: Award 15 Puntos Ruta to confirming driver (respects 200 pts/day cap)
            currentUid?.let { uid ->
                try {
                    userRepo.awardPointsRuta(uid, 15)
                } catch (_: Throwable) {}
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun reportAlertNotThere(
        alertId: String,
        currentNotThereVotes: Int,
        currentConfirmations: Int,
        voteWeight: Int = 1,
        forceOwnerResolve: Boolean = false
    ): Result<Boolean> {
        val docRef = db.collection("alerts").document(alertId)
        val weight = voteWeight.coerceIn(1, 2)
        val newVotes = currentNotThereVotes + weight
        val shouldResolve = forceOwnerResolve || (newVotes >= 3 && newVotes >= currentConfirmations)
        val currentUid = auth.currentUser?.uid
        val canEarnCleanupPoints = DriverPreferences.canConfirmAlert(
            context = appContext,
            alertId = "resolve_$alertId",
            reporterUid = "",
            currentUid = currentUid,
            alertCreatedMillis = 0L
        )
        return try {
            val updates = mutableMapOf<String, Any>(
                "notThereVotes" to newVotes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (shouldResolve) {
                updates["status"] = "resolved"
            }
            docRef.update(updates).await()
            DriverPreferences.recordAlertConfirmation(appContext, alertId)
            DriverPreferences.recordAlertConfirmation(appContext, "resolve_$alertId")
            if (shouldResolve) {
                alertDao?.updateAlertStatus(alertId, "resolved")
            }
            if (currentUid != null && canEarnCleanupPoints) {
                try {
                    userRepo.awardPointsRuta(
                        userId = currentUid,
                        pointsToAdd = UserRepository.CLEANUP_BONUS_POINTS,
                        resolvedDelta = 1
                    )
                } catch (_: Throwable) {}
            }
            Result.success(shouldResolve)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Grants +25 XP/pts precision bonus to the original author when any of their alerts
     * achieves 2+ community confirmations (tracked via `authorBonusAwarded` with zero-trust rule).
     */
    suspend fun claimAuthorConfirmationBonuses(userId: String): Result<Int> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(0)
            if (uid != userId) return Result.success(0)

            val ownAlerts = db.collection("alerts")
                .whereEqualTo("reporterUid", uid)
                .get()
                .await()

            var bonusAlertsCount = 0
            for (doc in ownAlerts.documents) {
                val conf = doc.getLong("confirmations")?.toInt() ?: 1
                val awarded = doc.getBoolean("authorBonusAwarded") ?: false
                if (conf >= 2 && !awarded) {
                    try {
                        doc.reference.update(
                            mapOf(
                                "authorBonusAwarded" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        bonusAlertsCount++
                    } catch (_: Throwable) {}
                }
            }

            if (bonusAlertsCount > 0) {
                userRepo.awardPointsRuta(
                    userId = uid,
                    pointsToAdd = bonusAlertsCount * UserRepository.AUTHOR_CONFIRM_BONUS_POINTS,
                    impactDelta = bonusAlertsCount,
                    bypassDailyCap = true
                )
            }
            Result.success(bonusAlertsCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveAlert(alertId: String): Result<Unit> {
        val docRef = db.collection("alerts").document(alertId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "resolved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            alertDao?.updateAlertStatus(alertId, "resolved")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAlert(alertId: String): Result<Unit> {
        if (alertId.startsWith(OFFLINE_QUEUED_PREFIX) || alertId.startsWith("offline_")) {
            val cleanId = alertId.removePrefix(OFFLINE_QUEUED_PREFIX)
            OfflineAlertQueue.removeByLocalId(appContext, cleanId)
            alertDao?.deleteAlert(cleanId)
            return Result.success(Unit)
        }
        val docRef = db.collection("alerts").document(alertId)
        return try {
            try {
                docRef.delete().await()
            } catch (_: Throwable) {
                try {
                    docRef.update(
                        mapOf(
                            "status" to "resolved",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Throwable) {}
                docRef.delete().await()
            }
            alertDao?.deleteAlert(alertId)
            alertDao?.updateAlertStatus(alertId, "resolved")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                alertDao?.deleteAlert(alertId)
                alertDao?.updateAlertStatus(alertId, "resolved")
            } catch (_: Throwable) {}
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearTestAlerts(): Result<Int> {
        return try {
            val snapshot = db.collection("alerts").get().await()
            var count = 0
            for (doc in snapshot.documents) {
                val desc = doc.getString("description") ?: ""
                val road = doc.getString("roadName") ?: ""
                if (desc.contains("test", ignoreCase = true) || desc.contains("prueba", ignoreCase = true) ||
                    road.contains("test", ignoreCase = true) || road.contains("prueba", ignoreCase = true)) {
                    try {
                        doc.reference.delete().await()
                    } catch (_: Throwable) {
                        try {
                            doc.reference.update(mapOf("status" to "resolved")).await()
                            doc.reference.delete().await()
                        } catch (_: Throwable) {}
                    }
                    alertDao?.deleteAlert(doc.id)
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearResolvedAlerts(): Result<Int> {
        return try {
            val snapshot = db.collection("alerts").whereEqualTo("status", "resolved").get().await()
            var count = 0
            for (doc in snapshot.documents) {
                try {
                    doc.reference.delete().await()
                } catch (_: Throwable) {}
                alertDao?.deleteAlert(doc.id)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllAlerts(): Result<Int> {
        return try {
            val snapshot = db.collection("alerts").get().await()
            var count = 0
            for (doc in snapshot.documents) {
                try {
                    doc.reference.delete().await()
                } catch (_: Throwable) {
                    try {
                        doc.reference.update(mapOf("status" to "resolved")).await()
                        doc.reference.delete().await()
                    } catch (_: Throwable) {}
                }
                alertDao?.deleteAlert(doc.id)
                alertDao?.updateAlertStatus(doc.id, "resolved")
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val OFFLINE_QUEUED_PREFIX = "OFFLINE_QUEUED:"
        private const val ONE_HOUR_MS = 60L * 60L * 1000L

        fun isTemporaryHazardType(type: String): Boolean {
            return when (type.lowercase().trim()) {
                "neblina", "agua", "animales", "vehiculo_averiado" -> true
                else -> false
            }
        }

        fun temporaryTtlMillis(type: String): Long {
            return when (type.lowercase().trim()) {
                "neblina" -> 3L * ONE_HOUR_MS
                "animales", "vehiculo_averiado" -> 4L * ONE_HOUR_MS
                "agua" -> 6L * ONE_HOUR_MS
                else -> 0L
            }
        }

        fun computeInitialExpiresAtMillis(type: String, nowMillis: Long = System.currentTimeMillis()): Long {
            val ttl = temporaryTtlMillis(type)
            return if (ttl > 0L) nowMillis + ttl else 0L
        }

        fun isAlertExpired(alert: RoadAlert, nowMillis: Long = System.currentTimeMillis()): Boolean {
            if (alert.expiresAtMillis > 0L) {
                return nowMillis >= alert.expiresAtMillis
            }
            if (isTemporaryHazardType(alert.type)) {
                val createdMs = alert.createdAt?.toDate()?.time ?: return false
                val ttl = temporaryTtlMillis(alert.type)
                if (ttl > 0L) return (nowMillis - createdMs) >= ttl
            }
            return false
        }

        fun formatExpiryStatusLabel(alert: RoadAlert, nowMillis: Long = System.currentTimeMillis()): String {
            if (!isTemporaryHazardType(alert.type) && alert.expiresAtMillis <= 0L) {
                return "📌 Estructural • Activa hasta solución"
            }
            val effectiveExpiry = if (alert.expiresAtMillis > 0L) {
                alert.expiresAtMillis
            } else {
                val createdMs = alert.createdAt?.toDate()?.time ?: nowMillis
                createdMs + temporaryTtlMillis(alert.type)
            }
            val remainingMs = (effectiveExpiry - nowMillis).coerceAtLeast(0L)
            val totalMins = (remainingMs / 60_000L).toInt()
            val hours = totalMins / 60
            val mins = totalMins % 60
            val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins.coerceAtLeast(1)} min"
            return "⏱️ Peligro temporal • Expira en $timeText si no se reconfirma"
        }
    }
}
