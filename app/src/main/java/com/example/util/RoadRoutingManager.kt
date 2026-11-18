package com.example.util

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

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
        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
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
     * Generates 2 perpendicular midpoint corridor waypoints on either side of the A->B line
     * so OSRM can discover genuine alternative road corridors even on straight Argentine highways
     * where OSRM `alternatives=3` only returns 1 path.
     */
    fun buildLateralCorridorWaypoints(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        offsetFraction: Double = 0.14
    ): List<Pair<Double, Double>> {
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val midLat = (lat1 + lat2) * 0.5
        val midLon = (lon1 + lon2) * 0.5
        val cosLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.25)

        val scaledDx = dLon * cosLat
        val scaledDy = dLat
        val norm = hypot(scaledDx, scaledDy)
        if (norm < 1e-6) return emptyList()

        // Perpendicular unit vector in metric-scaled degrees
        val perpLat = (-scaledDx / norm) * (norm * offsetFraction)
        val perpLon = (scaledDy / norm) * (norm * offsetFraction) / cosLat

        return listOf(
            Pair(midLat + perpLat, midLon + perpLon),
            Pair(midLat - perpLat, midLon - perpLon)
        )
    }

    private fun fetchOsrmRoutesSync(urlString: String, connectTimeoutMs: Int = 7000, readTimeoutMs: Int = 8000): List<RouteOption> {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
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
                            parsed.add(
                                RouteOption(
                                    index = rIdx,
                                    label = "Opción ${rIdx + 1}",
                                    distanceMeters = roadDist,
                                    durationSeconds = roadDuration,
                                    routePoints = points,
                                    isFromNetwork = true
                                )
                            )
                        }
                    }
                    return parsed
                }
            }
        }
        return emptyList()
    }

    private fun isNearlySameRoute(a: RouteOption, b: RouteOption): Boolean {
        val distDiff = abs(a.distanceMeters - b.distanceMeters)
        val aMid = a.routePoints[a.routePoints.size / 2]
        val bMid = b.routePoints[b.routePoints.size / 2]
        val midSepMeters = LocationRadarManager.calculateDistanceMeters(aMid.first, aMid.second, bMid.first, bMid.second)
        return distDiff < 250.0 && midSepMeters < 300.0
    }

    /**
     * Filters real OSRM routes so genuine parallel corridors are shown while long invented
     * town-street loops or redundant identical paths are discarded.
     */
    fun filterGenuineAlternativeRoutes(rawRoutes: List<RouteOption>): List<RouteOption> {
        if (rawRoutes.isEmpty()) return emptyList()
        val sortedFastest = rawRoutes.sortedBy { it.durationSeconds }
        val primary = sortedFastest.first()
        val kept = mutableListOf(primary)
        for (candidate in sortedFastest.drop(1)) {
            val reasonableDistance = candidate.distanceMeters <= primary.distanceMeters * 1.28
            val reasonableDuration = candidate.durationSeconds <= primary.durationSeconds * 1.35
            if (reasonableDistance && reasonableDuration && kept.none { isNearlySameRoute(it, candidate) }) {
                kept.add(candidate)
            }
            if (kept.size >= 3) break
        }
        return kept.mapIndexed { idx, opt ->
            val label = when {
                kept.size == 1 -> "Ruta directa"
                idx == 0 -> "Más rápida"
                idx == 1 -> "Alternativa 2"
                else -> "Alternativa 3"
            }
            opt.copy(index = idx, label = label)
        }
    }

    /**
     * Requests up to 3 real road-network routes from OSRM (`alternatives=3`).
     * If public OSRM returns only 1 route on a trip >= 2.5 km, automatically queries
     * 2 lateral corridor bypass waypoints in parallel so the driver receives 2–3 real options.
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

        var baseRoutes: List<RouteOption> = emptyList()
        for (attempt in 1..2) {
            try {
                baseRoutes = fetchOsrmRoutesSync(urlString)
                if (baseRoutes.isNotEmpty()) break
            } catch (t: Throwable) {
                Log.w(TAG, "OSRM multi-route attempt $attempt failed: ${t.message}")
            }
        }

        if (baseRoutes.isNotEmpty()) {
            // Only keep genuine OSRM road-network alternatives within sensible detour limits.
            // Never inject artificial lateral bypass waypoints on direct single-highway corridors.
            val sorted = filterGenuineAlternativeRoutes(baseRoutes)

            val first = sorted.first()
            routeCache[cacheKey] = CachedRoute(
                distanceMeters = first.distanceMeters,
                durationSeconds = first.durationSeconds,
                timestamp = System.currentTimeMillis(),
                routePoints = first.routePoints,
                isFromNetwork = true
            )
            multiRouteCache[cacheKey] = CachedMultiRoute(sorted, System.currentTimeMillis())
            Log.d(TAG, "OSRM resolved ${sorted.size} genuine route option(s)")
            return@withContext sorted
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
            if (lastPt != null && ptIsValid(lastPt)) {
                val tail = Pair(lastPt.getDouble(1), lastPt.getDouble(0))
                if (points.lastOrNull() != tail) points.add(tail)
            }
        }
        return points
    }

    private fun ptIsValid(arr: org.json.JSONArray): Boolean = arr.length() >= 2

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

    private fun isInsideArgentina(lat: Double, lon: Double): Boolean {
        return lat in -55.5..-21.5 && lon in -74.0..-53.0
    }

    suspend fun searchPlaces(
        query: String,
        context: Context,
        biasLat: Double = -34.6037,
        biasLon: Double = -58.3816
    ): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()
        val results = mutableListOf<GeocodedPlace>()

        try {
            val geocoder = Geocoder(context, Locale("es", "AR"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(
                cleanQuery,
                6,
                -55.2, -73.6, -21.7, -53.6
            )
            addresses?.forEach { addr ->
                if (isInsideArgentina(addr.latitude, addr.longitude)) {
                    val title = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: cleanQuery
                    val subtitle = listOfNotNull(addr.locality, addr.subAdminArea, addr.adminArea).distinct().joinToString(", ")
                    results.add(GeocodedPlace(title, if (subtitle.isNotBlank()) subtitle else "Argentina", addr.latitude, addr.longitude))
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Android Geocoder error: ${t.message}")
        }

        if (results.isEmpty()) {
            try {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = URL("https://photon.komoot.io/api/?q=$encoded&limit=6&lang=es&lat=$biasLat&lon=$biasLon")
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
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
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

        results.sortedByDescending { isInsideArgentina(it.latitude, it.longitude) }
    }

    /**
     * Resolves exact GPS coordinates to the current town/locality or nearest town/paraje + distance + coordinates
     * so hitchhike pickups and SOS locations never display generic placeholders.
     */
    suspend fun describeLocationWithNearestTown(
        context: Context,
        lat: Double,
        lng: Double
    ): String = withContext(Dispatchers.IO) {
        val coordsText = String.format(Locale.US, "%.4f, %.4f", lat, lng)
        val (routeName, estimatedKm) = LocationRadarManager.resolveRouteAndKm(lat, lng)
        val routePrefix = when {
            estimatedKm > 0 -> "$routeName Km $estimatedKm"
            routeName != "Camino Rural / Interurbano" -> routeName
            else -> ""
        }

        var townName: String? = null
        var streetName: String? = null
        var adminArea: String? = null
        var townLat: Double? = null
        var townLng: Double? = null

        // 1. Reverse geocode via Android Geocoder
        try {
            val geocoder = Geocoder(context, Locale("es", "AR"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 3)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                streetName = addr.thoroughfare?.takeIf { it.isNotBlank() && !it.startsWith("Unnamed", true) }
                townName = listOfNotNull(addr.locality, addr.subLocality, addr.subAdminArea)
                    .firstOrNull { it.isNotBlank() }
                adminArea = addr.adminArea?.takeIf { it.isNotBlank() }

                if (!townName.isNullOrBlank()) {
                    try {
                        @Suppress("DEPRECATION")
                        val center = geocoder.getFromLocationName(
                            listOfNotNull(townName, adminArea, "Argentina").joinToString(", "),
                            1,
                            -55.2, -73.6, -21.7, -53.6
                        )?.firstOrNull()
                        if (center != null) {
                            townLat = center.latitude
                            townLng = center.longitude
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Reverse Geocoder error: ${t.message}")
        }

        // 2. If settlement center or town name still missing, query Photon reverse for nearest town/village/hamlet
        if (townName.isNullOrBlank() || townLat == null || townLng == null) {
            try {
                val url = URL("https://photon.komoot.io/reverse?lat=$lat&lon=$lng&limit=6")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("User-Agent", "ViaAlerta-App/1.2 (Android; Argentina)")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val features = JSONObject(text).optJSONArray("features")
                    if (features != null) {
                        var bestDist = Double.MAX_VALUE
                        val settlementTypes = setOf("city", "town", "village", "hamlet", "suburb", "locality")
                        for (i in 0 until features.length()) {
                            val feat = features.getJSONObject(i)
                            val props = feat.optJSONObject("properties") ?: continue
                            val coords = feat.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                            val fLon = coords.optDouble(0)
                            val fLat = coords.optDouble(1)
                            val osmVal = props.optString("osm_value", "")
                            val candidateName = props.optString("name", "").ifBlank {
                                props.optString("city", props.optString("county", ""))
                            }
                            if (candidateName.isNotBlank()) {
                                val dist = LocationRadarManager.calculateDistanceMeters(lat, lng, fLat, fLon)
                                val isSettlement = osmVal in settlementTypes || props.optString("osm_key") == "place"
                                if ((isSettlement && dist < bestDist) || (townName.isNullOrBlank() && i == 0)) {
                                    bestDist = dist
                                    townName = candidateName
                                    townLat = fLat
                                    townLng = fLon
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Photon reverse fallback error: ${t.message}")
            }
        }

        val distanceToTown = if (townLat != null && townLng != null) {
            LocationRadarManager.calculateDistanceMeters(lat, lng, townLat, townLng)
        } else {
            -1.0
        }

        buildFormattedGpsDescription(
            routePrefix = routePrefix,
            townName = townName,
            streetName = streetName,
            distanceToTownMeters = distanceToTown,
            coordsText = coordsText
        )
    }

    fun buildFormattedGpsDescription(
        routePrefix: String,
        townName: String?,
        streetName: String?,
        distanceToTownMeters: Double,
        coordsText: String
    ): String {
        val locationPart = when {
            !townName.isNullOrBlank() && distanceToTownMeters >= 1500.0 -> {
                val distLabel = LocationRadarManager.formatDisplayDistance(distanceToTownMeters)
                "A $distLabel de $townName"
            }
            !townName.isNullOrBlank() && !streetName.isNullOrBlank() && !streetName.equals(townName, ignoreCase = true) ->
                "$streetName, $townName"
            !townName.isNullOrBlank() ->
                "En $townName"
            !streetName.isNullOrBlank() ->
                streetName
            else -> ""
        }

        val prefixParts = listOf(routePrefix, locationPart).filter { it.isNotBlank() }
        return if (prefixParts.isNotEmpty()) {
            "GPS: ${prefixParts.joinToString(" · ")} ($coordsText)"
        } else {
            "GPS: ($coordsText)"
        }
    }

    data class NearbyFuelStation(
        val name: String,
        val brand: String,
        val hasGnc: Boolean,
        val latitude: Double,
        val longitude: Double,
        val distanceMeters: Double,
        val isAhead: Boolean
    )

    /**
     * Queries free OpenStreetMap Overpass API (with mirror + Nominatim fallback) for fuel/GNC
     * stations near the driver, preferring stations ahead along the travel heading.
     */
    suspend fun findNearbyFuelStations(
        lat: Double,
        lon: Double,
        heading: Float = -1f,
        radiusMeters: Int = 25_000,
        preferGnc: Boolean = false
    ): List<NearbyFuelStation> = withContext(Dispatchers.IO) {
        val gncFilter = if (preferGnc) "[\"fuel:cng\"=\"yes\"]" else ""
        val overpassQuery = "[out:json][timeout:8];(node[\"amenity\"=\"fuel\"]$gncFilter(around:$radiusMeters,$lat,$lon);way[\"amenity\"=\"fuel\"]$gncFilter(around:$radiusMeters,$lat,$lon););out center 18;"
        val encodedQuery = URLEncoder.encode(overpassQuery, "UTF-8")
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter?data=$encodedQuery",
            "https://overpass.kumi.systems/api/interpreter?data=$encodedQuery"
        )

        val stations = mutableListOf<NearbyFuelStation>()

        for (endpoint in endpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4500
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "ViaAlerta-App/1.3 (Android; Argentina)")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val elements = JSONObject(text).optJSONArray("elements") ?: continue
                    for (i in 0 until elements.length()) {
                        val el = elements.getJSONObject(i)
                        val center = el.optJSONObject("center")
                        val sLat = center?.optDouble("lat", Double.NaN) ?: el.optDouble("lat", Double.NaN)
                        val sLon = center?.optDouble("lon", Double.NaN) ?: el.optDouble("lon", Double.NaN)
                        if (sLat.isNaN() || sLon.isNaN()) continue
                        val tags = el.optJSONObject("tags")
                        val rawBrand = tags?.optString("brand", "")?.trim().orEmpty()
                        val rawName = tags?.optString("name", "")?.trim().orEmpty()
                        val displayName = when {
                            rawName.isNotBlank() -> rawName
                            rawBrand.isNotBlank() -> "Estación $rawBrand"
                            else -> "Estación de servicio"
                        }
                        val hasGnc = tags?.optString("fuel:cng", "")?.equals("yes", ignoreCase = true) == true ||
                            displayName.contains("gnc", ignoreCase = true)
                        val dist = LocationRadarManager.calculateDistanceMeters(lat, lon, sLat, sLon)
                        val isAhead = if (heading >= 0f) {
                            val bearing = LocationRadarManager.calculateBearing(lat, lon, sLat, sLon)
                            LocationRadarManager.angleDifference(heading, bearing) <= 75f
                        } else true
                        stations.add(
                            NearbyFuelStation(
                                name = displayName,
                                brand = rawBrand,
                                hasGnc = hasGnc,
                                latitude = sLat,
                                longitude = sLon,
                                distanceMeters = dist,
                                isAhead = isAhead
                            )
                        )
                    }
                    if (stations.isNotEmpty()) break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Overpass fuel query failed ($endpoint): ${t.message}")
            }
        }

        if (stations.isEmpty()) {
            try {
                val queryText = if (preferGnc) "estacion de servicio gnc" else "estacion de servicio"
                val viewDelta = 0.25
                val encoded = URLEncoder.encode(queryText, "UTF-8")
                val url = URL(
                    "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=8" +
                        "&viewbox=${lon - viewDelta},${lat + viewDelta},${lon + viewDelta},${lat - viewDelta}&bounded=1"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "ViaAlerta-App/1.3 (Android; Argentina)")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val array = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val sLat = item.optDouble("lat", Double.NaN)
                        val sLon = item.optDouble("lon", Double.NaN)
                        if (sLat.isNaN() || sLon.isNaN()) continue
                        val title = item.optString("display_name", "Estación de servicio").substringBefore(",").trim()
                        val dist = LocationRadarManager.calculateDistanceMeters(lat, lon, sLat, sLon)
                        val isAhead = if (heading >= 0f) {
                            val bearing = LocationRadarManager.calculateBearing(lat, lon, sLat, sLon)
                            LocationRadarManager.angleDifference(heading, bearing) <= 75f
                        } else true
                        stations.add(
                            NearbyFuelStation(
                                name = title.ifBlank { "Estación de servicio" },
                                brand = "",
                                hasGnc = preferGnc || title.contains("gnc", ignoreCase = true),
                                latitude = sLat,
                                longitude = sLon,
                                distanceMeters = dist,
                                isAhead = isAhead
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Nominatim fuel fallback failed: ${t.message}")
            }
        }

        stations
            .distinctBy { "${(it.latitude * 1000).toInt()}_${(it.longitude * 1000).toInt()}" }
            .sortedWith(compareByDescending<NearbyFuelStation> { it.isAhead }.thenBy { it.distanceMeters })
    }
}
