package pt.wede.sdk.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pt.wede.sdk.models.TeamInput
import pt.wede.sdk.core.ScoreEngine

private const val CACHE_KEY_TEAMS   = "wede_cache_teams"
private const val CACHE_KEY_CATALOG = "wede_cache_catalog"
private const val CACHE_KEY_META    = "wede_cache_meta"
private const val CACHE_TTL_MS      = 5 * 60 * 1000L

private val json = Json { ignoreUnknownKeys = true }

data class CachedTeam(
    val id: String,
    val name: String,
    val status: String,
    val vertical: String,
    val equipment: List<String> = emptyList(),
    val zoneLat: Double? = null,
    val zoneLng: Double? = null,
    val members: List<pt.wede.sdk.models.TeamMember> = emptyList()
)

data class CacheMeta(
    val teamsAt: Long? = null,
    val catalogAt: Long? = null
)

class WedeCache(private val storage: WedeStorage) {

    suspend fun setTeams(teams: List<CachedTeam>) {
        storage.setItem(CACHE_KEY_TEAMS, json.encodeToString(teams))
        val meta = getMeta().copy(teamsAt = System.currentTimeMillis())
        storage.setItem(CACHE_KEY_META, json.encodeToString(meta))
    }

    suspend fun getTeams(): List<TeamInput>? {
        val raw = storage.getItem(CACHE_KEY_TEAMS) ?: return null
        val meta = getMeta()
        if (meta.teamsAt != null && System.currentTimeMillis() - meta.teamsAt > CACHE_TTL_MS) {
            return null // stale
        }
        return try {
            json.decodeFromString<List<CachedTeam>>(raw).map { t ->
                TeamInput(
                    id = t.id, name = t.name, status = t.status,
                    vertical = t.vertical, equipment = t.equipment,
                    zoneLat = t.zoneLat, zoneLng = t.zoneLng,
                    members = t.members
                )
            }
        } catch (e: Exception) { null }
    }

    suspend fun getMeta(): CacheMeta {
        val raw = storage.getItem(CACHE_KEY_META) ?: return CacheMeta()
        return try { json.decodeFromString(raw) } catch (e: Exception) { CacheMeta() }
    }

    suspend fun clear() {
        storage.removeItem(CACHE_KEY_TEAMS)
        storage.removeItem(CACHE_KEY_CATALOG)
        storage.removeItem(CACHE_KEY_META)
    }
}
