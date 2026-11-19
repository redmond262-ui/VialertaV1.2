package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.HitchhikeRequest
import com.example.data.model.RoadAlert
import com.example.data.model.SeguimeSession
import com.example.data.model.SosRequest
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.DriverLocation
import com.example.util.RoadRoutingManager
import kotlin.math.roundToInt
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val MAP_STYLE_DAY = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
private const val MAP_STYLE_NIGHT = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

@Composable
fun RealRoadMapView(
    driverLocation: DriverLocation,
    alerts: List<RoadAlert>,
    sosList: List<SosRequest>,
    hitchhikes: List<HitchhikeRequest>,
    modifier: Modifier = Modifier,
    targetFocusLocation: Pair<Double, Double>? = null,
    targetFocusTitle: String? = null,
    routePolyline: List<Pair<Double, Double>> = emptyList(),
    alternativeRoutes: List<RoadRoutingManager.RouteOption> = emptyList(),
    selectedRouteIndex: Int = 0,
    isRouteActive: Boolean = false,
    isNightDimMode: Boolean = false,
    isRouteOverviewMode: Boolean = false,
    fitRouteTrigger: Int = 0,
    followedSession: SeguimeSession? = null,
    followedFocusTrigger: Int = 0,
    onRouteOverviewModeChange: (Boolean) -> Unit = {},
    onMapInteractionChange: (Boolean) -> Unit = {},
    onAlertSelected: (RoadAlert) -> Unit = {},
    onSosSelected: (SosRequest) -> Unit = {},
    onHitchhikeSelected: (HitchhikeRequest) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleLoaded by remember { mutableStateOf(false) }
    var loadedStyleIsNight by remember { mutableStateOf<Boolean?>(null) }
    var hasMapLibreError by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableFloatStateOf(14.5f) }
    var isInteracting by remember { mutableStateOf(false) }
    var hasInitiallyCentered by remember { mutableStateOf(false) }

    // Markers lookup
    val activeMarkers = remember { mutableMapOf<Marker, Any>() }

    // Safe MapView initialization
    val mapView = remember {
        try {
            MapView(context).apply {
                onCreate(null)
                onStart()
                onResume()
                getMapAsync { map ->
                    try {
                        mapInstance = map
                        map.uiSettings.isAttributionEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isCompassEnabled = false // Disabled default compass to avoid overlap with filters
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true

                        val isRealFix = driverLocation.hasGpsFix ||
                            (driverLocation.latitude != 0.0 && driverLocation.longitude != 0.0 &&
                             !(driverLocation.latitude == -34.6037 && driverLocation.longitude == -58.3816))

                        val initialTarget = LatLng(driverLocation.latitude, driverLocation.longitude)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(initialTarget)
                            .zoom(if (isRealFix) 15.5 else currentZoom.toDouble())
                            .tilt(if (isRealFix) 30.0 else 0.0)
                            .build()

                        if (isRealFix) {
                            hasInitiallyCentered = true
                        }

                        val initialStyle = if (isNightDimMode) MAP_STYLE_NIGHT else MAP_STYLE_DAY
                        loadedStyleIsNight = isNightDimMode
                        map.setStyle(initialStyle) {
                            isStyleLoaded = true
                            if (isRealFix) {
                                hasInitiallyCentered = true
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(initialTarget)
                                    .zoom(15.5)
                                    .tilt(30.0)
                                    .build()
                            }
                        }

                        map.setOnMarkerClickListener { marker ->
                            try {
                                val data = activeMarkers[marker]
                                when (data) {
                                    is RoadAlert -> onAlertSelected(data)
                                    is SosRequest -> onSosSelected(data)
                                    is HitchhikeRequest -> onHitchhikeSelected(data)
                                }
                            } catch (t: Throwable) {
                                android.util.Log.w("RealRoadMapView", "Marker click error: ${t.message}")
                            }
                            true
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("RealRoadMapView", "Error in getMapAsync: ${t.message}")
                        hasMapLibreError = true
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("RealRoadMapView", "Failed to instantiate MapView: ${t.message}")
            hasMapLibreError = true
            null
        }
    }

    // Auto-resume camera follow timer after manual touch
    var userTouchTimestamp by remember { mutableStateOf(0L) }
    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            userTouchTimestamp = System.currentTimeMillis()
        }
    }

    // Forward Android Lifecycle events to MapView safely
    if (mapView != null) {
        DisposableEffect(lifecycleOwner, mapView) {
            val observer = LifecycleEventObserver { _, event ->
                try {
                    when (event) {
                        Lifecycle.Event.ON_START -> mapView.onStart()
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        Lifecycle.Event.ON_STOP -> mapView.onStop()
                        Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                        else -> {}
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("RealRoadMapView", "Lifecycle error: ${t.message}")
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                try {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    mapView.onDestroy()
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }

        // Touch listener on MapView to detect user interaction
        DisposableEffect(mapView) {
            mapView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                        userTouchTimestamp = System.currentTimeMillis()
                        if (!isInteracting) {
                            isInteracting = true
                            onMapInteractionChange(true)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        userTouchTimestamp = System.currentTimeMillis()
                        isInteracting = false
                        onMapInteractionChange(false)
                    }
                }
                false
            }
            onDispose {
                try {
                    mapView.setOnTouchListener(null)
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }
    }

    // Dynamically switch MapLibre base style whenever user/clock toggles Day <-> Night Mode
    LaunchedEffect(mapInstance, isNightDimMode) {
        val map = mapInstance ?: return@LaunchedEffect
        if (loadedStyleIsNight != isNightDimMode) {
            isStyleLoaded = false
            loadedStyleIsNight = isNightDimMode
            val targetStyle = if (isNightDimMode) MAP_STYLE_NIGHT else MAP_STYLE_DAY
            try {
                map.setStyle(targetStyle) {
                    isStyleLoaded = true
                }
            } catch (t: Throwable) {
                android.util.Log.w("RealRoadMapView", "Failed to switch map style: ${t.message}")
            }
        }
    }

    // Camera Auto-Follow ("Follow Vehicle"):
    // Tracks the vehicle smoothly unless the driver is touching the map or inspecting Full Route Overview.
    LaunchedEffect(driverLocation, isInteracting, isRouteOverviewMode) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect

        val now = System.currentTimeMillis()
        val timeSinceLastTouch = now - userTouchTimestamp
        val shouldAutoFollow = !isRouteOverviewMode && !isInteracting && (timeSinceLastTouch > 5500L || !hasInitiallyCentered)

        if (shouldAutoFollow) {
            val target = LatLng(driverLocation.latitude, driverLocation.longitude)
            val cameraBuilder = CameraPosition.Builder()
                .target(target)
                .zoom(15.5)

            // If traveling at highway speed, tilt camera forward and rotate with heading
            if (driverLocation.speedKmH > 25f && driverLocation.heading > 0f) {
                cameraBuilder.tilt(40.0)
                cameraBuilder.bearing(driverLocation.heading.toDouble())
            }

            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraBuilder.build()), 1000)
            hasInitiallyCentered = true
        }
    }

    // Fit camera to full route bounds cleanly with top-down flat orientation when requested
    LaunchedEffect(fitRouteTrigger, routePolyline, isStyleLoaded) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect
        if (fitRouteTrigger > 0 && routePolyline.size >= 2) {
            try {
                userTouchTimestamp = System.currentTimeMillis() + 9000L
                val builder = LatLngBounds.Builder()
                routePolyline.forEach { (lat, lon) -> builder.include(LatLng(lat, lon)) }
                targetFocusLocation?.let { builder.include(LatLng(it.first, it.second)) }
                builder.include(LatLng(driverLocation.latitude, driverLocation.longitude))
                // Flatten pitch & north orientation so whole route overview is unmistakable
                map.cameraPosition = CameraPosition.Builder(map.cameraPosition)
                    .tilt(0.0)
                    .bearing(0.0)
                    .build()
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 90, 210, 90, 170),
                    900
                )
            } catch (_: Throwable) {}
        }
    }

    // Camera animate to searched destination preview
    LaunchedEffect(targetFocusLocation) {
        val target = targetFocusLocation ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect
        if (routePolyline.size >= 2) {
            try {
                userTouchTimestamp = System.currentTimeMillis() + 12000L
                val builder = LatLngBounds.Builder()
                routePolyline.forEach { (lat, lon) -> builder.include(LatLng(lat, lon)) }
                builder.include(LatLng(target.first, target.second))
                builder.include(LatLng(driverLocation.latitude, driverLocation.longitude))
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 120, 260, 120, 180),
                    1100
                )
            } catch (_: Throwable) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), 14.0),
                    1100
                )
            }
        } else {
            userTouchTimestamp = System.currentTimeMillis() + 12000L
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), 14.0),
                1100
            )
        }
    }

    // Focus camera on followed Seguime vehicle inside our native map
    LaunchedEffect(followedSession?.id, followedFocusTrigger) {
        val followed = followedSession ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect
        if (followed.latitude != 0.0 || followed.longitude != 0.0) {
            userTouchTimestamp = System.currentTimeMillis() + 12000L
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(followed.latitude, followed.longitude), 15.5),
                900
            )
        }
    }

    // Update driver car position, traced route polylines and markers ONLY when style is loaded
    LaunchedEffect(
        mapInstance,
        isStyleLoaded,
        driverLocation,
        alerts,
        sosList,
        hitchhikes,
        targetFocusLocation,
        routePolyline,
        alternativeRoutes,
        selectedRouteIndex,
        isRouteActive,
        followedSession
    ) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isStyleLoaded) return@LaunchedEffect

        try {
            map.clear()
            activeMarkers.clear()

            // 0a. Dimmed preview of non-selected route alternatives (only before starting trip)
            if (!isRouteActive && alternativeRoutes.size > 1) {
                alternativeRoutes.forEach { option ->
                    if (option.index != selectedRouteIndex && option.routePoints.size >= 2) {
                        val altLatLngs = option.routePoints.map { LatLng(it.first, it.second) }
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(altLatLngs)
                                .color(android.graphics.Color.parseColor("#1E293B"))
                                .width(8f)
                        )
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(altLatLngs)
                                .color(android.graphics.Color.parseColor("#64748B"))
                                .width(5f)
                        )
                    }
                }
            }

            // 0b. Selected/Active Road Polyline (Street-by-street OSRM geometry)
            if (routePolyline.size >= 2) {
                val latLngs = routePolyline.map { LatLng(it.first, it.second) }
                // Outer dark outline casing for clarity
                map.addPolyline(
                    PolylineOptions()
                        .addAll(latLngs)
                        .color(android.graphics.Color.parseColor("#0F172A"))
                        .width(10f)
                )
                // Inner vibrant route path (Emerald green when actively navigating, Sky blue on preview)
                map.addPolyline(
                    PolylineOptions()
                        .addAll(latLngs)
                        .color(
                            android.graphics.Color.parseColor(
                                if (isRouteActive) "#10B981" else "#38BDF8"
                            )
                        )
                        .width(6.5f)
                )
            }

            val iconFactory = IconFactory.getInstance(context)

            // 1. Driver Car Marker (Electric Blue / Cyan with Directional Heading Arrow)
            val carIconBitmap = createCarMarkerBitmap(driverLocation.heading)
            val carIcon = iconFactory.fromBitmap(carIconBitmap)
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(driverLocation.latitude, driverLocation.longitude))
                    .title("Mi Vehículo")
                    .snippet("Velocidad: ${driverLocation.speedKmH.roundToInt()} km/h")
                    .icon(carIcon)
            )

            // 2. Real Road Alerts
            for (alert in alerts) {
                val alertIconBitmap = createHazardMarkerBitmap(alert.type, alert.depthCm)
                val alertIcon = iconFactory.fromBitmap(alertIconBitmap)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(alert.latitude, alert.longitude))
                        .title(alert.description.ifBlank { alert.type.uppercase() })
                        .snippet(alert.roadName)
                        .icon(alertIcon)
                )
                activeMarkers[marker] = alert
            }

            // 3. SOS Requests
            for (sos in sosList) {
                val sosIconBitmap = createSosMarkerBitmap()
                val sosIcon = iconFactory.fromBitmap(sosIconBitmap)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(sos.latitude, sos.longitude))
                        .title("SOS: ${sos.vehicleModel}")
                        .snippet(sos.routeDescription)
                        .icon(sosIcon)
                )
                activeMarkers[marker] = sos
            }

            // 4. Hitchhike Requests
            for (hh in hitchhikes) {
                val hhIconBitmap = createHitchhikeMarkerBitmap()
                val hhIcon = iconFactory.fromBitmap(hhIconBitmap)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(hh.latitude, hh.longitude))
                        .title("Aventón: ${hh.userName}")
                        .snippet("Hacia: ${hh.destination}")
                        .icon(hhIcon)
                )
                activeMarkers[marker] = hh
            }

            // 5. Target Destination Pin if searched
            if (targetFocusLocation != null) {
                val destIconBitmap = createDestinationMarkerBitmap()
                val destIcon = iconFactory.fromBitmap(destIconBitmap)
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(targetFocusLocation.first, targetFocusLocation.second))
                        .title(targetFocusTitle ?: "Destino Seleccionado")
                        .snippet(if (isRouteActive) "Viaje en curso • Recalculando ETA" else "Tocá Trazar Ruta para iniciar")
                        .icon(destIcon)
                )
            }

            // 6. Followed Seguime Vehicle (trail + live pin inside Vialerta map)
            if (followedSession != null && followedSession.status == "active" &&
                (followedSession.latitude != 0.0 || followedSession.longitude != 0.0)
            ) {
                val trail = followedSession.recentPoints
                    .filter { it.lat != 0.0 || it.lng != 0.0 }
                    .map { LatLng(it.lat, it.lng) }
                    .toMutableList()
                val livePos = LatLng(followedSession.latitude, followedSession.longitude)
                if (trail.isEmpty() || trail.last().latitude != livePos.latitude || trail.last().longitude != livePos.longitude) {
                    trail.add(livePos)
                }
                if (trail.size >= 2) {
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(trail)
                            .color(android.graphics.Color.parseColor("#38BDF8"))
                            .width(5.5f)
                    )
                }
                val followedBitmap = createFollowedDriverBitmap()
                val followedIcon = iconFactory.fromBitmap(followedBitmap)
                map.addMarker(
                    MarkerOptions()
                        .position(livePos)
                        .title("📡 ${followedSession.driverName.ifBlank { "Conductor Seguime" }}")
                        .snippet("${followedSession.speedKmH.roundToInt()} km/h" + (if (followedSession.destinationTitle.isNotBlank()) " • Hacia ${followedSession.destinationTitle}" else ""))
                        .icon(followedIcon)
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e("RealRoadMapView", "Error updating markers: ${t.message}")
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 0f else 1f,
        label = "controlsAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (!hasMapLibreError && mapView != null) {
            // Real Native MapView
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Resilient High-Performance GPS Radar Fallback (Always works, zero crashes)
            FallbackRadarCanvas(
                driverLocation = driverLocation,
                alerts = alerts,
                sosList = sosList,
                hitchhikes = hitchhikes,
                onInteractionChange = { interacting ->
                    isInteracting = interacting
                    onMapInteractionChange(interacting)
                },
                onAlertSelected = onAlertSelected,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Floating Zoom, Recenter, and Cardinal Compass Controls (fade out smoothly when touching map)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .alpha(controlsAlpha),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cardinal Compass Widget (Norte / Dirección Cardinal no tapada por los filtros)
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        try {
                            mapInstance?.animateCamera(CameraUpdateFactory.bearingTo(0.0), 400)
                        } catch (_: Throwable) {}
                    }
                    .border(1.dp, Slate700, CircleShape)
                    .testTag("compass_btn"),
                shape = CircleShape,
                color = Slate900.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "▲",
                            color = AlertRed500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 12.sp
                        )
                        Text(
                            text = "N",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 11.sp
                        )
                    }
                }
            }

            // Zoom In (+)
            FloatingActionButton(
                onClick = {
                    try {
                        val map = mapInstance
                        if (map != null && isStyleLoaded) {
                            currentZoom = (currentZoom + 1f).coerceAtMost(19f)
                            map.animateCamera(CameraUpdateFactory.zoomTo(currentZoom.toDouble()), 300)
                        }
                    } catch (t: Throwable) {
                        // ignore
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, Slate700, CircleShape)
                    .testTag("zoom_in_btn"),
                containerColor = Slate900,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Acercar mapa (+)", modifier = Modifier.size(22.dp))
            }

            // Zoom Out (-)
            FloatingActionButton(
                onClick = {
                    try {
                        val map = mapInstance
                        if (map != null && isStyleLoaded) {
                            currentZoom = (currentZoom - 1f).coerceAtLeast(3f)
                            map.animateCamera(CameraUpdateFactory.zoomTo(currentZoom.toDouble()), 300)
                        }
                    } catch (t: Throwable) {
                        // ignore
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, Slate700, CircleShape)
                    .testTag("zoom_out_btn"),
                containerColor = Slate900,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Alejar mapa (-)", modifier = Modifier.size(22.dp))
            }

            // Recenter GPS Button (exits Route Overview & restores 3D driving camera immediately)
            FloatingActionButton(
                onClick = {
                    try {
                        onRouteOverviewModeChange(false)
                        userTouchTimestamp = 0L
                        isInteracting = false
                        val map = mapInstance
                        if (map != null && isStyleLoaded) {
                            val target = LatLng(driverLocation.latitude, driverLocation.longitude)
                            val camBuilder = CameraPosition.Builder()
                                .target(target)
                                .zoom(15.6)
                            if (driverLocation.speedKmH > 25f && driverLocation.heading > 0f) {
                                camBuilder.tilt(40.0).bearing(driverLocation.heading.toDouble())
                            } else {
                                camBuilder.tilt(25.0).bearing(0.0)
                            }
                            map.animateCamera(
                                CameraUpdateFactory.newCameraPosition(camBuilder.build()),
                                800
                            )
                        }
                    } catch (t: Throwable) {
                        // ignore
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = if (isRouteOverviewMode) 2.dp else 1.dp,
                        color = if (isRouteOverviewMode) Amber400 else Color(0xFF00D4FF).copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .testTag("recenter_gps_btn"),
                containerColor = if (isRouteOverviewMode) Color(0xFF0F2942) else Slate900,
                contentColor = if (isRouteOverviewMode) Amber400 else Color(0xFF00D4FF),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centrar en mi vehículo", modifier = Modifier.size(22.dp))
            }
        }

        // Permanent Real-Time Digital Speedometer (Estilo Waze / GPS Cruiser en esquina inferior izquierda)
        val roundedSpeed = driverLocation.speedKmH.roundToInt().coerceAtLeast(0)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 26.dp)
                .alpha(controlsAlpha)
                .testTag("driver_speedometer"),
            shape = RoundedCornerShape(16.dp),
            color = if (isNightDimMode) Color(0xFF060A14).copy(alpha = 0.88f) else Slate950.copy(alpha = 0.92f),
            border = BorderStroke(
                width = 1.5.dp,
                color = when {
                    roundedSpeed >= 125 -> AlertRed500
                    roundedSpeed >= 105 -> Amber400
                    isNightDimMode -> Color(0xFF00D4FF).copy(alpha = 0.55f)
                    else -> Color(0xFF00D4FF)
                }
            ),
            shadowElevation = if (isNightDimMode) 2.dp else 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$roundedSpeed",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        roundedSpeed >= 125 -> AlertRed500
                        roundedSpeed >= 105 -> Amber400
                        isNightDimMode -> Color.White.copy(alpha = 0.86f)
                        else -> Color.White
                    },
                    lineHeight = 26.sp
                )
                Text(
                    text = "KM/H",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (isNightDimMode) 0.5f else 0.65f),
                    letterSpacing = 0.5.sp,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

