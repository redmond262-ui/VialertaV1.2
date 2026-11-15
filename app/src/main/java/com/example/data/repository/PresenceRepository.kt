package com.example.data.repository

import android.content.Context
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.OnlineDriver
import com.example.util.getSafeFirestoreInstance
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

class PresenceRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(getSafeFirestoreInstance(context))

    private val auth = Firebase.auth

    suspend fun sendHeartbeat(
        userName: String,
        routeName: String,
        speedKmH: Float,
        isDriving: Boolean,
        latitude: Double,
        longitude: Double
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No auth user"))
        val docRef = db.collection("online_presence").document(uid)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "userName" to userName.ifBlank { "Conductor VíaAlerta" },
                "routeName" to routeName.ifBlank { "Ruta en Argentina" },
                "speedKmH" to speedKmH,
                "isDriving" to isDriving,
                "latitude" to latitude,
                "longitude" to longitude,
                "timestampMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeOnlineDrivers(): Flow<List<OnlineDriver>> = flow {
        val path = "online_presence"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    val now = System.currentTimeMillis()
                    val thresholdMillis = 150_000L // 2.5 minutes inactivity window
                    snapshot.toObjects(OnlineDriver::class.java).filter { driver ->
                        (now - driver.timestampMillis) < thresholdMillis
                    }.sortedByDescending { it.timestampMillis }
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

    suspend fun clearMyPresence(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("online_presence").document(uid)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
