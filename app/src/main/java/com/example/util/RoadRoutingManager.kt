package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object RoadRoutingManager {

    private const val TAG = "RoadRoutingManager"
    private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving/"

    private val routeCache = ConcurrentHashMap<String, CachedRoute>()
    private val multiRouteCache = ConcurrentHashMap<String, CachedMultiRoute>()

    data class RouteDetails(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val isFromNetwork: Boolean = true,
        val routePoints: List<Pair<Double, Double>> = emptyList()
    )

    data class RouteOption(
        val index: Int,
        val label: String,
        val distanceMeters: Double,
        val durationSeconds: Double,
        val routePoints: List<Pair<Double, Double>>,
        val isFromNetwork: Boolean = true
    )

    data class CachedRoute(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val timestamp: Long,
        val routePoints: List<Pair<Double, Double>> = emptyList(),
        val isFromNetwork: Boolean = true
    )

    data class CachedMultiRoute(
        val options: List<RouteOption>,
        val timestamp: Long
    )

    data class RouteProgress(
        val closestIndex: Int,
        val crossTrackDistanceMeters: Double,
        val remainingDistanceMeters: Double,
        val trimmedRoutePoints: List<Pair<Double, Double>>
    )

    private fun buildCacheKey(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val qLat1 = (lat1 * 800).toInt()
        val qLon1 = (lon1 * 800).toInt()
        val qLat2 = (lat2 * 800).toInt()
        val qLon2 = (lon2 * 800).toInt()
        return "${qLat1}_${qLon1}_${qLat2}_$qLon2"
    }

    fun getCachedOrApproxRoadDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val key = buildCacheKey(lat1, lon1, lat2, lon2)
        val cached = routeCache[key]
        if (cached != null && cached.isFromNetwork && (System.currentTimeMillis() - cached.timestamp < 120_000L)) {
            return cached.distanceMeters
        }
        return LocationRadarManager.calculateRoadDistanceMeters(lat1, lon1, lat2, lon2)
    }

    fun getCachedOrApproxDurationSeconds(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val key = buildCacheKey(lat1, lon1, lat2, lon2)
        val cached = routeCache[key]
        if (cached != null && cached.isFromNetwork && (System.currentTimeMillis() - cached.timestamp < 120_000L)) {
            return cached.durationSeconds
        }
        val distMeters = LocationRadarManager.calculateRoadDistanceMeters(lat1, lon1, lat2, lon2)
        return (distMeters / 20.8).coerceAtLeast(60.0)
    }

    /**
     * Adjusts remaining duration cleanly using the driver's smoothed moving speed
     * (ignoring stops at traffic lights / gas stations so 0 km/h never corrupts pace).
     */
    fun adjustDurationForMovingPace(
        baseDurationSeconds: Double,
        routeDistanceMeters: Double,
        smoothedMovingSpeedKmH: Float
    ): Double {
        if (baseDurationSeconds <= 0.0 || routeDistanceMeters <= 0.0) return baseDurationSeconds
        if (smoothedMovingSpeedKmH < 12f) return baseDurationSeconds

        val expectedSpeedKmH = ((routeDistanceMeters / baseDurationSeconds) * 3.6).coerceIn(30.0, 120.0)
        val rawRatio = expectedSpeedKmH / smoothedMovingSpeedKmH.toDouble()
        val blendedFactor = (0.60 + 0.40 * rawRatio.coerceIn(0.75, 1.35))
        return (baseDurationSeconds * blendedFactor).coerceAtLeast(30.0)
    }

    /**
     * Projects the driver onto the active street-by-street polyline:
     * - Measures exact perpendicular deviation (crossTrackDistanceMeters) to know when off-route
     * - Sums exact remaining meters vertex-by-vertex along the traced road polyline
     * - Trims behind-the-car segments so distance ticks down accurately without network calls
     */
    fun projectDriverOntoRoute(
        driverLat: Double,
        driverLon: Double,
        routePoints: List<Pair<Double, Double>>
    ): RouteProgress {
        if (routePoints.size < 2) {
            return RouteProgress(0, Double.MAX_VALUE, 0.0, routePoints)
        }

        var bestIndex = 0
        var minVertexDist = Double.MAX_VALUE

        for (i in routePoints.indices) {
            val (pLat, pLon) = routePoints[i]
            val d = LocationRadarManager.calculateDistanceMeters(driverLat, driverLon, pLat, pLon)
            if (d < minVertexDist) {
                minVertexDist = d
                bestIndex = i
            }
            if (i < routePoints.size - 1) {
                val (nLat, nLon) = routePoints[i + 1]
                val midD = LocationRadarManager.calculateDistanceMeters(
                    driverLat, driverLon, (pLat + nLat) * 0.5, (pLon + nLon) * 0.5
                )
                if (midD < minVertexDist) {
                    minVertexDist = midD
                    bestIndex = i
                }
            }
        }

        // Advance past vertex if we are closer to next vertex along the forward path
        val startRemainingIdx = (bestIndex + 1).coerceAtMost(routePoints.lastIndex)
        val trimmed = mutableListOf(Pair(driverLat, driverLon))
        var remainingMeters = 0.0

        var prevLat = driverLat
        var prevLon = driverLon
        for (i in startRemainingIdx until routePoints.size) {
            val pt = routePoints[i]
            remainingMeters += LocationRadarManager.calculateDistanceMeters(prevLat, prevLon, pt.first, pt.second)
            trimmed.add(pt)
            prevLat = pt.first
            prevLon = pt.second
        }

        return RouteProgress(
            closestIndex = bestIndex,
            crossTrackDistanceMeters = minVertexDist,
            remainingDistanceMeters = remainingMeters,
            trimmedRoutePoints = trimmed
        )
    }

    /**
     * Shortest distance (meters) from a hazard point to the actual traced road polyline.
     */
    fun minDistanceMetersToRoute(
        lat: Double,
        lon: Double,
        routePoints: List<Pair<Double, Double>>
    ): Double {
        if (routePoints.isEmpty()) return Double.MAX_VALUE
        var minDist = Double.MAX_VALUE
        for (i in routePoints.indices) {
            val (pLat, pLon) = routePoints[i]
            val d = LocationRadarManager.calculateDistanceMeters(lat, lon, pLat, pLon)
            if (d < minDist) minDist = d
            if (i < routePoints.size - 1) {
                val (nLat, nLon) = routePoints[i + 1]
                val midD = LocationRadarManager.calculateDistanceMeters(
                    lat, lon, (pLat + nLat) * 0.5, (pLon + nLon) * 0.5
                )
                if (midD < minDist) minDist = midD
            }
        }
        return minDist
    }

    fun calculateFormattedETA(
        durationSeconds: Double,
        extraIncidentDelaySeconds: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<String, String> {
        val totalSeconds = (durationSeconds + extraIncidentDelaySeconds).toLong().coerceAtLeast(60L)
        val arrivalMillis = nowMillis + (totalSeconds * 1000L)
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val arrivalTimeString = "${timeFormat.format(java.util.Date(arrivalMillis))} hs"

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val durationString = if (hours > 0) {
            "${hours} h ${minutes} min"
        } else {
            "${minutes.coerceAtLeast(1)} min"
        }
        return Pair(arrivalTimeString, durationString)
    }

    /**
     * Requests up to 3 real road-network routes from OSRM (`alternatives=3`) with retry and
     * resilient timeouts so the driver can compare and pick among 3 options.
     * Never caches failed network attempts as real routes.
     */
    suspend fun getAlternativeRoutes(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val straightDist = LocationRadarManager.calculateDistanceMeters(lat1, lon1, lat2, lon2)
        if (straightDist < 120.0) {
            val shortDuration = (straightDist / 6.0).coerceAtLeast(20.0)
            return@withContext listOf(
                RouteOption(
                    index = 0,
                    label = "Llegada Directa",
                    distanceMeters = straightDist,
                    durationSeconds = shortDuration,
                    routePoints = listOf(Pair(lat1, lon1), Pair(lat2, lon2)),
                    isFromNetwork = true
                )
            )
        }

        val cacheKey = buildCacheKey(lat1, lon1, lat2, lon2)
        val cachedMulti = multiRouteCache[cacheKey]
        if (cachedMulti != null && (System.currentTimeMillis() - cachedMulti.timestamp < 90_000L)) {
            return@withContext cachedMulti.options
        }

        val urlString = "$OSRM_BASE_URL$lon1,$lat1;$lon2,$lat2?overview=full&geometries=geojson&steps=false&alternatives=3"

        for (attempt in 1..2) {
            try {
                val url = URL(urlString)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "ViaAlerta-Routing/1.2 (Android; Argentina)")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(response)
                    if (json.optString("code") == "Ok") {
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val parsed = mutableListOf<RouteOption>()
                            val count = routes.length().coerceAtMost(3)
                            for (rIdx in 0 until count) {
                                val routeObj = routes.getJSONObject(rIdx)
                                val roadDist = routeObj.optDouble("distance", -1.0)
                                val roadDuration = routeObj.optDouble("duration", -1.0)
                                val points = parseRouteGeometry(routeObj, maxPoints = 1200)
                                if (roadDist > 0.0 && roadDuration > 0.0 && points.size >= 2) {
                                    val label = when (rIdx) {
                                        0 -> "Más rápida"
                                        1 -> "Alternativa 2"
                                        else -> "Alternativa 3"
                                    }
                                    parsed.add(
                                        RouteOption(
                                            index = rIdx,
                                            label = label,
                                            distanceMeters = roadDist,
                                            durationSeconds = roadDuration,
                                            routePoints = points,
                                            isFromNetwork = true
                                        )
                                    )
                                }
                            }
                            if (parsed.isNotEmpty()) {
                                val first = parsed.first()
                                routeCache[cacheKey] = CachedRoute(
                                    distanceMeters = first.distanceMeters,
                                    durationSeconds = first.durationSeconds,
                                    timestamp = System.currentTimeMillis(),
                                    routePoints = first.routePoints,
                                    isFromNetwork = true
                                )
                                multiRouteCache[cacheKey] = CachedMultiRoute(parsed, System.currentTimeMillis())
                                Log.d(TAG, "OSRM resolved ${parsed.size} route options cleanly")
                                return@withContext parsed
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "OSRM multi-route attempt $attempt failed: ${t.message}")
            }
        }

        // Offline fallback — explicitly flagged isFromNetwork = false and NOT stored in network cache
        val fallbackDist = LocationRadarManager.calculateRoadDistanceMeters(lat1, lon1, lat2, lon2)
        val fallbackDuration = (fallbackDist / 20.8).coerceAtLeast(60.0)
        val fallbackLine = listOf(Pair(lat1, lon1), Pair(lat2, lon2))
        return@withContext listOf(
            RouteOption(
                index = 0,
                label = "Sin señal (Estimada)",
                distanceMeters = fallbackDist,
                durationSeconds = fallbackDuration,
                routePoints = fallbackLine,
                isFromNetwork = false
            )
        )
    }

    private fun parseRouteGeometry(routeObj: JSONObject, maxPoints: Int): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val geom = routeObj.optJSONObject("geometry") ?: return points
        val coords = geom.optJSONArray("coordinates") ?: return points
        val total = coords.length()
        val step = if (total > maxPoints) (total / (maxPoints - 200)).coerceAtLeast(1) else 1
        var i = 0
        while (i < total) {
            val pt = coords.optJSONArray(i)
            if (pt != null && pt.length() >= 2) {
                points.add(Pair(pt.getDouble(1), pt.getDouble(0)))
            }
            i += step
        }
        if (total > 0) {
            val lastPt = coords.optJSONArray(total - 1)
            if (lastPt != null && lastPt.length() >= 2) {
                val tail = Pair(lastPt.getDouble(1), lastPt.getDouble(0))
                if (points.lastOrNull() != tail) points.add(tail)
            }
        }
        return points
    }

    suspend fun getActualRouteDetails(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): RouteDetails {
        val first = getAlternativeRoutes(lat1, lon1, lat2, lon2).first()
        return RouteDetails(
            distanceMeters = first.distanceMeters,
            durationSeconds = first.durationSeconds,
            isFromNetwork = first.isFromNetwork,
            routePoints = first.routePoints
        )
    }

    suspend fun getActualRoadDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return getActualRouteDetails(lat1, lon1, lat2, lon2).distanceMeters
    }

    data class GeocodedPlace(
        val title: String,
        val subtitle: String,
        val latitude: Double,
        val longitude: Double
    )

    suspend fun searchPlaces(query: String, context: Context): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()
        val results = mutableListOf<GeocodedPlace>()

        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale("es", "AR"))
            val addresses = geocoder.getFromLocationName(cleanQuery, 6)
            addresses?.forEach { addr ->
                val title = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: cleanQuery
                val subtitle = listOfNotNull(addr.locality, addr.subAdminArea, addr.adminArea).distinct().joinToString(", ")
                results.add(GeocodedPlace(title, if (subtitle.isNotBlank()) subtitle else "Argentina", addr.latitude, addr.longitude))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Android Geocoder error: ${t.message}")
        }

        if (results.isEmpty()) {
            try {
                val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
                val url = URL("https://photon.komoot.io/api/?q=$encoded&limit=6&lang=es")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("User-Agent", "ViaAlerta-App/1.2 (Android; Argentina)")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val features = json.optJSONArray("features")
                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feat = features.getJSONObject(i)
                            val props = feat.optJSONObject("properties") ?: continue
                            val geom = feat.optJSONObject("geometry") ?: continue
                            val coords = geom.optJSONArray("coordinates") ?: continue
                            val lon = coords.optDouble(0)
                            val lat = coords.optDouble(1)

                            val name = props.optString("name", cleanQuery)
                            val city = props.optString("city", props.optString("county", ""))
                            val state = props.optString("state", "")
                            val country = props.optString("country", "")

                            val sub = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                            results.add(GeocodedPlace(name, if (sub.isNotBlank()) sub else "Ubicación en mapa", lat, lon))
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Photon fallback error: ${t.message}")
            }
        }

        if (results.isEmpty()) {
            try {
                val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
                val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&countrycodes=ar&limit=5")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("User-Agent", "ViaAlerta-App/1.2 (Android; Argentina)")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = org.json.JSONArray(text)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val lat = item.optDouble("lat")
                        val lon = item.optDouble("lon")
                        val displayName = item.optString("display_name", cleanQuery)
                        val parts = displayName.split(",")
                        val title = parts.firstOrNull()?.trim() ?: cleanQuery
                        val sub = parts.drop(1).take(3).joinToString(", ") { it.trim() }
                        results.add(GeocodedPlace(title, sub, lat, lon))
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Nominatim fallback error: ${t.message}")
            }
        }

        results
    }
}