@Composable
private fun FallbackRadarCanvas(
    driverLocation: DriverLocation,
    alerts: List<RoadAlert>,
    sosList: List<SosRequest>,
    hitchhikes: List<HitchhikeRequest>,
    onInteractionChange: (Boolean) -> Unit,
    onAlertSelected: (RoadAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .background(Slate950)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onInteractionChange(true) },
                    onDragEnd = { onInteractionChange(false) },
                    onDragCancel = { onInteractionChange(false) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        panOffsetX += dragAmount.x
                        panOffsetY += dragAmount.y
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f + panOffsetX
            val cy = size.height / 2f + panOffsetY

            // Radar rings
            val maxR = size.width.coerceAtLeast(size.height) * 0.8f
            for (step in 1..4) {
                val r = (maxR / 4f) * step
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = r,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }

            // Crosshair lines
            drawLine(
                color = Color(0xFF334155),
                start = Offset(cx - maxR, cy),
                end = Offset(cx + maxR, cy),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0xFF334155),
                start = Offset(cx, cy - maxR),
                end = Offset(cx, cy + maxR),
                strokeWidth = 1.5f
            )

            // Real Alerts drawn relative to driver position
            for (alert in alerts) {
                val dx = ((alert.longitude - driverLocation.longitude) * 11000f).toFloat()
                val dy = -((alert.latitude - driverLocation.latitude) * 11000f).toFloat()
                val ax = cx + dx
                val ay = cy + dy

                // Hazard outer glow
                drawCircle(
                    color = AlertRed500.copy(alpha = 0.35f),
                    radius = 24f,
                    center = Offset(ax, ay)
                )
                drawCircle(
                    color = AlertRed500,
                    radius = 12f,
                    center = Offset(ax, ay)
                )
            }

            // Driver Car Marker Center (Electric Blue / Cyan)
            drawCircle(
                color = Color(0xFF00D4FF).copy(alpha = 0.35f),
                radius = 36f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xFF00D4FF),
                radius = 16f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White,
                radius = 16f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )
            drawCircle(
                color = Color(0xFF0F172A),
                radius = 7f,
                center = Offset(cx, cy)
            )
        }

        // Route & Speed Badge
        Surface(
            color = Slate900.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Slate800),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Amber400,
                    shape = CircleShape,
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${driverLocation.routeName} • ${driverLocation.speedKmH.roundToInt()} km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helpers for MapLibre Marker Bitmaps
private fun createCarMarkerBitmap(heading: Float = 0f): Bitmap {
    val size = 68
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Electric Blue / Cyan Glow
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3300D4FF")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 32f, glowPaint)

    val midGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#6600E5FF")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 24f, midGlowPaint)

    // Solid Electric Blue Disc
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#00D4FF")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 18f, paint)

    // Crisp White Outer Ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, 18f, borderPaint)

    // Dark Inner Disc
    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 10f, centerPaint)

    // Heading Arrow Pointer in Center
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }
    val path = android.graphics.Path()
    val cx = size / 2f
    val cy = size / 2f
    canvas.save()
    canvas.rotate(heading, cx, cy)
    path.moveTo(cx, cy - 7f)
    path.lineTo(cx + 4.5f, cy + 5f)
    path.lineTo(cx, cy + 2.5f)
    path.lineTo(cx - 4.5f, cy + 5f)
    path.close()
    canvas.drawPath(path, arrowPaint)
    canvas.restore()

    return bitmap
}

