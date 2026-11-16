package com.example.data.repository

import android.content.Context
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AppFeatureFlags
import com.example.data.model.FeatureMode
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

class FeatureFlagsRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(getSafeFirestoreInstance(context), context.applicationContext)

    private val auth = Firebase.auth

    fun observeFeatureFlags(): Flow<AppFeatureFlags> = flow {
        val docRef = db.collection("app_config").document("features")
        val initialCached = getCachedFeatureFlags(appContext)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    val flags = snapshot.toObject(AppFeatureFlags::class.java) ?: AppFeatureFlags()
                    cacheFeatureFlags(appContext, flags)
                    flags
                }
                .onStart { emit(initialCached) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(initialCached)
                }
        )
    }

    suspend fun getFeatureFlags(): AppFeatureFlags {
        return try {
            val snapshot = db.collection("app_config").document("features").get().await()
            val flags = snapshot.toObject(AppFeatureFlags::class.java) ?: AppFeatureFlags()
            cacheFeatureFlags(appContext, flags)
            flags
        } catch (_: Throwable) {
            getCachedFeatureFlags(appContext)
        }
    }

    suspend fun saveFeatureFlags(flags: AppFeatureFlags): Result<Unit> {
        val docRef = db.collection("app_config").document("features")
        return try {
            val payload = mapOf(
                "aventonMode" to sanitizeMode(flags.aventonMode),
                "baulVialMode" to sanitizeMode(flags.baulVialMode),
                "reclamosMode" to sanitizeMode(flags.reclamosMode),
                "proConversionMode" to sanitizeMode(flags.proConversionMode),
                "referralsMode" to sanitizeMode(flags.referralsMode),
                "preTripChecklistMode" to sanitizeMode(flags.preTripChecklistMode),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            cacheFeatureFlags(appContext, flags)
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

    companion object {
        private const val PREFS_NAME = "viaalerta_feature_flags_prefs"
        private const val KEY_AVENTON = "flag_aventon"
        private const val KEY_BAUL = "flag_baul"
        private const val KEY_RECLAMOS = "flag_reclamos"
        private const val KEY_PRO_CONV = "flag_pro_conv"
        private const val KEY_REFERRALS = "flag_referrals"
        private const val KEY_PRETRIP = "flag_pretrip"

        fun sanitizeMode(mode: String?): String {
            return when (mode?.lowercase()?.trim()) {
                FeatureMode.HIDDEN -> FeatureMode.HIDDEN
                FeatureMode.BETA -> FeatureMode.BETA
                FeatureMode.PUBLIC -> FeatureMode.PUBLIC
                else -> FeatureMode.BETA
            }
        }

        fun isModuleEnabled(mode: String, isAdmin: Boolean, isBetaTester: Boolean): Boolean {
            return when (mode.lowercase().trim()) {
                FeatureMode.PUBLIC -> true
                FeatureMode.BETA -> isAdmin || isBetaTester
                FeatureMode.HIDDEN -> false
                else -> false
            }
        }

        fun getCachedFeatureFlags(context: Context?): AppFeatureFlags {
            if (context == null) return AppFeatureFlags()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppFeatureFlags(
                aventonMode = prefs.getString(KEY_AVENTON, FeatureMode.BETA) ?: FeatureMode.BETA,
                baulVialMode = prefs.getString(KEY_BAUL, FeatureMode.BETA) ?: FeatureMode.BETA,
                reclamosMode = prefs.getString(KEY_RECLAMOS, FeatureMode.PUBLIC) ?: FeatureMode.PUBLIC,
                proConversionMode = prefs.getString(KEY_PRO_CONV, FeatureMode.BETA) ?: FeatureMode.BETA,
                referralsMode = prefs.getString(KEY_REFERRALS, FeatureMode.PUBLIC) ?: FeatureMode.PUBLIC,
                preTripChecklistMode = prefs.getString(KEY_PRETRIP, FeatureMode.PUBLIC) ?: FeatureMode.PUBLIC
            )
        }

        fun cacheFeatureFlags(context: Context?, flags: AppFeatureFlags) {
            if (context == null) return
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_AVENTON, sanitizeMode(flags.aventonMode))
                .putString(KEY_BAUL, sanitizeMode(flags.baulVialMode))
                .putString(KEY_RECLAMOS, sanitizeMode(flags.reclamosMode))
                .putString(KEY_PRO_CONV, sanitizeMode(flags.proConversionMode))
                .putString(KEY_REFERRALS, sanitizeMode(flags.referralsMode))
                .putString(KEY_PRETRIP, sanitizeMode(flags.preTripChecklistMode))
                .apply()
        }
    }
}
