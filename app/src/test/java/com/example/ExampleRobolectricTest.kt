package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SeguimePoint
import com.example.data.repository.SeguimeRepository
import com.example.util.OfflineAlertQueue
import com.example.util.PendingOfflineAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VíaAlerta", appName)
  }

  @Test
  fun `offline alert queue persists FIFO items and deletes by localId`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sample1 = PendingOfflineAlert(
      localId = "offline_1",
      type = "pozo",
      depthCm = 22,
      roadName = "RN 3",
      description = "Bache profundo sin señal",
      latitude = -38.7,
      longitude = -62.2,
      heading = 180f,
      directionDesc = "Hacia Sur",
      affectsBothDirections = false,
      estimatedKm = 680,
      reporterName = "Conductor",
      roadCategory = "ruta",
      connectsTowns = "Azul - Bahía Blanca",
      reporterLevel = 2,
      createdAtMillis = 1_760_000_000_000L
    )
    val sample2 = sample1.copy(localId = "offline_2", type = "animales", depthCm = 0)

    val serialized = OfflineAlertQueue.serializeQueue(listOf(sample1, sample2))
    val restored = OfflineAlertQueue.deserializeQueue(serialized)
    assertEquals(2, restored.size)
    assertEquals("offline_1", restored[0].localId)
    assertEquals(22, restored[0].depthCm)
    assertEquals("animales", restored[1].type)

    assertTrue(OfflineAlertQueue.enqueue(context, sample1))
    assertTrue(OfflineAlertQueue.enqueue(context, sample2))
    assertEquals(2, OfflineAlertQueue.count(context))

    OfflineAlertQueue.removeByLocalId(context, "OFFLINE_QUEUED:offline_1")
    val remaining = OfflineAlertQueue.getPending(context)
    assertEquals(1, remaining.size)
    assertEquals("offline_2", remaining[0].localId)
  }

  @Test
  fun `seguime breadcrumbs serialize and deserialize cleanly`() {
    val pts = listOf(
      SeguimePoint(lat = -34.6037, lng = -58.3816, ts = 1_760_000_001_000L),
      SeguimePoint(lat = -34.7000, lng = -58.4000, ts = 1_760_000_015_000L)
    )
    val json = SeguimeRepository.serializePoints(pts)
    val restored = SeguimeRepository.deserializePoints(json)
    assertEquals(2, restored.size)
    assertEquals(-34.6037, restored[0].lat, 0.0001)
    assertEquals(-58.4000, restored[1].lng, 0.0001)
  }
}
