package com.example

import com.example.data.model.AppUpdateConfig
import com.example.data.model.FeatureMode
import com.example.data.model.RoadAlert
import com.example.data.model.RoadLevels
import com.example.data.repository.FeatureFlagsRepository
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.SeguimeRepository
import com.example.data.repository.UserRepository
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
        assertFalse(VoiceCopilotManager.isUndoSpeechCommand(listOf("radar de velocidad adelante")))
        assertFalse(VoiceCopilotManager.isUndoSpeechCommand(emptyList()))
    }

    @Test
    fun isDisablePreTripReminderCommand_detectsVoiceOptOutAndIgnoresHazards() {
        assertTrue(VoiceCopilotManager.isDisablePreTripReminderCommand(listOf("desactivar recordatorio")))
        assertTrue(VoiceCopilotManager.isDisablePreTripReminderCommand(listOf("Silenciar chequeo por favor")))
        assertTrue(VoiceCopilotManager.isDisablePreTripReminderCommand(listOf("no me avises más")))
        assertTrue(VoiceCopilotManager.isDisablePreTripReminderCommand(listOf("sacar checklist")))

        assertFalse(VoiceCopilotManager.isDisablePreTripReminderCommand(listOf("pozo en el carril")))
        assertFalse(VoiceCopilotManager.isDisablePreTripReminderCommand(listOf("radar adelante")))
        assertFalse(VoiceCopilotManager.isDisablePreTripReminderCommand(emptyList()))
    }

    @Test
    fun preTripChecklist_respectsTwelveHourGatingAndVehicleAdaptation() {
        val now = 1_750_000_000_000L
        val sixHoursAgo = now - (6L * 60L * 60L * 1000L)
        val thirteenHoursAgo = now - (13L * 60L * 60L * 1000L)

        assertFalse(DriverPreferences.canSpeakPreTripChecklist(enabled = false, lastSpokenMillis = 0L, nowMillis = now))
        assertFalse(DriverPreferences.canSpeakPreTripChecklist(enabled = true, lastSpokenMillis = sixHoursAgo, nowMillis = now))
        assertTrue(DriverPreferences.canSpeakPreTripChecklist(enabled = true, lastSpokenMillis = thirteenHoursAgo, nowMillis = now))

        val autoSpeech = DriverPreferences.buildPreTripChecklistSpeech(DriverPreferences.VEHICLE_AUTO)
        val motoSpeech = DriverPreferences.buildPreTripChecklistSpeech(DriverPreferences.VEHICLE_MOTO)
        val truckSpeech = DriverPreferences.buildPreTripChecklistSpeech(DriverPreferences.VEHICLE_CAMION)

        assertTrue(autoSpeech.contains("cinturón"))
        assertTrue(motoSpeech.contains("casco"))
        assertTrue(truckSpeech.contains("camión"))
    }

    @Test
    fun proConversionCooldownAndMilestones_enforce7dThen20dAfter3Dismissals() {
        val now = 1_750_000_000_000L
        val fiveDaysAgo = now - (5L * DriverPreferences.ONE_DAY_MS)
        val tenDaysAgo = now - (10L * DriverPreferences.ONE_DAY_MS)
        val twentyTwoDaysAgo = now - (22L * DriverPreferences.ONE_DAY_MS)

        // Never dismissed -> not on cooldown
        assertFalse(DriverPreferences.isProCooldownActive(0L, 0, now))
        // Dismissed once 5 days ago -> active (< 7d)
        assertTrue(DriverPreferences.isProCooldownActive(fiveDaysAgo, 1, now))
        // Dismissed once 10 days ago -> expired (>= 7d)
        assertFalse(DriverPreferences.isProCooldownActive(tenDaysAgo, 1, now))
        // Dismissed 3+ times 10 days ago -> still active (< 20d)
        assertTrue(DriverPreferences.isProCooldownActive(tenDaysAgo, 3, now))
        // Dismissed 3+ times 22 days ago -> expired (>= 20d)
        assertFalse(DriverPreferences.isProCooldownActive(twentyTwoDaysAgo, 3, now))

        // Milestones progression
        assertEquals(15, DriverPreferences.nextMilestoneReached(totalAvoided = 16, lastShownMilestone = 0))
        assertEquals(null, DriverPreferences.nextMilestoneReached(totalAvoided = 18, lastShownMilestone = 15))
        assertEquals(25, DriverPreferences.nextMilestoneReached(totalAvoided = 25, lastShownMilestone = 15))
    }

    @Test
    fun referralCodeHelpers_formatNormalizeAndValidateProperly() {
        val code = UserRepository.buildReferralCode("aB12cdEfGhIj")
        assertEquals("VIA-AB12CD", code)
        assertTrue(UserRepository.isValidReferralCodeFormat(code))
        assertEquals("VIA-XYZ987", UserRepository.normalizeReferralCode("  via-xyz 987 "))
        assertTrue(UserRepository.isValidReferralCodeFormat("VIA-XYZ987"))
        assertFalse(UserRepository.isValidReferralCodeFormat("XYZ"))
    }

    @Test
    fun featureFlagsRepository_evaluatesRolloutModesAndSanitizesProperly() {
        // HIDDEN blocks everyone including admin & beta testers
        assertFalse(FeatureFlagsRepository.isModuleEnabled(FeatureMode.HIDDEN, isAdmin = false, isBetaTester = false))
        assertFalse(FeatureFlagsRepository.isModuleEnabled(FeatureMode.HIDDEN, isAdmin = false, isBetaTester = true))
        assertFalse(FeatureFlagsRepository.isModuleEnabled(FeatureMode.HIDDEN, isAdmin = true, isBetaTester = false))

        // BETA enables only admin or beta testers
        assertFalse(FeatureFlagsRepository.isModuleEnabled(FeatureMode.BETA, isAdmin = false, isBetaTester = false))
        assertTrue(FeatureFlagsRepository.isModuleEnabled(FeatureMode.BETA, isAdmin = false, isBetaTester = true))
        assertTrue(FeatureFlagsRepository.isModuleEnabled(FeatureMode.BETA, isAdmin = true, isBetaTester = false))

        // PUBLIC enables everyone including guests
        assertTrue(FeatureFlagsRepository.isModuleEnabled(FeatureMode.PUBLIC, isAdmin = false, isBetaTester = false))
        assertTrue(FeatureFlagsRepository.isModuleEnabled(FeatureMode.PUBLIC, isAdmin = false, isBetaTester = true))

        // sanitizeMode normalizes valid modes and falls back to BETA
        assertEquals(FeatureMode.HIDDEN, FeatureFlagsRepository.sanitizeMode(" HIDDEN "))
        assertEquals(FeatureMode.PUBLIC, FeatureFlagsRepository.sanitizeMode("public"))
        assertEquals(FeatureMode.BETA, FeatureFlagsRepository.sanitizeMode("unknown_value"))
        assertEquals(FeatureMode.BETA, FeatureFlagsRepository.sanitizeMode(null))
    }

    @Test
    fun roadLevels_mapsAllSixTiersAppliesDiscountsAndFormatsPioneerShare() {
        assertEquals(1, RoadLevels.forXp(0).level)
        assertEquals(2, RoadLevels.forXp(300).level)
        assertEquals(3, RoadLevels.forXp(1000).level)
        assertEquals(4, RoadLevels.forXp(2500).level)
        assertEquals(5, RoadLevels.forXp(6000).level)
        assertEquals(6, RoadLevels.forXp(15000).level)

        // Level 1 (0% dto), Level 3 (10% dto), Level 5 (25% dto)
        assertEquals(600, RoadLevels.discountedPointsCost(600, xpTotal = 150))
        assertEquals(540, RoadLevels.discountedPointsCost(600, xpTotal = 1200))
        assertEquals(450, RoadLevels.discountedPointsCost(600, xpTotal = 7000))

        // Level 4+ gets double resolve vote weight, Level 5+ gets auto-beta
        assertEquals(1, RoadLevels.forXp(1200).resolveVoteWeight)
        assertEquals(2, RoadLevels.forXp(2600).resolveVoteWeight)
        assertFalse(RoadLevels.forXp(2600).autoBetaAccess)
        assertTrue(RoadLevels.forXp(6200).autoBetaAccess)
        assertTrue(RoadLevels.forXp(16000).freeLegendProEligible)

        // Progress & remaining XP
        assertEquals(150, RoadLevels.xpRemainingForNextLevel(150))
        assertEquals(0.5f, RoadLevels.progressToNextLevel(150), 0.01f)
        assertEquals(0, RoadLevels.xpRemainingForNextLevel(20000))
        assertEquals(1f, RoadLevels.progressToNextLevel(20000), 0.01f)

        val share = RoadLevels.buildPioneerShareText(
            driverName = "Juancho",
            xpTotal = 2800,
            alertsCreated = 25,
            confirmedHelps = 18,
            alertsResolved = 9,
            referralCode = "VIA-JUAN12"
        )
        assertTrue(share.contains("Patrulla Comunitaria"))
        assertTrue(share.contains("VIA-JUAN12"))
        assertTrue(share.contains("25"))
        assertTrue(share.contains("9"))
    }

    @Test
    fun alertExpiration_distinguishesTemporaryVsStructuralHazards() {
        val now = 1_760_000_000_000L

        assertTrue(RoadAlertRepository.isTemporaryHazardType("neblina"))
        assertTrue(RoadAlertRepository.isTemporaryHazardType("animales"))
        assertTrue(RoadAlertRepository.isTemporaryHazardType("vehiculo_averiado"))
        assertTrue(RoadAlertRepository.isTemporaryHazardType("agua"))
        assertFalse(RoadAlertRepository.isTemporaryHazardType("pozo"))
        assertFalse(RoadAlertRepository.isTemporaryHazardType("control_radar"))
        assertFalse(RoadAlertRepository.isTemporaryHazardType("ruta_detonada"))

        val fogExpiry = RoadAlertRepository.computeInitialExpiresAtMillis("neblina", now)
        assertEquals(now + 3L * 60L * 60L * 1000L, fogExpiry)
        assertEquals(0L, RoadAlertRepository.computeInitialExpiresAtMillis("pozo", now))

        val activeFog = RoadAlert(id = "f1", type = "neblina", expiresAtMillis = now + 60_000L)
        val expiredFog = RoadAlert(id = "f2", type = "neblina", expiresAtMillis = now - 1_000L)
        val structuralPothole = RoadAlert(id = "p1", type = "pozo", expiresAtMillis = 0L)

        assertFalse(RoadAlertRepository.isAlertExpired(activeFog, now))
        assertTrue(RoadAlertRepository.isAlertExpired(expiredFog, now))
        assertFalse(RoadAlertRepository.isAlertExpired(structuralPothole, now))
    }

    @Test
    fun voiceConfirmations_handlePunctuationAndNaturalArgentineResponsesWithoutFalseNegation() {
        assertEquals("si confirmo es real", VoiceCopilotManager.normalizeSpeech("¡Sí, confirmo! Es real."))

        assertTrue(VoiceCopilotManager.isAffirmativeSpeechCommand(listOf("Ok, confirmo.")))
        assertTrue(VoiceCopilotManager.isAffirmativeSpeechCommand(listOf("Sí, sigue ahí")))
        assertTrue(VoiceCopilotManager.isAffirmativeSpeechCommand(listOf("dale confirmado")))
        assertTrue(VoiceCopilotManager.isAffirmativeSpeechCommand(listOf("es real estaba en la mano")))
        assertTrue(VoiceCopilotManager.isAffirmativeSpeechCommand(listOf("central")))

        // Crucially, "confirmo" and "bueno" end in "no" and MUST NOT match isNegativeSpeechCommand!
        assertFalse(VoiceCopilotManager.isNegativeSpeechCommand(listOf("confirmo")))
        assertFalse(VoiceCopilotManager.isNegativeSpeechCommand(listOf("bueno")))
        assertFalse(VoiceCopilotManager.isNegativeSpeechCommand(listOf("sí, sigue")))

        assertTrue(VoiceCopilotManager.isNegativeSpeechCommand(listOf("No, ya no está")))
        assertTrue(VoiceCopilotManager.isNegativeSpeechCommand(listOf("lo taparon")))
        assertTrue(VoiceCopilotManager.isNegativeSpeechCommand(listOf("falsa alarma")))
    }

    @Test
    fun antiFatigueEvaluator_triggersAtConfiguredIntervalCooldownsAndResetsAfterRest() {
        val base = 1_760_000_000_000L
        val oneMin = 60_000L

        // Disabled (0 min) -> never triggers
        val disabled = DriverPreferences.evaluateAntiFatigue(
            intervalMinutes = 0,
            speedKmH = 95f,
            isRouteActive = true,
            driveStartMs = base,
            stationarySinceMs = 0L,
            lastAlertMs = 0L,
            nowMillis = base + 180 * oneMin
        )
        assertFalse(disabled.shouldAlert)

        // Driving 125 min continuously with 120-min threshold -> triggers!
        val triggered = DriverPreferences.evaluateAntiFatigue(
            intervalMinutes = 120,
            speedKmH = 90f,
            isRouteActive = true,
            driveStartMs = base,
            stationarySinceMs = 0L,
            lastAlertMs = 0L,
            nowMillis = base + 125 * oneMin
        )
        assertTrue(triggered.shouldAlert)
        assertEquals(125, triggered.continuousMinutes)
        assertEquals(base + 125 * oneMin, triggered.nextLastAlertMs)

        // 20 minutes later (< 45 min repeat cooldown) -> does not re-trigger yet
        val cooldownBlocked = DriverPreferences.evaluateAntiFatigue(
            intervalMinutes = 120,
            speedKmH = 90f,
            isRouteActive = true,
            driveStartMs = triggered.nextDriveStartMs,
            stationarySinceMs = 0L,
            lastAlertMs = triggered.nextLastAlertMs,
            nowMillis = base + 145 * oneMin
        )
        assertFalse(cooldownBlocked.shouldAlert)

        // Resting stationary >= 12 minutes resets driving timer cleanly
        val afterRest = DriverPreferences.evaluateAntiFatigue(
            intervalMinutes = 120,
            speedKmH = 0f,
            isRouteActive = true,
            driveStartMs = base,
            stationarySinceMs = base + 126 * oneMin,
            lastAlertMs = triggered.nextLastAlertMs,
            nowMillis = base + 140 * oneMin
        )
        assertFalse(afterRest.shouldAlert)
        assertEquals(0L, afterRest.nextDriveStartMs)
    }

    @Test
    fun voiceExtraCommands_detectRemainingDistanceAndHandsFreeToggles() {
        assertTrue(VoiceCopilotManager.isRemainingDistanceCommand(listOf("¿Cuánto falta para llegar?")))
        assertTrue(VoiceCopilotManager.isRemainingDistanceCommand(listOf("cuanto falta")))
        assertTrue(VoiceCopilotManager.isRemainingDistanceCommand(listOf("hora de llegada")))
        assertFalse(VoiceCopilotManager.isRemainingDistanceCommand(listOf("bache en ruta")))

        assertTrue(VoiceCopilotManager.isDisableHandsFreeCommand(listOf("Apagar manos libres")))
        assertTrue(VoiceCopilotManager.isDisableHandsFreeCommand(listOf("desactivar micrófono")))
        assertFalse(VoiceCopilotManager.isDisableHandsFreeCommand(listOf("activar manos libres")))

        assertTrue(VoiceCopilotManager.isEnableHandsFreeCommand(listOf("Encender manos libres")))
        assertTrue(VoiceCopilotManager.isEnableHandsFreeCommand(listOf("activar micrófono")))
        assertFalse(VoiceCopilotManager.isEnableHandsFreeCommand(listOf("apagar manos libres")))
    }

    @Test
    fun seguimeHelpers_generateUnguessableTokensAndWhatsAppShareCopyWithOfflineNotice() {
        val token1 = SeguimeRepository.generateSessionToken()
        val token2 = SeguimeRepository.generateSessionToken()
        assertTrue(token1.startsWith("seguime_"))
        assertTrue(token1.length >= 24)
        assertTrue(token1 != token2)

        val url = SeguimeRepository.buildSeguimeUrl(token1)
        assertEquals("https://gen-lang-client-0699193083.web.app/seguime.html?s=$token1", url)

        val msg = SeguimeRepository.buildWhatsAppShareMessage(
            driverName = "Carlos",
            vehicleSummary = "Toyota Hilux (AE 456 BC)",
            destinationTitle = "Mar del Plata",
            liveUrl = url
        )
        assertEquals(8L * 60L * 60L * 1000L, SeguimeRepository.MAX_SESSION_DURATION_MS)
        assertTrue(msg.contains("Carlos"))
        assertTrue(msg.contains("Toyota Hilux"))
        assertTrue(msg.contains("Mar del Plata"))
        assertTrue(msg.contains(url))
        assertTrue(msg.contains("caduca automáticamente"))
        assertTrue(msg.contains("sin señal 4G/5G"))

        assertEquals(
            token1,
            SeguimeRepository.parseSessionIdFromText("viaalerta://seguime?s=$token1")
        )
        assertEquals(
            token1,
            SeguimeRepository.parseSessionIdFromText("https://gen-lang-client-0699193083.web.app/seguime.html?s=$token1")
        )
        assertEquals(token1, SeguimeRepository.parseSessionIdFromText(token1))
        assertEquals(null, SeguimeRepository.parseSessionIdFromText("viaalerta://seguime?s=corto"))
        assertEquals(null, SeguimeRepository.parseSessionIdFromText(""))
    }

    @Test
    fun routeAlternativesFilter_dropsForcedLongDetoursAndLabelsSingleDirectRoute() {
        val mainRoute = RoadRoutingManager.RouteOption(
            index = 0,
            label = "Opción 1",
            distanceMeters = 50_000.0,
            durationSeconds = 2_400.0,
            routePoints = listOf(Pair(-32.0, -60.0), Pair(-32.25, -60.25), Pair(-32.5, -60.5))
        )
        val exaggeratedTownDetour = RoadRoutingManager.RouteOption(
            index = 1,
            label = "Opción 2",
            distanceMeters = 78_000.0, // > 1.28x primary
            durationSeconds = 4_200.0,
            routePoints = listOf(Pair(-32.0, -60.0), Pair(-32.3, -60.05), Pair(-32.5, -60.5))
        )
        val genuineAlternative = RoadRoutingManager.RouteOption(
            index = 2,
            label = "Opción 3",
            distanceMeters = 55_000.0, // <= 1.28x primary and distinct corridor
            durationSeconds = 2_700.0,
            routePoints = listOf(Pair(-32.0, -60.0), Pair(-32.25, -60.40), Pair(-32.5, -60.5))
        )

        val singleFiltered = RoadRoutingManager.filterGenuineAlternativeRoutes(
            listOf(mainRoute, exaggeratedTownDetour)
        )
        assertEquals(1, singleFiltered.size)
        assertEquals("Ruta directa", singleFiltered.first().label)

        val multiFiltered = RoadRoutingManager.filterGenuineAlternativeRoutes(
            listOf(mainRoute, exaggeratedTownDetour, genuineAlternative)
        )
        assertEquals(2, multiFiltered.size)
        assertEquals("Más rápida", multiFiltered[0].label)
        assertEquals("Alternativa 2", multiFiltered[1].label)
    }

    @Test
    fun voiceAntiEchoAndExpandedTravelCommands_behaveAccurately() {
        val now = 100_000L
        val recentUtterances = listOf(
            (now - 3_000L) to VoiceCopilotManager.normalizeSpeech(
                "¡Atención! Pozo profundo de 18 centímetros en tu carril a 300 metros. Reduzca la velocidad."
            )
        )

        assertTrue(
            VoiceCopilotManager.isLikelySelfEcho(
                listOf("pozo profundo de 18 centimetros"),
                recentUtterances,
                now
            )
        )
        assertTrue(
            VoiceCopilotManager.isLikelySelfEcho(
                listOf("no te entendi podes decir pozo"),
                recentUtterances,
                now
            )
        )
        assertFalse(
            VoiceCopilotManager.isLikelySelfEcho(
                listOf("a cuanto queda Rosario"),
                recentUtterances,
                now
            )
        )
        assertFalse(
            VoiceCopilotManager.isLikelySelfEcho(
                listOf("radar adelante"),
                recentUtterances,
                now
            )
        )

        assertTrue(VoiceCopilotManager.isRemainingDistanceCommand(listOf("cuánto tiempo falta")))
        assertTrue(VoiceCopilotManager.isRemainingDistanceCommand(listOf("cuántos kilómetros faltan")))
        assertTrue(VoiceCopilotManager.isRemainingDistanceCommand(listOf("a qué hora llegamos")))
        assertFalse(VoiceCopilotManager.isRemainingDistanceCommand(listOf("cuánto falta para Rosario")))

        assertEquals("rosario", VoiceCopilotManager.extractDistancePlaceQuery(listOf("¿A cuánto queda Rosario?")))
        assertEquals("villa maria", VoiceCopilotManager.extractDistancePlaceQuery(listOf("cuánto falta para Villa María")))
        assertEquals(null, VoiceCopilotManager.extractDistancePlaceQuery(listOf("cuánto falta para llegar")))

        assertTrue(VoiceCopilotManager.isWhereAmICommand(listOf("¿Dónde estoy?")))
        assertTrue(VoiceCopilotManager.isWhereAmICommand(listOf("a qué velocidad voy")))
        assertTrue(VoiceCopilotManager.isRouteHazardsAheadCommand(listOf("¿Hay peligros adelante?")))
        assertTrue(VoiceCopilotManager.isRouteHazardsAheadCommand(listOf("cómo está la ruta")))

        assertEquals(false, VoiceCopilotManager.extractFuelQueryPreference(listOf("estación de servicio cercana")))
        assertEquals(true, VoiceCopilotManager.extractFuelQueryPreference(listOf("dónde hay GNC adelante")))
        assertEquals(null, VoiceCopilotManager.extractFuelQueryPreference(listOf("reportar pozo en ruta")))

        assertTrue(VoiceCopilotManager.isStopNavigationCommand(listOf("detener navegación")))
        assertTrue(VoiceCopilotManager.isRecalculateRouteCommand(listOf("recalcular ruta")))
    }

    @Test
    fun appUpdatePrompt_validatesUrlsVersionsAndDismissalRules() {
        assertTrue(FeatureFlagsRepository.isValidUpdateDownloadUrl("https://drive.google.com/file/d/12345/view"))
        assertTrue(FeatureFlagsRepository.isValidUpdateDownloadUrl("https://github.com/user/viaalerta/releases/latest/download/app.apk"))
        assertFalse(FeatureFlagsRepository.isValidUpdateDownloadUrl(""))
        assertFalse(FeatureFlagsRepository.isValidUpdateDownloadUrl("ftp://example.com/app.apk"))
        assertFalse(FeatureFlagsRepository.isValidUpdateDownloadUrl("https://localhost"))
        assertFalse(FeatureFlagsRepository.isValidUpdateDownloadUrl("https://example.com/my app.apk"))

        val activeUpdate = AppUpdateConfig(
            enabled = true,
            mandatory = false,
            latestVersionCode = 4,
            latestVersionName = "2.3",
            message = "Nueva versión lista",
            downloadUrl = "https://drive.google.com/file/d/12345/view"
        )

        // Installed v3 < published v4 -> should prompt
        assertTrue(FeatureFlagsRepository.shouldPromptAppUpdate(3, activeUpdate, dismissedVersionCode = 0))
        // Installed v4 == published v4 -> should NOT prompt
        assertFalse(FeatureFlagsRepository.shouldPromptAppUpdate(4, activeUpdate, dismissedVersionCode = 0))
        // Installed v5 > published v4 -> should NOT prompt
        assertFalse(FeatureFlagsRepository.shouldPromptAppUpdate(5, activeUpdate, dismissedVersionCode = 0))
        // Disabled notice -> should NOT prompt
        assertFalse(FeatureFlagsRepository.shouldPromptAppUpdate(3, activeUpdate.copy(enabled = false), dismissedVersionCode = 0))
        // Invalid URL -> should NOT prompt
        assertFalse(FeatureFlagsRepository.shouldPromptAppUpdate(3, activeUpdate.copy(downloadUrl = "not-a-url"), dismissedVersionCode = 0))
        // Optional update dismissed for v4 -> skipped for v4, but prompts again if v5 is published
        assertFalse(FeatureFlagsRepository.shouldPromptAppUpdate(3, activeUpdate, dismissedVersionCode = 4))
        assertTrue(FeatureFlagsRepository.shouldPromptAppUpdate(3, activeUpdate.copy(latestVersionCode = 5), dismissedVersionCode = 4))
        // Mandatory update ignores previous optional dismissal
        assertTrue(FeatureFlagsRepository.shouldPromptAppUpdate(3, activeUpdate.copy(mandatory = true), dismissedVersionCode = 4))
    }

    @Test
    fun seguimeOfflineSmsMessage_includesCoordinatesMapsLinkAndLiveUrl() {
        val url = SeguimeRepository.buildSeguimeUrl("seguime_abc1234567890xyz")
        val sms = SeguimeRepository.buildOfflineSmsMessage(
            driverName = "Juan Perez",
            vehicleSummary = "Hilux (AA123BB)",
            destinationTitle = "Mar del Plata",
            latitude = -35.12345,
            longitude = -59.54321,
            liveUrl = url
        )
        assertTrue(sms.contains("Juan Perez"))
        assertTrue(sms.contains("Hilux (AA123BB)"))
        assertTrue(sms.contains("Mar del Plata"))
        assertTrue(sms.contains("-35.12345,-59.54321"))
        assertTrue(sms.contains("https://maps.google.com/?q=-35.12345,-59.54321"))
        assertTrue(sms.contains(url))
    }
}

