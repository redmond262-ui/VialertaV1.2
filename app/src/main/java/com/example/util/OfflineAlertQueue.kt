package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class PendingOfflineAlert(
    val localId: String,
    val type: String,
    val depthCm: Int,
    val roadName: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val heading: Float,
    val directionDesc: String,
    val affectsBothDirections: Boolean,
    val estimatedKm: Int,
    val reporterName: String,
    val roadCategory: String,
    val connectsTowns: String,
    val reporterLevel: Int,
    val createdAtMillis: Long
)

/**
 * Lightweight persistent FIFO queue for road hazard alerts created while traveling
 * through rural cellular dead zones ("pozos negros" sin señal 4G/5G).
 * Automatically flushes to Firestore upon network restoration.
 */
object OfflineAlertQueue {
    private const val PREFS_NAME = "viaalerta_offline_queue"
    private const val KEY_PENDING_ALERTS = "pending_alerts_json"
    private const val MAX_QUEUED_ALERTS = 50

    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(active) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected
            }
        } catch (_: Throwable) {
            true
        }
    }

    @Synchronized
    fun enqueue(context: Context?, alert: PendingOfflineAlert): Boolean {
        if (context == null) return false
        val current = getPending(context).toMutableList()
        if (current.any { it.localId == alert.localId }) return true
        if (current.size >= MAX_QUEUED_ALERTS) {
            current.removeAt(0)
        }
        current.add(alert)
        savePending(context, current)
        return true
    }

    @Synchronized
    fun getPending(context: Context?): List<PendingOfflineAlert> {
        if (context == null) return emptyList()
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_ALERTS, "") ?: ""
        return deserializeQueue(raw)
    }

    @Synchronized
    fun count(context: Context?): Int = getPending(context).size

    @Synchronized
    fun removeByLocalId(context: Context?, localId: String) {
        if (context == null || localId.isBlank()) return
        val cleanId = localId.removePrefix("OFFLINE_QUEUED:")
        val filtered = getPending(context).filterNot { it.localId == cleanId }
        savePending(context, filtered)
    }

    private fun savePending(context: Context, list: List<PendingOfflineAlert>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_ALERTS, serializeQueue(list))
            .apply()
    }

    fun serializeQueue(list: List<PendingOfflineAlert>): String {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("localId", item.localId)
                put("type", item.type)
                put("depthCm", item.depthCm)
                put("roadName", item.roadName)
                put("description", item.description)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("heading", item.heading.toDouble())
                put("directionDesc", item.directionDesc)
                put("affectsBothDirections", item.affectsBothDirections)
                put("estimatedKm", item.estimatedKm)
                put("reporterName", item.reporterName)
                put("roadCategory", item.roadCategory)
                put("connectsTowns", item.connectsTowns)
                put("reporterLevel", item.reporterLevel)
                put("createdAtMillis", item.createdAtMillis)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeQueue(json: String): List<PendingOfflineAlert> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<PendingOfflineAlert>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val localId = obj.optString("localId", "")
                if (localId.isBlank()) continue
                list.add(
                    PendingOfflineAlert(
                        localId = localId,
                        type = obj.optString("type", "pozo"),
                        depthCm = obj.optInt("depthCm", 0),
                        roadName = obj.optString("roadName", ""),
                        description = obj.optString("description", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        heading = obj.optDouble("heading", -1.0).toFloat(),
                        directionDesc = obj.optString("directionDesc", ""),
                        affectsBothDirections = obj.optBoolean("affectsBothDirections", false),
                        estimatedKm = obj.optInt("estimatedKm", 0),
                        reporterName = obj.optString("reporterName", ""),
                        roadCategory = obj.optString("roadCategory", "ruta"),
                        connectsTowns = obj.optString("connectsTowns", ""),
                        reporterLevel = obj.optInt("reporterLevel", 1),
                        createdAtMillis = obj.optLong("createdAtMillis", 0L)
                    )
                )
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
