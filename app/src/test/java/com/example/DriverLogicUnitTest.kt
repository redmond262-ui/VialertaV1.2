package com.example

import com.example.data.model.RoadAlert
import com.example.util.DriverLocation
import com.example.util.DriverPreferences
import com.example.util.LocationRadarManager
import com.example.util.RoadRoutingManager
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
}