private fun createHazardMarkerBitmap(type: String, depthCm: Int): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val colorHex = when (type) {
        "pozo" -> "#E11D48"
        "control_radar" -> "#6366F1"
        "ruta_detonada" -> "#F59E0B"
        "trabajos_obra" -> "#EA580C"
        "corte_obra" -> "#DC2626"
        "agua" -> "#0284C7"
        "neblina" -> "#64748B"
        "animales" -> "#D97706"
        else -> "#E11D48"
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(colorHex)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 18f, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(size / 2f, size / 2f, 18f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (depthCm > 0) 14f else 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val label = when {
        type == "pozo" && depthCm > 0 -> "$depthCm"
        type == "control_radar" -> "R"
        else -> "!"
    }
    canvas.drawText(label, size / 2f, size / 2f + 6f, textPaint)

    return bitmap
}

private fun createSosMarkerBitmap(): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E11D48")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 20f, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, 20f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 14f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("SOS", size / 2f, size / 2f + 5f, textPaint)

    return bitmap
}

private fun createHitchhikeMarkerBitmap(): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 18f, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(size / 2f, size / 2f, 18f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("👍", size / 2f, size / 2f + 6f, textPaint)

    return bitmap
}

private fun createDestinationMarkerBitmap(): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 19f, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, 19f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 16f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("📍", size / 2f, size / 2f + 5f, textPaint)

    return bitmap
}

private fun createFollowedDriverBitmap(): Bitmap {
    val size = 68
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#5522C55E")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 31f, haloPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#22C55E")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 20f, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(size / 2f, size / 2f, 20f, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("🚗", size / 2f, size / 2f + 6f, textPaint)

    return bitmap
}
