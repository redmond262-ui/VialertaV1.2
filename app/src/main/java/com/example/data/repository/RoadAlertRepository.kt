package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.local.AlertDao
import com.example.data.local.AlertEntity
import com.example.data.local.ViaAlertaDatabase
import com.example.data.model.RoadAlert
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

import com.example.util.DriverPreferences
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
                    val alerts = snapshot.toObjects(RoadAlert::class.java)
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
        connectsTowns: String = ""
    ): Result<String> {
        return try {
            val uid = requireUserId()
            if (DriverPreferences.isDuplicateNearbyReport(appContext, latitude, longitude, type)) {
                return Result.failure(IllegalStateException("Ya reportaste este peligro recién en el mismo punto."))
            }

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
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
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
                    timestampMillis = System.currentTimeMillis()
                )
            )

            // Gamification: Award 30 Puntos Ruta for reporting hazard (respects 200 pts/day cap)
            try {
                userRepo.awardPointsRuta(uid, 30)
            } catch (_: Throwable) {}

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "alerts")
            Result.failure(e)
        }
    }

    suspend fun confirmAlert(
        alertId: String,
        currentConfirmations: Int,
        reporterUid: String = "",
        alertCreatedMillis: Long = 0L
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
            docRef.update(
                mapOf(
                    "confirmations" to currentConfirmations + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

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
        currentConfirmations: Int
    ): Result<Boolean> {
        val docRef = db.collection("alerts").document(alertId)
        val newVotes = currentNotThereVotes + 1
        val shouldResolve = newVotes >= 3 && newVotes >= currentConfirmations
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
            if (shouldResolve) {
                alertDao?.updateAlertStatus(alertId, "resolved")
            }
            Result.success(shouldResolve)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
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
}
