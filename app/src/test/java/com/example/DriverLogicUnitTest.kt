package com.example

import com.example.data.model.RoadAlert
import com.example.util.DriverLocation
import com.example.util.DriverPreferences
import com.example.util.LocationRadarManager
import com.example.util.RoadRoutingManager
import com.example.util.VoiceCopilotManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverLogicUnitTest {

    @Test
    fun projectDriverOntoRoute_calculatesRemainingDistanceAndOffRouteCorrectly() {
        val route = listOf(
            Pair(-34.6000, -58.3800),
            Pair(-34.6100, -58.3800),
            Pair(-34.6200, -58.3800)
        )

        // Driver near middle point (-34.6100, -58.3800) heading south
        val onRouteProgress = RoadRoutingManager.projectDriverOntoRoute(-34.6101, -58.3801, route)
        assertTrue(onRouteProgress.crossTrackDistanceMeters < 50.0)
        // Roughly 1.1 km remaining to -34.6200
        assertTrue(onRouteProgress.remainingDistanceMeters in 900.0..1300.0)
        assertTrue(onRouteProgress.trimmedRoutePoints.size >= 2)

        // Driver ~500m east off-route
        val offRouteProgress = RoadRoutingManager.projectDriverOntoRoute(-34.6100, -58.3740, route)
        assertTrue(offRouteProgress.crossTrackDistanceMeters > 400.0)
    }

    @Test
    fun isAlertRelevantToDriver_filtersOpposingLaneAndBehindAlerts() {
        // Driver heading South (180 deg) from -34.600 to -34.610
        val driver = DriverLocation(
            latitude = -34.6000,
            longitude = -58.3800,
            speedKmH = 85f,
            heading = 180f,
            hasGpsFix = true
        )

        // Same lane alert ahead (500m South, heading 180)
        val sameLaneAhead = RoadAlert(
            id = "a1",
            type = "pozo",
            latitude = -34.6045,
            longitude = -58.3800,
            heading = 180f,
            affectsBothDirections = false
        )
        assertTrue(LocationRadarManager.isAlertRelevantToDriver(sameLaneAhead, driver))

        // Opposing lane alert ahead (500m South, heading 0 North)
        val oppositeLaneAhead = sameLaneAhead.copy(id = "a2", heading = 0f, affectsBothDirections = false)
        assertFalse(LocationRadarManager.isAlertRelevantToDriver(oppositeLaneAhead, driver))

        // Central / both-directions alert ahead (heading 0, affectsBothDirections = true)
        val centralAhead = oppositeLaneAhead.copy(id = "a3", affectsBothDirections = true)
        assertTrue(LocationRadarManager.isAlertRelevantToDriver(centralAhead, driver))

        // Same-heading alert BEHIND the driver (North of driver)
        val behindDriver = sameLaneAhead.copy(id = "a4", latitude = -34.5950)
        assertFalse(LocationRadarManager.isAlertRelevantToDriver(behindDriver, driver))
    }

    @Test
    fun canConfirmAlert_preventsSelfConfirmFarmingWithinFourHoursAndAllowsReturnTrip() {
        val now = 1_700_000_000_000L
        val twoMinutesAgo = now - 120_000L
        val fiveHoursAgo = now - (5L * 60L * 60L * 1000L)

        // Author cannot confirm their own recent alert (2 min old)
        assertFalse(
            DriverPreferences.canConfirmAlert(
                context = null,
                alertId = "alert_own",
                reporterUid = "driver_1",
                currentUid = "driver_1",
                alertCreatedMillis = twoMinutesAgo,
                nowMillis = now
            )
        )

        // Author CAN confirm after 5 hours (legitimate return trip)
        assertTrue(
            DriverPreferences.canConfirmAlert(
                context = null,
                alertId = "alert_own",
                reporterUid = "driver_1",
                currentUid = "driver_1",
                alertCreatedMillis = fiveHoursAgo,
                nowMillis = now
            )
        )

        // Another driver can immediately confirm
        assertTrue(
            DriverPreferences.canConfirmAlert(
                context = null,
                alertId = "alert_other",
                reporterUid = "driver_1",
                currentUid = "driver_2",
                alertCreatedMillis = twoMinutesAgo,
                nowMillis = now
            )
        )
    }

    @Test
    fun formatSpeedLabel_roundsProperly() {
        assertEquals(89, DriverPreferences.formatSpeedLabel(88.7f))
        assertEquals(88, DriverPreferences.formatSpeedLabel(88.4f))
        assertEquals(0, DriverPreferences.formatSpeedLabel(-1f))
    }

    @Test
    fun buildLateralCorridorWaypoints_generatesSymmetricCorridors() {
        // Straight North-South route in Argentina (e.g. Ruta 9 segment)
        val waypoints = RoadRoutingManager.buildLateralCorridorWaypoints(
            lat1 = -34.0,
            lon1 = -59.0,
            lat2 = -33.0,
            lon2 = -59.0
        )
        assertEquals(2, waypoints.size)
        // Midpoint latitude is -33.5; lateral offsets should sit on opposite sides (east vs west)
        assertTrue(waypoints[0].second < -59.0 && waypoints[1].second > -59.0 ||
            waypoints[0].second > -59.0 && waypoints[1].second < -59.0)
        assertEquals(-33.5, waypoints[0].first, 0.001)
        assertEquals(-33.5, waypoints[1].first, 0.001)
    }

    @Test
    fun buildFormattedGpsDescription_includesDistanceTownAndCoordinates() {
        val ruralDesc = RoadRoutingManager.buildFormattedGpsDescription(
            routePrefix = "RN 9",
            townName = "San Pedro",
            streetName = "Ruta Nacional 9",
            distanceToTownMeters = 6400.0,
            coordsText = "-33.67950, -59.66720"
        )
        assertTrue(ruralDesc.contains("RN 9"))
        assertTrue(ruralDesc.contains("San Pedro"))
        assertTrue(ruralDesc.contains("km"))
        assertTrue(ruralDesc.contains("-33.67950, -59.66720"))

        val insideTownDesc = RoadRoutingManager.buildFormattedGpsDescription(
            routePrefix = "",
            townName = "Chivilcoy",
            streetName = null,
            distanceToTownMeters = 450.0,
            coordsText = "-34.89500, -60.01700"
        )
        assertEquals("GPS: En Chivilcoy (-34.89500, -60.01700)", insideTownDesc)

        val fallbackCoordsOnly = RoadRoutingManager.buildFormattedGpsDescription(
            routePrefix = "",
            townName = null,
            streetName = null,
            distanceToTownMeters = 0.0,
            coordsText = "-35.12345, -60.54321"
        )
        assertEquals("GPS: (-35.12345, -60.54321)", fallbackCoordsOnly)
    }

    @Test
    fun isUndoSpeechCommand_acceptsSpanishCancelPhrasesAndRejectsNormalHazards() {
        assertTrue(VoiceCopilotManager.isUndoSpeechCommand(listOf("Cancelar")))
        assertTrue(VoiceCopilotManager.isUndoSpeechCommand(listOf("no era nada", "anular alerta")))
        assertTrue(VoiceCopilotManager.isUndoSpeechCommand(listOf("me equivoqué")))
        assertTrue(VoiceCopilotManager.isUndoSpeechCommand(listOf("falsa alarma sacalo")))
        assertTrue(VoiceCopilotManager.isUndoSpeechCommand(listOf("fue un error")))

        assertFalse(VoiceCopilotManager.isUndoSpeechCommand(listOf("bache profundo en mi mano")))
        assertFalse(VoiceCopilotManager.isUndoSpeechCommand(listOf("control policial adelante")))
        assertFalse(VoiceCopilotManager.isUndoSpeechCommand(emptyList()))
    }
}
