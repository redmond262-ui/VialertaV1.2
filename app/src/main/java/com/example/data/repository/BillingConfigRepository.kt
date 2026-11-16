package com.example.data.repository

import android.content.Context
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AppBillingConfig
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

class BillingConfigRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(getSafeFirestoreInstance(context))

    private val auth = Firebase.auth

    fun observeBillingConfig(): Flow<AppBillingConfig> = flow {
        val docRef = db.collection("app_config").document("billing")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(AppBillingConfig::class.java) ?: AppBillingConfig()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(AppBillingConfig())
                }
        )
    }

    suspend fun getBillingConfig(): AppBillingConfig {
        return try {
            val snapshot = db.collection("app_config").document("billing").get().await()
            snapshot.toObject(AppBillingConfig::class.java) ?: AppBillingConfig()
        } catch (_: Throwable) {
            AppBillingConfig()
        }
    }

    suspend fun saveBillingConfig(
        mercadoPagoAlias: String,
        proPriceArs: Int,
        paymentLinkUrl: String = ""
    ): Result<Unit> {
        val docRef = db.collection("app_config").document("billing")
        return try {
            val payload = mapOf(
                "mercadoPagoAlias" to mercadoPagoAlias.trim(),
                "proPriceArs" to proPriceArs.coerceIn(0, 1_000_000),
                "paymentLinkUrl" to paymentLinkUrl.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
            }
            Result.failure(e)
        }
    }
}
