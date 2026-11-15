package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
  fun observeActiveAlerts_unauthenticatedUser_returnsSafeEmptyListWithoutCrashing() = runBlocking {
    auth.signOut()
    val repository = RoadAlertRepository(firestore, null)

    val alerts = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeActiveAlerts().first()
    }
    assertTrue("Expected safe empty list fallback on unauthenticated access", alerts.isEmpty())
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

  private companion object {
    const val TEST_USER_EMAIL = "viaalerta_tester@example.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
