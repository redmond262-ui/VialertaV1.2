package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.HitchhikeRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

import com.example.util.getSafeFirestoreInstance

class HitchhikeRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(getSafeFirestoreInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeWaitingHitchhikes(): Flow<List<HitchhikeRequest>> = flow {
        val path = "hitchhike_requests"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "waiting")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(HitchhikeRequest::class.java).filter { hh ->
                        val isDemo = hh.userName.contains("test", ignoreCase = true) ||
                                hh.userName.contains("prueba", ignoreCase = true) ||
                                hh.userName.contains("pasajero verificado", ignoreCase = true) ||
                                hh.destination.contains("test", ignoreCase = true) ||
                                hh.destination.contains("prueba", ignoreCase = true) ||
                                hh.destination.contains("Rosario, Mar del Plata", ignoreCase = true) ||
                                hh.userDni == "DNI Verificado"
                        !isDemo
                    }
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
                    emit(emptyList())
                }
        )
    }

    suspend fun createHitchhike(
        userName: String,
        userDni: String,
        roleCategory: String,
        pickupPoint: String,
        destination: String,
        latitude: Double,
        longitude: Double
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("hitchhike_requests").document()
            val payload = mapOf(
                "userUid" to uid,
                "userName" to userName,
                "userDni" to userDni,
                "roleCategory" to roleCategory,
                "pickupPoint" to pickupPoint,
                "destination" to destination,
                "latitude" to latitude,
                "longitude" to longitude,
                "pairedPlate" to "",
                "status" to "waiting",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "hitchhike_requests")
            Result.failure(e)
        }
    }

    suspend fun pairWithDriver(hitchhikeId: String, driverPlate: String): Result<Unit> {
        val docRef = db.collection("hitchhike_requests").document(hitchhikeId)
        return try {
            docRef.update(
                mapOf(
                    "pairedPlate" to driverPlate.uppercase().trim(),
                    "status" to "picked_up",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun resolveHitchhike(hitchhikeId: String): Result<Unit> {
        val docRef = db.collection("hitchhike_requests").document(hitchhikeId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "picked_up",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHitchhike(hitchhikeId: String): Result<Unit> {
        val docRef = db.collection("hitchhike_requests").document(hitchhikeId)
        return try {
            try {
                docRef.delete().await()
            } catch (_: Throwable) {
                try {
                    docRef.update(
                        mapOf(
                            "status" to "picked_up",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Throwable) {}
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearAllHitchhikes(): Result<Int> {
        return try {
            val snapshot = db.collection("hitchhike_requests").get().await()
            var count = 0
            for (doc in snapshot.documents) {
                try {
                    doc.reference.delete().await()
                } catch (_: Throwable) {
                    try {
                        doc.reference.update(mapOf("status" to "picked_up")).await()
                        doc.reference.delete().await()
                    } catch (_: Throwable) {}
                }
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
