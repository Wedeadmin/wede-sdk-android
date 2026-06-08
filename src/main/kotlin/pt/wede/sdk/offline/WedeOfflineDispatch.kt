package pt.wede.sdk.offline

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pt.wede.sdk.cache.WedeCache
import pt.wede.sdk.cache.WedeStorage
import pt.wede.sdk.core.ScoreEngine
import pt.wede.sdk.models.*
import java.util.UUID

private const val QUEUE_KEY = "wede_offline_dispatch_queue"
private val json = Json { ignoreUnknownKeys = true }

/**
 * Wede Offline Dispatch — Android SDK
 * Scores teams locally and queues dispatches for guaranteed delivery.
 * Patent INPI 120488 — Claim 5 implementation.
 * Queue is immutable — never deleted until server confirmation.
 */
class WedeOfflineDispatch(private val storage: WedeStorage) {

    private val cache = WedeCache(storage)

    /**
     * Score teams locally using cached data.
     * Works without network connectivity.
     */
    suspend fun scoreLocally(event: EventInput): List<ScoredTeam> {
        val teams = cache.getTeams() ?: return emptyList()
        return ScoreEngine.scoreTeams(teams, event)
    }

    /**
     * Dispatch offline:
     * 1. Score teams locally
     * 2. Pick best available team
     * 3. Queue dispatch — persists until server confirms
     */
    suspend fun dispatch(actionId: String, event: EventInput): DispatchOfflineResult {
        val scored = scoreLocally(event)
        val best = scored.firstOrNull { it.status == "available" } ?: scored.firstOrNull()

        if (best == null) {
            return DispatchOfflineResult(
                success = false, team = null, queued = false,
                queueId = null, reason = "no_teams_cached"
            )
        }

        val seq = getNextSequence()
        val id = UUID.randomUUID().toString()
        val entry = OfflineDispatchRequest(
            id = id,
            actionId = actionId,
            event = event,
            teamId = best.teamId,
            teamName = best.teamName,
            score = best.score,
            channel = best.channel,
            queuedAt = java.time.Instant.now().toString(),
            synced = false,
            sequenceNumber = seq
        )

        enqueue(entry)
        return DispatchOfflineResult(success = true, team = best, queued = true, queueId = id)
    }

    /**
     * Get all pending (unsynced) dispatch requests.
     * These must be sent to server and confirmed before removal.
     */
    suspend fun getPendingQueue(): List<OfflineDispatchRequest> {
        return getAllQueue().filter { !it.synced }
    }

    /**
     * Mark a queued dispatch as synced after server confirmation.
     */
    suspend fun markSynced(id: String) {
        val all = getAllQueue().map { if (it.id == id) it.copy(synced = true) else it }
        storage.setItem(QUEUE_KEY, json.encodeToString(all))
    }

    /**
     * Remove synced entries — only call after server confirmation.
     */
    suspend fun clearSynced() {
        val pending = getAllQueue().filter { !it.synced }
        storage.setItem(QUEUE_KEY, json.encodeToString(pending))
    }

    suspend fun queueSize(): Int = getPendingQueue().size

    private suspend fun getAllQueue(): List<OfflineDispatchRequest> {
        val raw = storage.getItem(QUEUE_KEY) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }

    private suspend fun enqueue(entry: OfflineDispatchRequest) {
        val all = getAllQueue().toMutableList()
        all.add(entry)
        storage.setItem(QUEUE_KEY, json.encodeToString(all))
    }

    private suspend fun getNextSequence(): Long {
        val all = getAllQueue()
        return (all.maxOfOrNull { it.sequenceNumber } ?: 0L) + 1L
    }
}
