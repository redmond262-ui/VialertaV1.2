package com.example.data.repository

import android.content.Context
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.BaulVialItem
import com.example.util.getSafeFirestoreInstance
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BaulVialRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(getSafeFirestoreInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeItems(): Flow<List<BaulVialItem>> = flow {
        val path = "baul_vial"
        emitAll(
            db.collection(path)
                .whereIn("status", listOf("activo", "devuelto"))
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(BaulVialItem::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
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

    suspend fun createItem(
        type: String,
        category: String,
        title: String,
        publicDescription: String,
        roadName: String,
        latitude: Double,
        longitude: Double,
        publisherName: String,
        verificationQuestion: String,
        safeDropoffPlace: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("baul_vial").document()
            val payload = mapOf(
                "type" to type,
                "category" to category,
                "title" to title.trim().take(120),
                "publicDescription" to publicDescription.trim().take(500),
                "roadName" to roadName.ifBlank { "Ruta Argentina" }.trim().take(120),
                "latitude" to latitude,
                "longitude" to longitude,
                "publisherUid" to uid,
                "publisherName" to publisherName.ifBlank { "Conductor" }.take(100),
                "verificationQuestion" to verificationQuestion.trim().take(200),
                "safeDropoffPlace" to safeDropoffPlace.trim().take(150),
                "claimantUid" to "",
                "claimantName" to "",
                "claimantAnswer" to "",
                "claimantContact" to "",
                "publisherContact" to "",
                "claimStatus" to "sin_reclamo",
                "status" to "activo",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "baul_vial")
            Result.failure(e)
        }
    }

    suspend fun submitVerificationClaim(
        itemId: String,
        claimantName: String,
        answer: String,
        contactPhone: String
    ): Result<Unit> {
        val docRef = db.collection("baul_vial").document(itemId)
        return try {
            val uid = requireUserId()
            docRef.update(
                mapOf(
                    "claimantUid" to uid,
                    "claimantName" to claimantName.ifBlank { "Usuario" }.take(100),
                    "claimantAnswer" to answer.trim().take(250),
                    "claimantContact" to contactPhone.trim().take(40),
                    "claimStatus" to "pendiente",
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

    suspend fun reviewClaim(
        itemId: String,
        approved: Boolean,
        publisherContactToShare: String
    ): Result<Unit> {
        val docRef = db.collection("baul_vial").document(itemId)
        return try {
            requireUserId()
            val updates = if (approved) {
                mapOf(
                    "claimStatus" to "aprobado",
                    "publisherContact" to publisherContactToShare.trim().take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "claimStatus" to "rechazado",
                    "publisherContact" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markReturned(itemId: String): Result<Unit> {
        val docRef = db.collection("baul_vial").document(itemId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "status" to "devuelto",
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

    suspend fun deleteItem(itemId: String): Result<Unit> {
        val docRef = db.collection("baul_vial").document(itemId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
