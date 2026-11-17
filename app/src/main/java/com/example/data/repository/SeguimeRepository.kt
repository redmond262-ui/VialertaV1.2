package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.SeguimePoint
import com.example.data.model.SeguimeSession
import com.example.util.LocationRadarManager
import com.example.util.OfflineAlertQueue
import com.example.util.getSafeFirestoreInstance
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.roundToInt

sealed interface SeguimeFollowState {
    data object Loading : SeguimeFollowState
    data class Active(val session: SeguimeSession) : SeguimeFollowState
    data class Ended(val status: String, val driverName: String) : SeguimeFollowState
    data object NotFound : SeguimeFollowState
    data class Error(val message: String) : SeguimeFollowState
}

class SeguimeRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        getSafeFirestoreInstance(context),
        context.applicationContext
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para compartir tu recorrido en vivo.")
    }

    fun getActiveSessionId(): String? {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return null
        val id = prefs.getString(KEY_ACTIVE_SESSION_ID, "") ?: ""
        val exp = prefs.getLong(KEY_ACTIVE_SESSION_EXPIRES, 0L)
        if (id.length < 16 || (exp > 0L && System.currentTimeMillis() > exp)) {
            if (id.isNotBlank()) clearLocalSession()
            return null
        }
        return id
    }

    fun hasPendingOfflineSync(): Boolean {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return false
        return prefs.getBoolean(KEY_NEEDS_CREATE_SYNC, false) ||
            prefs.getBoolean(KEY_NEEDS_LOCATION_SYNC, false)
    }

    private fun clearLocalSession(clearPendingFinish: Boolean = false) {
        val editor = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.remove(KEY_ACTIVE_SESSION_ID)
            ?.remove(KEY_ACTIVE_SESSION_EXPIRES)
            ?.remove(KEY_DRIVER_NAME)
            ?.remove(KEY_VEHICLE_SUMMARY)
            ?.remove(KEY_DESTINATION_TITLE)
            ?.remove(KEY_ORIGIN_NAME)
            ?.remove(KEY_BREADCRUMBS_JSON)
            ?.remove(KEY_NEEDS_CREATE_SYNC)
            ?.remove(KEY_NEEDS_LOCATION_SYNC)
            ?.remove(KEY_LAST_SENT_AT_MS)
        if (clearPendingFinish) {
            editor
                ?.remove(KEY_PENDING_FINISH_ID)
                ?.remove(KEY_PENDING_FINISH_STATUS)
        }
        editor?.apply()
    }

    suspend fun startOrReuseSession(
        driverName: String,
        vehicleSummary: String,
        destinationTitle: String,
        originName: String,
        latitude: Double,
        longitude: Double,
        speedKmH: Float,
        heading: Float
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val cleanDriver = driverName.trim().ifBlank { "Conductor VíaAlerta" }.take(100)
            val cleanVehicle = vehicleSummary.trim().take(120)
            val cleanDest = destinationTitle.trim().take(150)
            val cleanOrigin = originName.trim().take(150)
            val existingId = getActiveSessionId()
            if (existingId != null) {
                updateSessionMetadataOrLocation(
                    sessionId = existingId,
                    latitude = latitude,
                    longitude = longitude,
                    speedKmH = speedKmH,
                    heading = heading,
                    destinationTitle = cleanDest,
                    forceImmediate = true
                )
                return Result.success(existingId)
            }

            val now = System.currentTimeMillis()
            val sessionId = generateSessionToken()
            val expiresAt = now + MAX_SESSION_DURATION_MS
            val initialPoints = listOf(SeguimePoint(lat = latitude, lng = longitude, ts = now))
            saveLocalSessionState(
                sessionId = sessionId,
                expiresAtMillis = expiresAt,
                driverName = cleanDriver,
                vehicleSummary = cleanVehicle,
                destinationTitle = cleanDest,
                originName = cleanOrigin,
                points = initialPoints,
                needsCreateSync = true,
                needsLocationSync = false
            )

            val createdOnline = tryWriteSessionCreate(
                sessionId = sessionId,
                uid = uid,
                driverName = cleanDriver,
                vehicleSummary = cleanVehicle,
                destinationTitle = cleanDest,
                originName = cleanOrigin,
                latitude = latitude,
                longitude = longitude,
                speedKmH = speedKmH,
                heading = heading,
                points = initialPoints,
                expiresAtMillis = expiresAt
            )
            if (createdOnline) {
                appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
                    ?.putBoolean(KEY_NEEDS_CREATE_SYNC, false)
                    ?.putLong(KEY_LAST_SENT_AT_MS, now)
                    ?.apply()
            }

            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, COLLECTION_NAME)
            Result.failure(e)
        }
    }

    suspend fun updateSessionMetadataOrLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        speedKmH: Float,
        heading: Float,
        destinationTitle: String? = null,
        forceImmediate: Boolean = false
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (sessionId.length < 16) return false
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val expiresAt = prefs?.getLong(KEY_ACTIVE_SESSION_EXPIRES, 0L) ?: 0L
        if (expiresAt > 0L && now > expiresAt) {
            finishSession(sessionId = sessionId, status = "cancelled")
            return false
        }
        val lastSentAt = prefs?.getLong(KEY_LAST_SENT_AT_MS, 0L) ?: 0L
        if (!forceImmediate && (now - lastSentAt) < MIN_UPDATE_INTERVAL_MS) {
            return false
        }

        val currentPoints = loadLocalBreadcrumbs().toMutableList()
        val lastPoint = currentPoints.lastOrNull()
        val movedMeters = if (lastPoint != null) {
            LocationRadarManager.calculateDistanceMeters(lastPoint.lat, lastPoint.lng, latitude, longitude)
        } else 999.0
        if (lastPoint == null || movedMeters >= 45.0 || (now - lastPoint.ts) >= 20_000L) {
            currentPoints.add(SeguimePoint(lat = latitude, lng = longitude, ts = now))
            while (currentPoints.size > MAX_BREADCRUMBS) {
                currentPoints.removeAt(0)
            }
            saveBreadcrumbs(currentPoints)
        }

        if (!destinationTitle.isNullOrBlank()) {
            prefs?.edit()?.putString(KEY_DESTINATION_TITLE, destinationTitle.trim().take(150))?.apply()
        }
        prefs?.edit()?.putBoolean(KEY_NEEDS_LOCATION_SYNC, true)?.apply()

        val needsCreate = prefs?.getBoolean(KEY_NEEDS_CREATE_SYNC, false) == true
        if (!OfflineAlertQueue.isNetworkAvailable(appContext)) {
            return false
        }

        val safeSpeed = speedKmH.coerceIn(0f, 350f)
        val safeHeading = heading.coerceIn(-1f, 360f)

        return try {
            if (needsCreate) {
                val created = tryWriteSessionCreate(
                    sessionId = sessionId,
                    uid = uid,
                    driverName = prefs?.getString(KEY_DRIVER_NAME, "Conductor VíaAlerta") ?: "Conductor VíaAlerta",
                    vehicleSummary = prefs?.getString(KEY_VEHICLE_SUMMARY, "") ?: "",
                    destinationTitle = prefs?.getString(KEY_DESTINATION_TITLE, "") ?: "",
                    originName = prefs?.getString(KEY_ORIGIN_NAME, "") ?: "",
                    latitude = latitude,
                    longitude = longitude,
                    speedKmH = safeSpeed,
                    heading = safeHeading,
                    points = currentPoints,
                    expiresAtMillis = prefs?.getLong(KEY_ACTIVE_SESSION_EXPIRES, now + MAX_SESSION_DURATION_MS)
                        ?: (now + MAX_SESSION_DURATION_MS)
                )
                if (created) {
                    prefs?.edit()
                        ?.putBoolean(KEY_NEEDS_CREATE_SYNC, false)
                        ?.putBoolean(KEY_NEEDS_LOCATION_SYNC, false)
                        ?.putLong(KEY_LAST_SENT_AT_MS, now)
                        ?.apply()
                }
                created
            } else {
                val payload = mutableMapOf<String, Any>(
                    "status" to "active",
                    "latitude" to latitude.coerceIn(-90.0, 90.0),
                    "longitude" to longitude.coerceIn(-180.0, 180.0),
                    "speedKmH" to safeSpeed.toDouble(),
                    "heading" to safeHeading.toDouble(),
                    "offlineBuffered" to (prefs?.getBoolean(KEY_NEEDS_LOCATION_SYNC, false) == true && (now - lastSentAt) > 35_000L),
                    "recentPoints" to pointsToFirestoreList(currentPoints),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!destinationTitle.isNullOrBlank()) {
                    payload["destinationTitle"] = destinationTitle.trim().take(150)
                }
                withTimeout(4500L) {
                    db.collection(COLLECTION_NAME).document(sessionId).update(payload).await()
                }
                prefs?.edit()
                    ?.putBoolean(KEY_NEEDS_LOCATION_SYNC, false)
                    ?.putLong(KEY_LAST_SENT_AT_MS, now)
                    ?.apply()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun flushBufferedSessionIfNeeded(
        latitude: Double,
        longitude: Double,
        speedKmH: Float,
        heading: Float
    ): Boolean {
        val pendingFinishFlushed = flushPendingFinishIfNeeded()
        val activeId = getActiveSessionId() ?: return pendingFinishFlushed
        if (!hasPendingOfflineSync()) return pendingFinishFlushed
        return updateSessionMetadataOrLocation(
            sessionId = activeId,
            latitude = latitude,
            longitude = longitude,
            speedKmH = speedKmH,
            heading = heading,
            forceImmediate = true
        ) || pendingFinishFlushed
    }

    private suspend fun flushPendingFinishIfNeeded(): Boolean {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return false
        val pendingId = prefs.getString(KEY_PENDING_FINISH_ID, "") ?: ""
        val pendingStatus = prefs.getString(KEY_PENDING_FINISH_STATUS, "cancelled") ?: "cancelled"
        if (pendingId.length < 16 || auth.currentUser?.uid == null || !OfflineAlertQueue.isNetworkAvailable(appContext)) {
            return false
        }
        return finishSession(sessionId = pendingId, status = pendingStatus).isSuccess
    }

    suspend fun finishActiveIfPresent(status: String = "cancelled"): Boolean {
        val activeId = getActiveSessionId() ?: return false
        return finishSession(sessionId = activeId, status = status).isSuccess
    }

    suspend fun finishSession(
        sessionId: String,
        status: String = "arrived",
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<Unit> {
        val safeStatus = if (status == "cancelled") "cancelled" else "arrived"
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasNeverSyncedOnline = prefs?.getBoolean(KEY_NEEDS_CREATE_SYNC, false) == true &&
            (prefs.getString(KEY_ACTIVE_SESSION_ID, "") == sessionId)
        clearLocalSession()
        if (sessionId.length < 16 || wasNeverSyncedOnline) {
            prefs?.edit()?.remove(KEY_PENDING_FINISH_ID)?.remove(KEY_PENDING_FINISH_STATUS)?.apply()
            return Result.success(Unit)
        }
        val uid = auth.currentUser?.uid
        if (uid == null || !OfflineAlertQueue.isNetworkAvailable(appContext)) {
            prefs?.edit()
                ?.putString(KEY_PENDING_FINISH_ID, sessionId)
                ?.putString(KEY_PENDING_FINISH_STATUS, safeStatus)
                ?.apply()
            return Result.success(Unit)
        }
        return try {
            val now = System.currentTimeMillis()
            // Anti-espionage & privacy: scrub all GPS coordinates, trail history, and trip details upon ending/cancelling
            val updates = mapOf<String, Any>(
                "status" to safeStatus,
                "latitude" to 0.0,
                "longitude" to 0.0,
                "speedKmH" to 0.0,
                "heading" to -1.0,
                "vehicleSummary" to "",
                "destinationTitle" to "",
                "originName" to "",
                "offlineBuffered" to false,
                "recentPoints" to emptyList<Map<String, Any>>(),
                "expiresAtMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            withTimeout(4500L) {
                db.collection(COLLECTION_NAME).document(sessionId).update(updates).await()
            }
            prefs?.edit()?.remove(KEY_PENDING_FINISH_ID)?.remove(KEY_PENDING_FINISH_STATUS)?.apply()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs?.edit()
                ?.putString(KEY_PENDING_FINISH_ID, sessionId)
                ?.putString(KEY_PENDING_FINISH_STATUS, safeStatus)
                ?.apply()
            handleFirestoreError(e, OperationType.UPDATE, "$COLLECTION_NAME/$sessionId")
            Result.failure(e)
        }
    }

    private suspend fun tryWriteSessionCreate(
        sessionId: String,
        uid: String,
        driverName: String,
        vehicleSummary: String,
        destinationTitle: String,
        originName: String,
        latitude: Double,
        longitude: Double,
        speedKmH: Float,
        heading: Float,
        points: List<SeguimePoint>,
        expiresAtMillis: Long
    ): Boolean {
        if (!OfflineAlertQueue.isNetworkAvailable(appContext)) return false
        return try {
            val payload = mapOf(
                "ownerUid" to uid,
                "driverName" to driverName.take(100).ifBlank { "Conductor VíaAlerta" },
                "vehicleSummary" to vehicleSummary.take(120),
                "destinationTitle" to destinationTitle.take(150),
                "originName" to originName.take(150),
                "status" to "active",
                "latitude" to latitude.coerceIn(-90.0, 90.0),
                "longitude" to longitude.coerceIn(-180.0, 180.0),
                "speedKmH" to speedKmH.coerceIn(0f, 350f).toDouble(),
                "heading" to heading.coerceIn(-1f, 360f).toDouble(),
                "offlineBuffered" to false,
                "recentPoints" to pointsToFirestoreList(points),
                "expiresAtMillis" to expiresAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            withTimeout(7500L) {
                db.collection(COLLECTION_NAME).document(sessionId).set(payload).await()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("SeguimeRepo", "Create Seguime failed (will retry when online): ${t.message}")
            false
        }
    }

    fun observeFollowedSession(sessionId: String): Flow<SeguimeFollowState> = callbackFlow {
        val cleanId = sessionId.trim()
        if (!isValidSessionId(cleanId)) {
            trySend(SeguimeFollowState.NotFound)
            close()
            return@callbackFlow
        }
        trySend(SeguimeFollowState.Loading)
        val listener = db.collection(COLLECTION_NAME).document(cleanId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(SeguimeFollowState.Error(error.localizedMessage ?: "Error al conectar con Seguime"))
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(SeguimeFollowState.NotFound)
                    return@addSnapshotListener
                }
                val session = snap.toObject(SeguimeSession::class.java)
                if (session == null) {
                    trySend(SeguimeFollowState.NotFound)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val isExpired = session.expiresAtMillis > 0L && now > session.expiresAtMillis
                if (session.status != "active" || isExpired) {
                    val resolvedStatus = if (isExpired && session.status == "active") "expired" else session.status
                    trySend(
                        SeguimeFollowState.Ended(
                            status = resolvedStatus,
                            driverName = session.driverName.ifBlank { "El conductor" }
                        )
                    )
                    return@addSnapshotListener
                }
                trySend(SeguimeFollowState.Active(session))
            }
        awaitClose { listener.remove() }
    }

    private fun pointsToFirestoreList(points: List<SeguimePoint>): List<Map<String, Any>> {
        return points.takeLast(MAX_BREADCRUMBS).map { pt ->
            mapOf(
                "lat" to pt.lat,
                "lng" to pt.lng,
                "ts" to pt.ts
            )
        }
    }

    private fun saveLocalSessionState(
        sessionId: String,
        expiresAtMillis: Long,
        driverName: String,
        vehicleSummary: String,
        destinationTitle: String,
        originName: String,
        points: List<SeguimePoint>,
        needsCreateSync: Boolean,
        needsLocationSync: Boolean
    ) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_ACTIVE_SESSION_ID, sessionId)
            ?.putLong(KEY_ACTIVE_SESSION_EXPIRES, expiresAtMillis)
            ?.putString(KEY_DRIVER_NAME, driverName)
            ?.putString(KEY_VEHICLE_SUMMARY, vehicleSummary)
            ?.putString(KEY_DESTINATION_TITLE, destinationTitle)
            ?.putString(KEY_ORIGIN_NAME, originName)
            ?.putString(KEY_BREADCRUMBS_JSON, serializePoints(points))
            ?.putBoolean(KEY_NEEDS_CREATE_SYNC, needsCreateSync)
            ?.putBoolean(KEY_NEEDS_LOCATION_SYNC, needsLocationSync)
            ?.apply()
    }

    private fun loadLocalBreadcrumbs(): List<SeguimePoint> {
        val raw = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_BREADCRUMBS_JSON, "") ?: ""
        return deserializePoints(raw)
    }

    private fun saveBreadcrumbs(points: List<SeguimePoint>) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_BREADCRUMBS_JSON, serializePoints(points))
            ?.apply()
    }

    companion object {
        const val COLLECTION_NAME = "seguime_sessions"
        const val BASE_TRACKER_URL = "https://gen-lang-client-0699193083.web.app/seguime.html"
        private const val PREFS_NAME = "viaalerta_seguime_prefs"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_ACTIVE_SESSION_EXPIRES = "active_session_expires"
        private const val KEY_DRIVER_NAME = "driver_name"
        private const val KEY_VEHICLE_SUMMARY = "vehicle_summary"
        private const val KEY_DESTINATION_TITLE = "destination_title"
        private const val KEY_ORIGIN_NAME = "origin_name"
        private const val KEY_BREADCRUMBS_JSON = "breadcrumbs_json"
        private const val KEY_NEEDS_CREATE_SYNC = "needs_create_sync"
        private const val KEY_NEEDS_LOCATION_SYNC = "needs_location_sync"
        private const val KEY_LAST_SENT_AT_MS = "last_sent_at_ms"
        private const val KEY_PENDING_FINISH_ID = "pending_finish_id"
        private const val KEY_PENDING_FINISH_STATUS = "pending_finish_status"

        private const val MIN_UPDATE_INTERVAL_MS = 12_000L
        private const val MAX_BREADCRUMBS = 35
        const val MAX_SESSION_DURATION_MS = 8L * 60L * 60L * 1000L

        private val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val secureRandom = SecureRandom()

        private val TOKEN_REGEX = Regex("^[a-zA-Z0-9_-]{16,64}$")
        private val QUERY_PARAM_REGEX = Regex("[?&]s=([a-zA-Z0-9_-]{16,64})")

        fun isValidSessionId(sessionId: String?): Boolean {
            return !sessionId.isNullOrBlank() && TOKEN_REGEX.matches(sessionId.trim())
        }

        fun parseSessionIdFromText(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            if (isValidSessionId(trimmed)) return trimmed
            return QUERY_PARAM_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                ?.takeIf { isValidSessionId(it) }
        }

        fun generateSessionToken(): String {
            val sb = StringBuilder("seguime_")
            repeat(20) {
                sb.append(TOKEN_ALPHABET[secureRandom.nextInt(TOKEN_ALPHABET.length)])
            }
            return sb.toString()
        }

        fun buildSeguimeUrl(sessionId: String): String {
            return "$BASE_TRACKER_URL?s=$sessionId"
        }

        fun buildWhatsAppShareMessage(
            driverName: String,
            vehicleSummary: String,
            destinationTitle: String,
            liveUrl: String
        ): String {
            val cleanDriver = driverName.trim().ifBlank { "Conductor VíaAlerta" }
            val vehicleLine = vehicleSummary.trim().takeIf { it.isNotBlank() }?.let { "\n🚗 *Vehículo:* $it" } ?: ""
            val destLine = destinationTitle.trim().takeIf { it.isNotBlank() }?.let { "\n🎯 *Destino:* $it" } ?: ""
            return """
                📍 *Seguime en vivo (VíaAlerta)*
                
                Soy *$cleanDriver*, salí a la ruta. Podés seguir mi recorrido en tiempo real desde este enlace privado:$vehicleLine$destLine
                
                🔗 $liveUrl
                
                🔒 _Por privacidad, este enlace caduca automáticamente apenas termine o cancele el viaje._
                📡 _Nota: En tramos rurales o de montaña sin señal 4G/5G el mapa conserva mi última posición GPS y actualiza ni bien vuelve la cobertura._
            """.trimIndent()
        }

        fun serializePoints(points: List<SeguimePoint>): String {
            val arr = JSONArray()
            for (pt in points.takeLast(MAX_BREADCRUMBS)) {
                arr.put(
                    JSONObject().apply {
                        put("lat", pt.lat)
                        put("lng", pt.lng)
                        put("ts", pt.ts)
                    }
                )
            }
            return arr.toString()
        }

        fun deserializePoints(json: String): List<SeguimePoint> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val list = ArrayList<SeguimePoint>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        SeguimePoint(
                            lat = obj.optDouble("lat", 0.0),
                            lng = obj.optDouble("lng", 0.0),
                            ts = obj.optLong("ts", 0L)
                        )
                    )
                }
                list
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
}
