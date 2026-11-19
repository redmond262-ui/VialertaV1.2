package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.RoadLevels
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
            val resolvedCode = profile.referralCode.ifBlank { existing?.referralCode.orEmpty() }
                .ifBlank { buildReferralCode(uid) }
            val payload = mapOf(
                "userId" to uid,
                "name" to profile.name.ifBlank { auth.currentUser?.displayName ?: "Conductor Vialerta" },
                "email" to (profile.email.ifBlank { auth.currentUser?.email ?: "" }),
                "dni" to profile.dni,
                "isDniVerified" to profile.isDniVerified,
                "phone" to profile.phone,
                "defaultVehicle" to profile.defaultVehicle,
                "defaultPlate" to profile.defaultPlate.uppercase().trim(),
                "trustRole" to profile.trustRole,
                "isProUser" to profile.isProUser,
                "proExpiresAtMillis" to profile.proExpiresAtMillis,
                "isBetaTester" to (existing?.isBetaTester ?: profile.isBetaTester),
                "xpTotal" to kotlin.math.max(profile.xpTotal, existing?.xpTotal ?: 0),
                "pointsRuta" to profile.pointsRuta,
                "dailyPointsEarned" to profile.dailyPointsEarned,
                "lastPointsDateString" to profile.lastPointsDateString,
                "referralCode" to resolvedCode,
                "referredByUid" to profile.referredByUid.ifBlank { existing?.referredByUid.orEmpty() },
                "referredByCode" to profile.referredByCode.ifBlank { existing?.referredByCode.orEmpty() },
                "referralCount" to kotlin.math.max(profile.referralCount, existing?.referralCount ?: 0),
                "alertsCreatedCount" to kotlin.math.max(profile.alertsCreatedCount, existing?.alertsCreatedCount ?: 0),
                "alertsResolvedCount" to kotlin.math.max(profile.alertsResolvedCount, existing?.alertsResolvedCount ?: 0),
                "confirmedImpactCount" to kotlin.math.max(profile.confirmedImpactCount, existing?.confirmedImpactCount ?: 0),
                "lastWeekendProClaimMonth" to (profile.lastWeekendProClaimMonth.ifBlank { existing?.lastWeekendProClaimMonth.orEmpty() }),
                "lastLegendProClaimMonth" to (profile.lastLegendProClaimMonth.ifBlank { existing?.lastLegendProClaimMonth.orEmpty() }),
                "createdAt" to createdAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            ensureReferralCodeDocument(uid, profile.name, resolvedCode)
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
            val generatedCode = current?.referralCode?.ifBlank { null } ?: buildReferralCode(userId)
            val displayName = current?.name?.ifBlank { null }
                ?: auth.currentUser?.displayName?.ifBlank { null }
                ?: "Conductor Vialerta"

            if (current == null) {
                val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
                val trialExpiry = System.currentTimeMillis() + sevenDaysMillis
                val initialPayload = mapOf(
                    "userId" to userId,
                    "name" to displayName,
                    "email" to (auth.currentUser?.email ?: ""),
                    "trustRole" to "general",
                    "isProUser" to true,
                    "proExpiresAtMillis" to trialExpiry,
                    "isBetaTester" to false,
                    "xpTotal" to 0,
                    "pointsRuta" to 0,
                    "dailyPointsEarned" to 0,
                    "referralCode" to generatedCode,
                    "referralCount" to 0,
                    "alertsCreatedCount" to 0,
                    "alertsResolvedCount" to 0,
                    "confirmedImpactCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(initialPayload).await()
                ensureReferralCodeDocument(userId, displayName, generatedCode)
                Result.success(true)
            } else {
                ensureReferralCodeDocument(userId, displayName, generatedCode)
                if (current.proExpiresAtMillis == 0L) {
                    val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
                    val trialExpiry = System.currentTimeMillis() + sevenDaysMillis
                    docRef.update(
                        mapOf(
                            "isProUser" to true,
                            "proExpiresAtMillis" to trialExpiry,
                            "referralCode" to generatedCode,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success(true)
                } else {
                    if (current.referralCode.isBlank()) {
                        docRef.update(
                            mapOf(
                                "referralCode" to generatedCode,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    Result.success(false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureReferralCodeDocument(uid: String, displayName: String, code: String) {
        try {
            val codeRef = db.collection("referral_codes").document(code)
            val snap = codeRef.get().await()
            if (!snap.exists()) {
                codeRef.set(
                    mapOf(
                        "code" to code,
                        "ownerUid" to uid,
                        "ownerName" to displayName.trim().split(" ").firstOrNull().orEmpty().ifBlank { "Conductor" },
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Redeems a friend's referral code (VIA-XXXXX) once per invited account.
     * Creates `/referrals/{uid}` and credits +150 XP/Puntos Ruta and +7 PRO days immediately to the referee.
     */
    suspend fun redeemReferralCode(rawCode: String): Result<String> {
        val uid = requireUserId()
        val code = normalizeReferralCode(rawCode)
        if (!isValidReferralCodeFormat(code)) {
            return Result.failure(IllegalArgumentException("Formato inválido. Usá el código tipo VIA-XXXXX."))
        }

        return try {
            val myProfile = getUserProfile(uid)
            if (!myProfile?.referredByCode.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Ya canjeaste el código ${myProfile?.referredByCode} anteriormente."))
            }

            val existingRef = db.collection("referrals").document(uid).get().await()
            if (existingRef.exists()) {
                return Result.failure(IllegalStateException("Tu cuenta ya tiene una invitación aplicada."))
            }

            val codeSnap = db.collection("referral_codes").document(code).get().await()
            if (!codeSnap.exists()) {
                return Result.failure(IllegalArgumentException("No encontramos el código $code. Revisalo con quien te invitó."))
            }

            val ownerUid = codeSnap.getString("ownerUid") ?: ""
            val ownerName = codeSnap.getString("ownerName") ?: "un conductor"
            if (ownerUid.isBlank() || ownerUid == uid) {
                return Result.failure(IllegalArgumentException("No podés usar tu propio código de invitación."))
            }

            val myName = myProfile?.name?.ifBlank { null }
                ?: auth.currentUser?.displayName?.ifBlank { null }
                ?: "Conductor Vialerta"

            val refPayload = mapOf(
                "refereeUid" to uid,
                "refereeName" to myName,
                "referrerUid" to ownerUid,
                "code" to code,
                "status" to "pending",
                "rewardGrantedToReferee" to true,
                "rewardGrantedToReferrer" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("referrals").document(uid).set(refPayload).await()

            val now = System.currentTimeMillis()
            val baseExpiry = if ((myProfile?.proExpiresAtMillis ?: 0L) > now) myProfile!!.proExpiresAtMillis else now
            val bonusMs = REFERRAL_BONUS_DAYS.toLong() * 24L * 60L * 60L * 1000L
            val newExpiry = baseExpiry + bonusMs

            val userUpdates = mapOf(
                "pointsRuta" to ((myProfile?.pointsRuta ?: 0) + REFERRAL_BONUS_POINTS),
                "xpTotal" to ((myProfile?.xpTotal ?: 0) + REFERRAL_BONUS_POINTS),
                "isProUser" to true,
                "proExpiresAtMillis" to newExpiry,
                "referredByUid" to ownerUid,
                "referredByCode" to code,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(userUpdates).await()

            Result.success("¡Código de $ownerName aplicado! Sumaste +$REFERRAL_BONUS_POINTS pts/XP y +$REFERRAL_BONUS_DAYS días PRO.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "referrals/$uid")
            Result.failure(e)
        }
    }

    /**
     * Checks `/referrals` where current user is the referrer and settles any pending invitations,
     * granting +150 XP/Puntos Ruta and +7 PRO days per invited driver.
     */
    suspend fun claimPendingReferralRewards(userId: String): Result<Int> {
        return try {
            val uid = requireUserId()
            if (uid != userId) return Result.success(0)

            val pendingSnaps = db.collection("referrals")
                .whereEqualTo("referrerUid", uid)
                .whereEqualTo("rewardGrantedToReferrer", false)
                .get()
                .await()

            if (pendingSnaps.isEmpty) return Result.success(0)

            val profile = getUserProfile(uid) ?: return Result.success(0)
            var claimedCount = 0
            for (doc in pendingSnaps.documents) {
                try {
                    doc.reference.update(
                        mapOf(
                            "rewardGrantedToReferrer" to true,
                            "status" to "claimed",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    claimedCount++
                } catch (_: Throwable) {}
            }

            if (claimedCount > 0) {
                val now = System.currentTimeMillis()
                val baseExpiry = if (profile.proExpiresAtMillis > now) profile.proExpiresAtMillis else now
                val bonusMs = (REFERRAL_BONUS_DAYS * claimedCount).toLong() * 24L * 60L * 60L * 1000L
                val bonusPts = REFERRAL_BONUS_POINTS * claimedCount

                db.collection("users").document(uid).update(
                    mapOf(
                        "pointsRuta" to (profile.pointsRuta + bonusPts),
                        "xpTotal" to (profile.xpTotal + bonusPts),
                        "referralCount" to (profile.referralCount + claimedCount),
                        "isProUser" to true,
                        "proExpiresAtMillis" to (baseExpiry + bonusMs),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(claimedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun awardPointsRuta(
        userId: String,
        pointsToAdd: Int,
        createdDelta: Int = 0,
        resolvedDelta: Int = 0,
        impactDelta: Int = 0,
        bypassDailyCap: Boolean = false
    ): Result<Int> {
        val docRef = db.collection("users").document(userId)
        return try {
            val profile = getUserProfile(userId) ?: return Result.failure(Exception("Usuario no encontrado"))
            val todayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

            val currentDaily = if (profile.lastPointsDateString == todayString) profile.dailyPointsEarned else 0
            val maxAllowedToday = 200
            val remainingQuota = kotlin.math.max(0, maxAllowedToday - currentDaily)
            val actualPointsAdded = if (bypassDailyCap) pointsToAdd else kotlin.math.min(pointsToAdd, remainingQuota)

            if (actualPointsAdded > 0 || createdDelta > 0 || resolvedDelta > 0 || impactDelta > 0) {
                val baseXp = kotlin.math.max(profile.xpTotal, profile.pointsRuta)
                val newTotalPoints = profile.pointsRuta + actualPointsAdded
                val newXpTotal = baseXp + actualPointsAdded
                val newDailyPoints = if (bypassDailyCap) currentDaily else currentDaily + actualPointsAdded
                val updates = mutableMapOf<String, Any>(
                    "pointsRuta" to newTotalPoints,
                    "xpTotal" to newXpTotal,
                    "dailyPointsEarned" to newDailyPoints,
                    "lastPointsDateString" to todayString,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (createdDelta > 0) updates["alertsCreatedCount"] = profile.alertsCreatedCount + createdDelta
                if (resolvedDelta > 0) updates["alertsResolvedCount"] = profile.alertsResolvedCount + resolvedDelta
                if (impactDelta > 0) updates["confirmedImpactCount"] = profile.confirmedImpactCount + impactDelta
                docRef.update(updates).await()
            }
            Result.success(actualPointsAdded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redeemPointsForPro(userId: String, basePointsCost: Int, daysToGrant: Int): Result<Int> {
        val docRef = db.collection("users").document(userId)
        return try {
            val profile = getUserProfile(userId) ?: return Result.failure(Exception("Usuario no encontrado"))
            val effectiveXp = kotlin.math.max(profile.xpTotal, profile.pointsRuta)
            val finalCost = RoadLevels.discountedPointsCost(basePointsCost, effectiveXp)
            if (profile.pointsRuta < finalCost) {
                return Result.failure(Exception("Puntos insuficientes. Necesitás $finalCost pts."))
            }

            val now = System.currentTimeMillis()
            val baseTime = if (profile.proExpiresAtMillis > now) profile.proExpiresAtMillis else now
            val addedDuration = daysToGrant.toLong() * 24L * 60L * 60L * 1000L
            val newExpiry = baseTime + addedDuration

            // NOTE: xpTotal intentionally NEVER decreases on point redemptions
            docRef.update(
                mapOf(
                    "pointsRuta" to (profile.pointsRuta - finalCost),
                    "isProUser" to true,
                    "proExpiresAtMillis" to newExpiry,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(finalCost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Grants monthly PRO rewards unlocked by Road Level:
     * - Level 6 (Leyenda Vial, >= 15.000 XP): +30 days PRO free per month.
     * - Level 4-5 (Patrulla / Centinela, >= 2.500 XP): +2 days weekend PRO per month.
     */
    suspend fun claimMonthlyLevelProBonus(userId: String): Result<String> {
        val docRef = db.collection("users").document(userId)
        return try {
            val profile = getUserProfile(userId) ?: return Result.failure(Exception("Usuario no encontrado"))
            val effectiveXp = kotlin.math.max(profile.xpTotal, profile.pointsRuta)
            val level = RoadLevels.forXp(effectiveXp)
            if (!level.monthlyWeekendProBonus && !level.freeLegendProEligible) {
                return Result.failure(IllegalStateException("Este beneficio se desbloquea desde Nivel 4 (Patrulla Comunitaria)."))
            }

            val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
            val now = System.currentTimeMillis()
            val baseTime = if (profile.proExpiresAtMillis > now) profile.proExpiresAtMillis else now

            if (level.freeLegendProEligible && profile.lastLegendProClaimMonth != monthKey) {
                val addedMs = 30L * 24L * 60L * 60L * 1000L
                docRef.update(
                    mapOf(
                        "isProUser" to true,
                        "proExpiresAtMillis" to (baseTime + addedMs),
                        "lastLegendProClaimMonth" to monthKey,
                        "lastWeekendProClaimMonth" to monthKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.success("👑 ¡Beneficio Leyenda Vial! Sumaste +30 días PRO sin costo este mes.")
            }

            if (profile.lastWeekendProClaimMonth == monthKey) {
                return Result.failure(IllegalStateException("Ya reclamaste tu bono mensual de Nivel ${level.level} este mes ($monthKey)."))
            }

            val weekendMs = 2L * 24L * 60L * 60L * 1000L
            docRef.update(
                mapOf(
                    "isProUser" to true,
                    "proExpiresAtMillis" to (baseTime + weekendMs),
                    "lastWeekendProClaimMonth" to monthKey,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success("${level.badge} ¡Finde PRO activado (+2 días gratis por Nivel ${level.level})!")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin-only real-time observation of registered users for Beta Tester and PRO management. */
    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java)
                        .sortedByDescending { it.xpTotal }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    /** Admin-only toggle to grant or revoke Beta Tester access for a specific driver. */
    suspend fun setUserBetaTester(targetUserId: String, isBeta: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(targetUserId)
        return try {
            docRef.update(
                mapOf(
                    "isBetaTester" to isBeta,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
            }
            Result.failure(e)
        }
    }

    /**
     * Admin-only helper to grant PRO licenses:
     * - `daysToGrant > 0`: sets PRO expiring in N days from now
     * - `daysToGrant == 0`: grants Unlimited / Lifetime PRO (`proExpiresAtMillis = 0L`)
     * - `daysToGrant == null || daysToGrant < 0`: revokes to Freemium
     */
    suspend fun setUserProByAdmin(targetUserId: String, daysToGrant: Int?): Result<Unit> {
        val docRef = db.collection("users").document(targetUserId)
        return try {
            val now = System.currentTimeMillis()
            val payload = when {
                daysToGrant != null && daysToGrant > 0 -> mapOf(
                    "isProUser" to true,
                    "proExpiresAtMillis" to (now + daysToGrant.toLong() * 24L * 60L * 60L * 1000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                daysToGrant == 0 -> mapOf(
                    "isProUser" to true,
                    "proExpiresAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                else -> mapOf(
                    "isProUser" to false,
                    "proExpiresAtMillis" to (now - 1000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
            }
            Result.failure(e)
        }
    }

    companion object {
        const val OWNER_MODE_AUTO = "AUTO"
        const val OWNER_MODE_FORCE_PRO = "FORCE_PRO"
        const val OWNER_MODE_FORCE_FREEMIUM = "FORCE_FREEMIUM"

        const val REFERRAL_BONUS_POINTS = 150
        const val REFERRAL_BONUS_DAYS = 7
        const val CLEANUP_BONUS_POINTS = 20
        const val AUTHOR_CONFIRM_BONUS_POINTS = 25

        fun buildReferralCode(uid: String): String {
            val clean = uid.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
            val suffix = if (clean.length >= 6) clean.take(6) else clean.padEnd(6, 'V')
            return "VIA-$suffix"
        }

        fun normalizeReferralCode(raw: String): String {
            return raw.trim().uppercase().replace(" ", "")
        }

        fun isValidReferralCodeFormat(code: String): Boolean {
            return code.matches(Regex("^VIA-[A-Z0-9]{4,12}$"))
        }

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
