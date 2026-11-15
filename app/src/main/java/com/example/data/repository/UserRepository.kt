package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

import com.example.util.getSafeFirestoreInstance

class UserRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(getSafeFirestoreInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot -> snapshot.toObject(UserProfile::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    emit(null)
                }
        )
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            snapshot.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = getUserProfile(uid)
            val createdAt = existing?.createdAt ?: profile.createdAt ?: FieldValue.serverTimestamp()
            val payload = mapOf(
                "userId" to uid,
                "name" to profile.name,
                "email" to (profile.email.ifBlank { auth.currentUser?.email ?: "" }),
                "dni" to profile.dni,
                "isDniVerified" to profile.isDniVerified,
                "phone" to profile.phone,
                "defaultVehicle" to profile.defaultVehicle,
                "defaultPlate" to profile.defaultPlate.uppercase().trim(),
                "trustRole" to profile.trustRole,
                "isProUser" to profile.isProUser,
                "proExpiresAtMillis" to profile.proExpiresAtMillis,
                "pointsRuta" to profile.pointsRuta,
                "dailyPointsEarned" to profile.dailyPointsEarned,
                "lastPointsDateString" to profile.lastPointsDateString,
                "createdAt" to createdAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun grantWelcomeProTrialIfEligible(userId: String): Result<Boolean> {
        val docRef = db.collection("users").document(userId)
        return try {
            val current = getUserProfile(userId)
            // If user has not yet been granted pro trial (proExpiresAtMillis == 0L)
            if (current == null || current.proExpiresAtMillis == 0L) {
                val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
                val trialExpiry = System.currentTimeMillis() + sevenDaysMillis
                docRef.update(
                    mapOf(
                        "isProUser" to true,
                        "proExpiresAtMillis" to trialExpiry,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun awardPointsRuta(userId: String, pointsToAdd: Int): Result<Int> {
        val docRef = db.collection("users").document(userId)
        return try {
            val profile = getUserProfile(userId) ?: return Result.failure(Exception("Usuario no encontrado"))
            val todayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

            val currentDaily = if (profile.lastPointsDateString == todayString) profile.dailyPointsEarned else 0
            val maxAllowedToday = 200
            val remainingQuota = kotlin.math.max(0, maxAllowedToday - currentDaily)
            val actualPointsAdded = kotlin.math.min(pointsToAdd, remainingQuota)

            if (actualPointsAdded > 0) {
                val newTotalPoints = profile.pointsRuta + actualPointsAdded
                val newDailyPoints = currentDaily + actualPointsAdded
                docRef.update(
                    mapOf(
                        "pointsRuta" to newTotalPoints,
                        "dailyPointsEarned" to newDailyPoints,
                        "lastPointsDateString" to todayString,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(actualPointsAdded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redeemPointsForPro(userId: String, pointsCost: Int, daysToGrant: Int): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val profile = getUserProfile(userId) ?: return Result.failure(Exception("Usuario no encontrado"))
            if (profile.pointsRuta < pointsCost) {
                return Result.failure(Exception("Puntos insuficientes. Necesitás $pointsCost pts."))
            }

            val now = System.currentTimeMillis()
            val baseTime = if (profile.proExpiresAtMillis > now) profile.proExpiresAtMillis else now
            val addedDuration = daysToGrant.toLong() * 24L * 60L * 60L * 1000L
            val newExpiry = baseTime + addedDuration

            docRef.update(
                mapOf(
                    "pointsRuta" to (profile.pointsRuta - pointsCost),
                    "isProUser" to true,
                    "proExpiresAtMillis" to newExpiry,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val OWNER_MODE_AUTO = "AUTO"
        const val OWNER_MODE_FORCE_PRO = "FORCE_PRO"
        const val OWNER_MODE_FORCE_FREEMIUM = "FORCE_FREEMIUM"

        fun getOwnerOverrideMode(context: Context): String {
            val prefs = context.getSharedPreferences("viaalerta_owner_prefs", Context.MODE_PRIVATE)
            return prefs.getString("owner_pro_override", OWNER_MODE_AUTO) ?: OWNER_MODE_AUTO
        }

        fun setOwnerOverrideMode(context: Context, mode: String) {
            val prefs = context.getSharedPreferences("viaalerta_owner_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("owner_pro_override", mode).apply()
        }

        fun isProEffective(context: Context, userProfile: UserProfile?, userEmail: String): Boolean {
            val isOwner = userEmail.equals("redmond262@gmail.com", ignoreCase = true) ||
                    userProfile?.trustRole == "admin"
            if (isOwner) {
                val mode = getOwnerOverrideMode(context)
                if (mode == OWNER_MODE_FORCE_PRO) return true
                if (mode == OWNER_MODE_FORCE_FREEMIUM) return false
            }
            if (userProfile == null) return false
            val now = System.currentTimeMillis()
            return userProfile.isProUser && (userProfile.proExpiresAtMillis > now || userProfile.proExpiresAtMillis == 0L)
        }
    }
}
