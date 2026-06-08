package pt.wede.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class TeamMember(
    val id: String,
    val status: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val lastSeen: String? = null
)

@Serializable
data class TeamEquipment(
    val code: String,
    val status: String
)

@Serializable
data class TeamVertical(
    val vertical: String,
    val eventTypes: List<String> = emptyList()
)

@Serializable
data class TeamInput(
    val id: String,
    val name: String,
    val status: String,
    val vertical: String,
    val equipment: List<String> = emptyList(),
    val zoneLat: Double? = null,
    val zoneLng: Double? = null,
    val zoneBoundary: List<GeoPoint>? = null,
    val members: List<TeamMember> = emptyList(),
    val verticals: List<TeamVertical>? = null,
    val teamEquipment: List<TeamEquipment>? = null
)

@Serializable
data class GeoPoint(
    val lat: Double,
    val lng: Double
)

@Serializable
data class EventInput(
    val lat: Double,
    val lng: Double,
    val vertical: String? = null,
    val eventType: String? = null,
    val priority: String? = null,
    val requiredEquipment: List<String>? = null
)

@Serializable
data class ScoredTeam(
    val teamId: String,
    val teamName: String,
    val status: String,
    val vertical: String,
    val distanceKm: Double,
    val etaMin: Int,
    val equipmentMatch: Double,
    val memberAvailability: Double,
    val score: Double,
    val recommended: Boolean,
    val channel: String,
    val position: TeamPosition
)

@Serializable
data class TeamPosition(
    val lat: Double,
    val lng: Double,
    val source: String,
    val lastSeen: String? = null
)

@Serializable
data class OfflineDispatchRequest(
    val id: String,
    val actionId: String,
    val event: EventInput,
    val teamId: String,
    val teamName: String,
    val score: Double,
    val channel: String,
    val queuedAt: String,
    val synced: Boolean = false,
    val sequenceNumber: Long
)

@Serializable
data class DispatchOfflineResult(
    val success: Boolean,
    val team: ScoredTeam?,
    val queued: Boolean,
    val queueId: String?,
    val reason: String? = null
)

@Serializable
data class SyncResult(
    val accepted: List<Long>,
    val duplicates: List<Long>,
    val failed: List<Long>,
    val serverSeq: Long,
    val deviceLastReceivedSeq: Long,
    val syncedAt: String
)
