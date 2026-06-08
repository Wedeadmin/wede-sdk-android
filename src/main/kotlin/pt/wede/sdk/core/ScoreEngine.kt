package pt.wede.sdk.core

import pt.wede.sdk.models.*
import kotlin.math.*

/**
 * Wede Proximity Score Engine — Android SDK
 * Identical algorithm to backend scoreEngine.ts and SDK JS/RN.
 * No external dependencies. Pure Kotlin. Works fully offline.
 * Patent INPI 120488 — Claim 5: local scoring without connectivity.
 */
object ScoreEngine {

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLng = (lng2 - lng1) * PI / 180
        val a = sin(dLat / 2).pow(2) +
                cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun pointInPolygon(lat: Double, lng: Double, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val xi = polygon[i].lng; val yi = polygon[i].lat
            val xj = polygon[j].lng; val yj = polygon[j].lat
            val intersect = ((yi > lat) != (yj > lat)) &&
                    (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun resolvePosition(team: TeamInput): TeamPosition {
        val now = System.currentTimeMillis()
        val tenMin = 10 * 60 * 1000L

        // Fresh GPS from available member
        val fresh = team.members
            .filter { m -> m.status == "available" && m.lat != null && m.lng != null && m.lastSeen != null }
            .filter { m ->
                try {
                    now - java.time.Instant.parse(m.lastSeen).toEpochMilli() < tenMin
                } catch (e: Exception) { false }
            }
            .maxByOrNull { m -> m.lastSeen ?: "" }

        if (fresh?.lat != null && fresh.lng != null) {
            return TeamPosition(fresh.lat, fresh.lng, "gps", fresh.lastSeen)
        }

        // Any GPS
        val anyGps = team.members.firstOrNull { m -> m.lat != null && m.lng != null }
        if (anyGps?.lat != null && anyGps.lng != null) {
            return TeamPosition(anyGps.lat, anyGps.lng, "gps", anyGps.lastSeen)
        }

        // Zone center
        if (team.zoneLat != null && team.zoneLng != null) {
            return TeamPosition(team.zoneLat, team.zoneLng, "zone")
        }

        return TeamPosition(0.0, 0.0, "unknown")
    }

    private fun resolveChannel(etaMin: Int, priority: String?): String {
        if (priority == "P1_CRITICAL" || priority == "CRITICAL") {
            return if (etaMin > 5) "sms" else "internet"
        }
        return if (etaMin > 15) "sms" else "internet"
    }

    fun scoreTeams(teams: List<TeamInput>, evt: EventInput): List<ScoredTeam> {
        val available = teams.filter { t -> t.status == "available" || t.status == "on_mission" }

        val scored = available.map { team ->
            val pos = resolvePosition(team)

            val distanceKm = if (pos.source != "unknown") {
                val d = haversineKm(pos.lat, pos.lng, evt.lat, evt.lng)
                (d * 100).roundToInt() / 100.0
            } else 0.0

            val etaMin = (distanceKm / 0.7).roundToInt()

            val memberAvail = if (team.members.isNotEmpty()) {
                team.members.count { m -> m.status == "available" }.toDouble() / team.members.size
            } else 0.0

            val operationalEquip = team.teamEquipment
                ?.filter { e -> e.status == "operational" }
                ?.map { e -> e.code }
                ?: team.equipment

            val required = evt.requiredEquipment ?: emptyList()
            val equipmentMatch = when {
                required.isNotEmpty() ->
                    required.count { eq -> operationalEquip.contains(eq) }.toDouble() / required.size
                operationalEquip.isNotEmpty() -> 0.8
                else -> 0.5
            }

            val inZone = team.zoneBoundary?.let { boundary ->
                if (boundary.size >= 3) pointInPolygon(evt.lat, evt.lng, boundary) else true
            } ?: true
            val geofencePenalty = if (inZone) 0.0 else 0.2

            val coversVertical = evt.vertical == null || team.vertical == evt.vertical ||
                    (team.verticals?.any { v -> v.vertical == evt.vertical } ?: false)
            val coversEventType = evt.eventType == null ||
                    (team.verticals?.any { v -> v.eventTypes.contains(evt.eventType) } ?: true)
            val capabilityBonus = if (coversVertical && coversEventType) 0.0 else 0.3

            val travelScore = minOf(etaMin / 30.0, 1.0)
            val capScore = (1 - equipmentMatch) + capabilityBonus
            val memberScore = 1 - memberAvail
            val loadPenalty = if (team.status == "on_mission") 0.5 else 0.0

            val finalScore = (0.35 * travelScore) + (0.25 * capScore) +
                    (0.2 * memberScore) + (0.1 * loadPenalty) + (0.1 * geofencePenalty)

            ScoredTeam(
                teamId = team.id,
                teamName = team.name,
                status = team.status,
                vertical = team.vertical,
                distanceKm = distanceKm,
                etaMin = etaMin,
                equipmentMatch = (equipmentMatch * 100).roundToInt() / 100.0,
                memberAvailability = (memberAvail * 100).roundToInt() / 100.0,
                score = (finalScore * 10000).roundToInt() / 10000.0,
                recommended = false,
                channel = resolveChannel(etaMin, evt.priority),
                position = pos
            )
        }.sortedByDescending { it.score }

        return if (scored.isNotEmpty()) {
            scored.toMutableList().also { it[0] = it[0].copy(recommended = true) }
        } else scored
    }
}
