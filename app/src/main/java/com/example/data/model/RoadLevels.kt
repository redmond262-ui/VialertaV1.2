package com.example.data.model

import kotlin.math.roundToInt

data class RoadLevelSpec(
    val level: Int,
    val minXp: Int,
    val nextLevelXp: Int?,
    val title: String,
    val badge: String,
    val accentHex: Long,
    val perkSummary: String,
    val redemptionDiscountPercent: Int = 0,
    val resolveVoteWeight: Int = 1,
    val monthlyWeekendProBonus: Boolean = false,
    val autoBetaAccess: Boolean = false,
    val freeLegendProEligible: Boolean = false
)

object RoadLevels {
    val ALL_LEVELS: List<RoadLevelSpec> = listOf(
        RoadLevelSpec(
            level = 1,
            minXp = 0,
            nextLevelXp = 300,
            title = "Copiloto Inicial",
            badge = "🧭",
            accentHex = 0xFF38BDF8L,
            perkSummary = "7 días PRO bienvenida + canje estándar"
        ),
        RoadLevelSpec(
            level = 2,
            minXp = 300,
            nextLevelXp = 1000,
            title = "Conductor Atento",
            badge = "👀",
            accentHex = 0xFF22C55EL,
            perkSummary = "Insignia comunitaria + contador de impacto"
        ),
        RoadLevelSpec(
            level = 3,
            minXp = 1000,
            nextLevelXp = 2500,
            title = "Baqueano de Ruta",
            badge = "🛣️",
            accentHex = 0xFFF59E0BL,
            perkSummary = "10% dto. en canjes PRO + mayor peso de confianza",
            redemptionDiscountPercent = 10
        ),
        RoadLevelSpec(
            level = 4,
            minXp = 2500,
            nextLevelXp = 6000,
            title = "Patrulla Comunitaria",
            badge = "🛡️",
            accentHex = 0xFFA855F7L,
            perkSummary = "Voto doble al marcar Solucionado + 1 finde PRO gratis/mes",
            redemptionDiscountPercent = 10,
            resolveVoteWeight = 2,
            monthlyWeekendProBonus = true
        ),
        RoadLevelSpec(
            level = 5,
            minXp = 6000,
            nextLevelXp = 15000,
            title = "Centinela de Corredor",
            badge = "🦅",
            accentHex = 0xFFEC4899L,
            perkSummary = "25% dto. en canjes PRO + acceso automático a módulos Beta",
            redemptionDiscountPercent = 25,
            resolveVoteWeight = 2,
            monthlyWeekendProBonus = true,
            autoBetaAccess = true
        ),
        RoadLevelSpec(
            level = 6,
            minXp = 15000,
            nextLevelXp = null,
            title = "Leyenda Vial",
            badge = "👑",
            accentHex = 0xFFFACC15L,
            perkSummary = "PRO mensual sin costo manteniendo actividad + emblema Pionero",
            redemptionDiscountPercent = 25,
            resolveVoteWeight = 2,
            monthlyWeekendProBonus = true,
            autoBetaAccess = true,
            freeLegendProEligible = true
        )
    )

    fun forXp(xpTotal: Int): RoadLevelSpec {
        val safeXp = xpTotal.coerceAtLeast(0)
        return ALL_LEVELS.lastOrNull { safeXp >= it.minXp } ?: ALL_LEVELS.first()
    }

    fun nextLevelFor(xpTotal: Int): RoadLevelSpec? {
        val current = forXp(xpTotal)
        return ALL_LEVELS.firstOrNull { it.level == current.level + 1 }
    }

    fun progressToNextLevel(xpTotal: Int): Float {
        val safeXp = xpTotal.coerceAtLeast(0)
        val current = forXp(safeXp)
        val nextXp = current.nextLevelXp ?: return 1f
        val span = (nextXp - current.minXp).coerceAtLeast(1)
        return ((safeXp - current.minXp).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    fun xpRemainingForNextLevel(xpTotal: Int): Int {
        val safeXp = xpTotal.coerceAtLeast(0)
        val current = forXp(safeXp)
        val nextXp = current.nextLevelXp ?: return 0
        return (nextXp - safeXp).coerceAtLeast(0)
    }

    fun discountedPointsCost(baseCost: Int, xpTotal: Int): Int {
        val discount = forXp(xpTotal).redemptionDiscountPercent
        if (discount <= 0) return baseCost
        return ((baseCost * (100 - discount)) / 100.0).roundToInt().coerceAtLeast(1)
    }

    fun buildPioneerShareText(
        driverName: String,
        xpTotal: Int,
        alertsCreated: Int,
        confirmedHelps: Int,
        alertsResolved: Int,
        referralCode: String? = null,
        includeReferral: Boolean = true
    ): String {
        val spec = forXp(xpTotal)
        val cleanName = driverName.ifBlank { "Conductor VíaAlerta" }
        val helpedDrivers = confirmedHelps.coerceAtLeast(alertsCreated)
        val referralBlock = if (includeReferral && !referralCode.isNullOrBlank()) {
            "\n🎁 Usá mi código *$referralCode* y sumá +150 pts/XP + 7 días PRO gratis."
        } else {
            ""
        }
        return """
            ${spec.badge} *Pionero de Ruta — VíaAlerta Argentina*
            
            Soy *$cleanName* • Nivel ${spec.level}: *${spec.title}* ($xpTotal XP)
            🛣️ Alertas reportadas en ruta: *$alertsCreated*
            🤝 Conductores ayudados/confirmaciones: *$helpedDrivers*
            ✅ Peligros solucionados/limpiados: *$alertsResolved*
            
            📲 Sumate gratis y manejá con radar de pozos por voz en tiempo real:
            https://gen-lang-client-0699193083.web.app$referralBlock
        """.trimIndent()
    }
}
