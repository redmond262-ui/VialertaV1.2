package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ViaAlertaRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAlert_authenticatedUser_createsDocumentAndReturnsId() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val repository = RoadAlertRepository(firestore, null)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createAlert(
        type = "pozo",
        depthCm = 20,
        roadName = "Ruta 2 Km 140",
        description = "Bache muy profundo en carril rápido",
        latitude = -35.5,
        longitude = -58.0,
        reporterName = "Conductor Test"
      )
    }

    assertTrue(result.isSuccess)
    val alertId = result.getOrThrow()
    assertTrue(alertId.isNotBlank())
  }

  @Test
  fun observeActiveAlerts_authenticatedUser_receivesEmittedAlerts() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val repository = RoadAlertRepository(firestore, null)

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createAlert(
        type = "ruta_detonada",
        roadName = "Ruta 40 Km 100",
        description = "Asfalto destruido",
        latitude = -40.1,
        longitude = -71.2,
        reporterName = "Viajero"
      )
    }
    val alertId = createResult.getOrThrow()

    val alerts = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeActiveAlerts().first { list -> list.any { it.id == alertId } }
    }
    assertTrue(alerts.any { it.id == alertId })
  }

  @Test
  fun createSos_authenticatedUser_createsSosRequestSuccessfully() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val sosRepository = SosRepository(firestore, null)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      sosRepository.createSos(
        ownerName = "Juan Perez",
        ownerDni = "30123456",
        vehicleModel = "Fiat Cronos Rojo",
        vehicleColor = "Rojo",
        licensePlate = "AF 123 CD",
        assistanceType = "pinchadura",
        routeDescription = "Ruta 2 Km 180",
        latitude = -36.0,
        longitude = -58.2,
        phone = "+5491122334455"
      )
    }

    assertTrue(result.isSuccess)
    val sosId = result.getOrThrow()
    assertNotNull(sosId)
  }

  @Test
  fun createHitchhike_authenticatedUser_createsHitchhikeRequest() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val hitchhikeRepository = HitchhikeRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      hitchhikeRepository.createHitchhike(
        userName = "Laura Docente",
        userDni = "28999888",
        roleCategory = "docente",
        pickupPoint = "Garita Escuela Rural 14",
        destination = "Dolores",
        latitude = -36.3,
        longitude = -57.7
      )
    }

    assertTrue(result.isSuccess)
    val hitchhikeId = result.getOrThrow()
    assertNotNull(hitchhikeId)
  }

  @Test
  fun observeActiveAlerts_unauthenticatedUser_receivesActiveAlertsReadOnlyAndBlocksWrites() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val repository = RoadAlertRepository(firestore, null)
    val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createAlert(
        type = "control_radar",
        roadName = "Ruta 9 Km 85",
        description = "Radar fijo de velocidad 110 km/h",
        latitude = -34.15,
        longitude = -59.05,
        reporterName = "Conductor Invitador"
      )
    }.getOrThrow()

    auth.signOut()

    val alerts = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeActiveAlerts().first { list -> list.any { it.id == createdId } }
    }
    assertTrue("Guest should read active alerts in real time", alerts.any { it.id == createdId && it.type == "control_radar" })

    val writeAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createAlert(
        type = "pozo",
        roadName = "Ruta 9",
        description = "Intento invitado",
        latitude = -34.2,
        longitude = -59.1,
        reporterName = "Invitado"
      )
    }
    assertTrue("Guest write must be rejected", writeAttempt.isFailure)
  }

  @Test
  fun referrals_redeemAndClaimFlow_awardsPointsXpAndProDaysToBothUsers() = runBlocking {
    val userRepo = UserRepository(firestore)

    // 1. User A signs in and initializes profile + referral code
    val uidA = signInTestUser(REFERRER_EMAIL)
    val grantA = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.grantWelcomeProTrialIfEligible(uidA) }
    assertTrue(grantA.isSuccess)
    val profileA = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.getUserProfile(uidA) }
    assertNotNull(profileA)
    val codeA = profileA!!.referralCode
    assertTrue(UserRepository.isValidReferralCodeFormat(codeA))

    // 2. User B signs in, initializes profile, and redeems User A's code
    val uidB = signInTestUser(REFEREE_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.grantWelcomeProTrialIfEligible(uidB) }
    val redeemRes = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.redeemReferralCode(codeA) }
    assertTrue("Referee redemption failed: ${redeemRes.exceptionOrNull()}", redeemRes.isSuccess)

    val profileB = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.getUserProfile(uidB) }
    assertNotNull(profileB)
    assertEquals(UserRepository.REFERRAL_BONUS_POINTS, profileB!!.pointsRuta)
    assertEquals(UserRepository.REFERRAL_BONUS_POINTS, profileB.xpTotal)
    assertEquals(codeA, profileB.referredByCode)

    // 3. Re-redeeming must fail
    val secondRedeem = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.redeemReferralCode(codeA) }
    assertTrue(secondRedeem.isFailure)

    // 4. User A logs back in and claims pending referral reward
    signInTestUser(REFERRER_EMAIL)
    val claimRes = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.claimPendingReferralRewards(uidA) }
    assertTrue("Claim failed: ${claimRes.exceptionOrNull()}", claimRes.isSuccess)
    assertEquals(1, claimRes.getOrThrow())

    val updatedA = withTimeout(DEFAULT_TIMEOUT_MS) { userRepo.getUserProfile(uidA) }
    assertNotNull(updatedA)
    assertEquals(UserRepository.REFERRAL_BONUS_POINTS, updatedA!!.pointsRuta)
    assertEquals(UserRepository.REFERRAL_BONUS_POINTS, updatedA.xpTotal)
    assertEquals(1, updatedA.referralCount)
  }

  @Test
  fun billingConfig_nonAdminCannotOverwriteButCanReadDefaultOrStoredConfig() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val billingRepo = BillingConfigRepository(firestore)

    val saveAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
      billingRepo.saveBillingConfig(
        mercadoPagoAlias = "hacker.alias.mp",
        proPriceArs = 1,
        paymentLinkUrl = "https://example.com"
      )
    }
    assertTrue("Non-admin write to app_config/billing must fail", saveAttempt.isFailure)
    val firestoreErr = saveAttempt.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)

    val currentConfig = withTimeout(FLOW_TIMEOUT_MS) {
      billingRepo.observeBillingConfig().first()
    }
    assertEquals("vialerta.oficial.mp", currentConfig.mercadoPagoAlias)
    assertEquals(5000, currentConfig.proPriceArs)
  }

  @Test
  fun baulVial_createObserveAndSubmitClaim_succeeds() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val baulRepo = BaulVialRepository(firestore)

    val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      baulRepo.createItem(
        type = "encontrado",
        category = "patente",
        title = "Patente hallada en banquina",
        publicDescription = "Chapa nueva terminada en 45",
        roadName = "Ruta 41 Km 120",
        latitude = -35.1,
        longitude = -59.3,
        publisherName = "Carlos",
        verificationQuestion = "¿Qué dos letras figuran al inicio?",
        safeDropoffPlace = "YPF Navarro"
      )
    }
    assertTrue(createRes.isSuccess)
    val itemId = createRes.getOrThrow()

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      baulRepo.observeItems().first { list -> list.any { it.id == itemId } }
    }
    assertTrue(observed.any { it.id == itemId && it.claimStatus == "sin_reclamo" })

    val claimRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      baulRepo.submitVerificationClaim(
        itemId = itemId,
        claimantName = "Maria",
        answer = "AF",
        contactPhone = "+5491122334455"
      )
    }
    assertTrue(claimRes.isSuccess)

    val approvedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      baulRepo.reviewClaim(
        itemId = itemId,
        approved = true,
        publisherContactToShare = "+5491199887766"
      )
    }
    assertTrue(approvedRes.isSuccess)
  }

  @Test
  fun featureFlags_nonAdminCannotWriteAndReadsDefaultBetaModes() = runBlocking {
    signInTestUser(TEST_USER_EMAIL)
    val flagsRepo = FeatureFlagsRepository(firestore, null)

    val saveAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
      flagsRepo.saveFeatureFlags(
        com.example.data.model.AppFeatureFlags(
          aventonMode = com.example.data.model.FeatureMode.PUBLIC,
          baulVialMode = com.example.data.model.FeatureMode.PUBLIC
        )
      )
    }
    assertTrue("Non-admin write to app_config/features must fail", saveAttempt.isFailure)
    val firestoreErr = saveAttempt.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)

    val currentFlags = withTimeout(FLOW_TIMEOUT_MS) {
      flagsRepo.observeFeatureFlags().first()
    }
    assertEquals(com.example.data.model.FeatureMode.BETA, currentFlags.aventonMode)
    assertEquals(com.example.data.model.FeatureMode.BETA, currentFlags.baulVialMode)
  }

  @Test
  fun userBetaTester_nonAdminCannotSelfPromoteViaFirestoreUpdate() = runBlocking {
    val uid = signInTestUser(TEST_USER_EMAIL)
    val userRepo = UserRepository(firestore)
    val grantRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      userRepo.grantWelcomeProTrialIfEligible(uid)
    }
    assertTrue("Initial profile creation should succeed: ${grantRes.exceptionOrNull()}", grantRes.isSuccess)

    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(uid).update("isBetaTester", true).await()
      }
      fail("Non-admin user must NOT be able to self-promote isBetaTester = true")
    } catch (e: Exception) {
      val firestoreErr = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
    }
  }

  private companion object {
    const val TEST_USER_EMAIL = "viaalerta_tester@example.com"
    const val REFERRER_EMAIL = "viaalerta_referrer@example.com"
    const val REFEREE_EMAIL = "viaalerta_referee@example.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}

