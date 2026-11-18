package com.example.data.repository

import android.content.Context
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AppFeatureFlags
import com.example.data.model.AppUpdateConfig
import com.example.data.model.FeatureMode
import com.example.util.getSafeFirestoreInstance
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
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

    fun observeAppUpdateConfig(): Flow<AppUpdateConfig> = flow {
        val docRef = db.collection("app_config").document("app_update")
        val initialCached = getCachedAppUpdateConfig(appContext)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    val cfg = snapshot.toObject(
                        AppUpdateConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: AppUpdateConfig()
                    cacheAppUpdateConfig(appContext, cfg)
                    cfg
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

    suspend fun getAppUpdateConfig(): AppUpdateConfig {
        return try {
            val snapshot = db.collection("app_config").document("app_update").get().await()
            val cfg = snapshot.toObject(
                AppUpdateConfig::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: AppUpdateConfig()
            cacheAppUpdateConfig(appContext, cfg)
            cfg
        } catch (_: Throwable) {
            getCachedAppUpdateConfig(appContext)
        }
    }

    suspend fun saveAppUpdateConfig(config: AppUpdateConfig): Result<Unit> {
        val docRef = db.collection("app_config").document("app_update")
        return try {
            if (auth.currentUser == null) {
                return Result.failure(IllegalStateException("Debés iniciar sesión como administrador."))
            }
            val cleanCode = config.latestVersionCode.coerceIn(1, 1_000_000)
            val cleanName = config.latestVersionName.trim().take(32)
            val cleanUrl = config.downloadUrl.trim().take(500)
            val cleanMessage = config.message.trim().take(500)
            if (config.enabled) {
                require(cleanName.isNotBlank()) { "Ingresá el nombre de versión (ej: 2.3)." }
                require(isValidUpdateDownloadUrl(cleanUrl)) {
                    "Ingresá un link válido que empiece con https:// o http://"
                }
            }
            val payload = mapOf(
                "enabled" to config.enabled,
                "mandatory" to config.mandatory,
                "latestVersionCode" to cleanCode,
                "latestVersionName" to cleanName,
                "message" to cleanMessage,
                "downloadUrl" to cleanUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val sanitizedConfig = config.copy(
                latestVersionCode = cleanCode,
                latestVersionName = cleanName,
                message = cleanMessage,
                downloadUrl = cleanUrl
            )
            cacheAppUpdateConfig(appContext, sanitizedConfig)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
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

        private const val KEY_UPDATE_ENABLED = "update_enabled"
        private const val KEY_UPDATE_MANDATORY = "update_mandatory"
        private const val KEY_UPDATE_CODE = "update_version_code"
        private const val KEY_UPDATE_NAME = "update_version_name"
        private const val KEY_UPDATE_MESSAGE = "update_message"
        private const val KEY_UPDATE_URL = "update_download_url"

        fun isValidUpdateDownloadUrl(url: String?): Boolean {
            val clean = url?.trim() ?: return false
            if (clean.length > 500 || clean.contains(' ')) return false
            val afterScheme = when {
                clean.startsWith("https://", ignoreCase = true) -> clean.substring(8)
                clean.startsWith("http://", ignoreCase = true) -> clean.substring(7)
                else -> return false
            }
            return afterScheme.contains('.') && afterScheme.length >= 4
        }

        fun shouldPromptAppUpdate(
            installedVersionCode: Int,
            config: AppUpdateConfig,
            dismissedVersionCode: Int
        ): Boolean {
            if (!config.enabled) return false
            if (config.latestVersionCode <= installedVersionCode) return false
            if (!isValidUpdateDownloadUrl(config.downloadUrl)) return false
            if (!config.mandatory && dismissedVersionCode >= config.latestVersionCode) return false
            return true
        }

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

        fun getCachedAppUpdateConfig(context: Context?): AppUpdateConfig {
            if (context == null) return AppUpdateConfig()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppUpdateConfig(
                enabled = prefs.getBoolean(KEY_UPDATE_ENABLED, false),
                mandatory = prefs.getBoolean(KEY_UPDATE_MANDATORY, false),
                latestVersionCode = prefs.getInt(KEY_UPDATE_CODE, 1).coerceAtLeast(1),
                latestVersionName = prefs.getString(KEY_UPDATE_NAME, "") ?: "",
                message = prefs.getString(KEY_UPDATE_MESSAGE, "") ?: "",
                downloadUrl = prefs.getString(KEY_UPDATE_URL, "") ?: ""
            )
        }

        fun cacheAppUpdateConfig(context: Context?, config: AppUpdateConfig) {
            if (context == null) return
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_UPDATE_ENABLED, config.enabled)
                .putBoolean(KEY_UPDATE_MANDATORY, config.mandatory)
                .putInt(KEY_UPDATE_CODE, config.latestVersionCode.coerceAtLeast(1))
                .putString(KEY_UPDATE_NAME, config.latestVersionName.trim().take(32))
                .putString(KEY_UPDATE_MESSAGE, config.message.trim().take(500))
                .putString(KEY_UPDATE_URL, config.downloadUrl.trim().take(500))
                .apply()
        }
    }
}
