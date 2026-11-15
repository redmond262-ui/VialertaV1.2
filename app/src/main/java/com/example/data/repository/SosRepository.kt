package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.local.AlertDao
import com.example.data.local.SosEntity
import com.example.data.local.ViaAlertaDatabase
import com.example.data.model.SosRequest
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

import com.example.util.getSafeFirestoreInstance

class SosRepository(
    private val db: FirebaseFirestore,
    private val alertDao: AlertDao? = null
) {
    constructor(context: Context) : this(
        getSafeFirestoreInstance(context),
        try { ViaAlertaDatabase.getInstance(context).alertDao() } catch (t: Throwable) { null }
    )

    private val auth = Firebase.auth
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeActiveSos(): Flow<List<SosRequest>> = flow {
        val path = "sos_requests"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "active")
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(SosRequest::class.java).filter { sos ->
                        val isDemo = sos.ownerName.contains("test", ignoreCase = true) ||
                                sos.ownerName.contains("prueba", ignoreCase = true) ||
                                sos.ownerName.contains("carlos silva", ignoreCase = true) ||
                                sos.routeDescription.contains("test", ignoreCase = true) ||
                                sos.routeDescription.contains("prueba", ignoreCase = true) ||
                                sos.ownerDni == "00000000" ||
                                sos.ownerDni == "36.123.456" ||
                                sos.licensePlate == "ARG 123" ||
                                sos.licensePlate == "AB 123 CD"
                        !isDemo
                    }
                    alertDao?.let { dao ->
                        ioScope.launch {
                            val entities = list.map { sos ->
                                SosEntity(
                                    id = sos.id,
                                    ownerUid = sos.ownerUid,
                                    ownerName = sos.ownerName,
                                    ownerDni = sos.ownerDni,
                                    dniVerified = sos.dniVerified,
                                    vehicleModel = sos.vehicleModel,
                                    vehicleColor = sos.vehicleColor,
                                    licensePlate = sos.licensePlate,
                                    assistanceType = sos.assistanceType,
                                    routeDescription = sos.routeDescription,
                                    latitude = sos.latitude,
                                    longitude = sos.longitude,
                                    phone = sos.phone,
                                    status = sos.status,
                                    timestampMillis = sos.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            dao.insertSosList(entities)
                        }
                    }
                    list
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
                    if (alertDao != null) {
                        try {
                            emitAll(
                                alertDao.getActiveSos().map { cachedList ->
                                    cachedList.map { entity ->
                                        SosRequest(
                                            id = entity.id,
                                            ownerUid = entity.ownerUid,
                                            ownerName = entity.ownerName,
                                            ownerDni = entity.ownerDni,
                                            dniVerified = entity.dniVerified,
                                            vehicleModel = entity.vehicleModel,
                                            vehicleColor = entity.vehicleColor,
                                            licensePlate = entity.licensePlate,
                                            assistanceType = entity.assistanceType,
                                            routeDescription = entity.routeDescription,
                                            latitude = entity.latitude,
                                            longitude = entity.longitude,
                                            phone = entity.phone,
                                            status = entity.status
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

    suspend fun createSos(
        ownerName: String,
        ownerDni: String,
        vehicleModel: String,
        vehicleColor: String,
        licensePlate: String,
        assistanceType: String,
        routeDescription: String,
        latitude: Double,
        longitude: Double,
        phone: String = ""
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("sos_requests").document()
            val payload = mapOf(
                "ownerUid" to uid,
                "ownerName" to ownerName,
                "ownerDni" to ownerDni,
                "dniVerified" to true,
                "vehicleModel" to vehicleModel,
                "vehicleColor" to vehicleColor,
                "licensePlate" to licensePlate.uppercase().trim(),
                "assistanceType" to assistanceType,
                "routeDescription" to routeDescription,
                "latitude" to latitude,
                "longitude" to longitude,
                "phone" to phone,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            alertDao?.insertSos(
                SosEntity(
                    id = docRef.id,
                    ownerUid = uid,
                    ownerName = ownerName,
                    ownerDni = ownerDni,
                    dniVerified = true,
                    vehicleModel = vehicleModel,
                    vehicleColor = vehicleColor,
                    licensePlate = licensePlate.uppercase().trim(),
                    assistanceType = assistanceType,
                    routeDescription = routeDescription,
                    latitude = latitude,
                    longitude = longitude,
                    phone = phone,
                    status = "active",
                    timestampMillis = System.currentTimeMillis()
                )
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "sos_requests")
            Result.failure(e)
        }
    }

    suspend fun resolveSos(sosId: String): Result<Unit> {
        val docRef = db.collection("sos_requests").document(sosId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "resolved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            alertDao?.updateSosStatus(sosId, "resolved")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSos(sosId: String): Result<Unit> {
        val docRef = db.collection("sos_requests").document(sosId)
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
            alertDao?.updateSosStatus(sosId, "resolved")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try { alertDao?.updateSosStatus(sosId, "resolved") } catch (_: Throwable) {}
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearAllSos(): Result<Int> {
        return try {
            val snapshot = db.collection("sos_requests").get().await()
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
                alertDao?.updateSosStatus(doc.id, "resolved")
                count++
            }
            try { alertDao?.clearAllCachedSos() } catch (_: Throwable) {}
            Result.success(count)
        } catch (e: Exception) {
            try { alertDao?.clearAllCachedSos() } catch (_: Throwable) {}
            Result.failure(e)
        }
    }
}
