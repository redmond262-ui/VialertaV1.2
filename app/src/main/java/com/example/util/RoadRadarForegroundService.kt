package com.example.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.model.UserProfile
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RoadRadarForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var radarMonitoringJob: Job? = null
    private var profileJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var cachedUserProfile: UserProfile? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        startInForeground()
        acquireWakeLock()
        startBackgroundRadarLoop()
        return START_STICKY
    }

    private fun stopServiceCleanly() {
        ViaAlertaRadarHolder.isForegroundServiceRunning.value = false
        radarMonitoringJob?.cancel()
        profileJob?.cancel()
        releaseWakeLock()
        try {
            ViaAlertaRadarHolder.getLocationRadarManager(applicationContext).stopContinuousTracking()
            ViaAlertaRadarHolder.getVoiceCopilotManager(applicationContext).stopListening()
        } catch (_: Throwable) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ViaAlerta::RoadRadarWakeLock").apply {
                setReferenceCounted(false)
                // Safety timeout of 6 hours; re-acquired on each command start
                acquire(6L * 60L * 60L * 1000L)
            }
        } catch (t: Throwable) {
            Log.w("RoadRadarService", "Could not acquire WakeLock: ${t.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
        wakeLock = null
    }

    private fun startBackgroundRadarLoop() {
        val appCtx = applicationContext
        val radar = ViaAlertaRadarHolder.getLocationRadarManager(appCtx)
        val alertRepo = RoadAlertRepository(appCtx)
        val userRepo = UserRepository(appCtx)

        ViaAlertaRadarHolder.ensureDefaultCallbacks(appCtx)
        ViaAlertaRadarHolder.isForegroundServiceRunning.value = true
        radar.startContinuousTracking()

        if (profileJob?.isActive != true) {
            profileJob = serviceScope.launch {
                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                try {
                    userRepo.observeUserProfile(uid).collectLatest { profile ->
                        cachedUserProfile = profile
                    }
                } catch (t: Throwable) {
                    Log.w("RoadRadarService", "Profile observer ended: ${t.message}")
                }
            }
        }

        if (radarMonitoringJob?.isActive == true) return

        radarMonitoringJob = serviceScope.launch {
            try {
                combine(
                    radar.currentLocation,
                    alertRepo.observeActiveAlerts(),
                    ViaAlertaRadarHolder.isDriveSimulationActive
                ) { driverLoc, activeAlerts, isSimulating ->
                    Triple(driverLoc, activeAlerts, isSimulating)
                }.collectLatest { (driverLoc, activeAlerts, isSimulating) ->
                    val currentUser = Firebase.auth.currentUser ?: return@collectLatest
                    val email = currentUser.email?.lowercase()?.trim() ?: ""
                    val isPro = UserRepository.isProEffective(appCtx, cachedUserProfile, email)
                    val isDriveMode = isSimulating || (driverLoc.speedKmH > 15f)
                    radar.checkProximityWarnings(
                        activeAlerts = activeAlerts,
                        isDriveMode = isDriveMode,
                        isProUser = isPro,
                        currentUserId = currentUser.uid
                    )
                }
            } catch (t: Throwable) {
                Log.w("RoadRadarService", "Radar monitoring loop error: ${t.message}")
            }
        }
    }

    private fun startInForeground() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RoadRadarForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VíaAlerta: Copiloto en Ruta Activo")
            .setContentText("Alertas de pozos y peligros activas aunque cambies de app o apagues la pantalla.")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Detener Copiloto",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()

        try {
            val hasAudio = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val fgsType = if (hasAudio) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("RoadRadarService", "Foreground service started (micType=$hasAudio).")
        } catch (t: Throwable) {
            Log.e("RoadRadarService", "Error starting foreground service: ${t.message}", t)
            // Fallback to location-only if microphone FGS type isn't permitted from current state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } catch (_: Throwable) {}
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VíaAlerta - Radar en Ruta",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene las alertas de pozos y el copiloto activos con la pantalla apagada o en otras apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        ViaAlertaRadarHolder.isForegroundServiceRunning.value = false
        releaseWakeLock()
        radarMonitoringJob?.cancel()
        profileJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "viaalerta_radar_channel"
        const val NOTIFICATION_ID = 4041
        const val ACTION_STOP = "com.viaalerta.app.ACTION_STOP_RADAR"

        fun start(context: Context) {
            try {
                val intent = Intent(context, RoadRadarForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w("RoadRadarService", "Could not start service: ${t.message}")
            }
        }

        fun stop(context: Context) {
            ViaAlertaRadarHolder.isForegroundServiceRunning.value = false
            try {
                val intent = Intent(context, RoadRadarForegroundService::class.java)
                context.stopService(intent)
            } catch (t: Throwable) {
                Log.w("RoadRadarService", "Could not stop service: ${t.message}")
            }
        }
    }
}
