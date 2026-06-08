package pt.wede.sdk.sync

import pt.wede.sdk.cache.WedeStorage
import java.util.UUID

private const val DEVICE_ID_KEY = "wede_device_id"

/**
 * Permanent device identifier — generated on first install.
 * Never deleted, survives app reinstalls if storage is backed up.
 */
class WedeDeviceId(private val storage: WedeStorage) {

    suspend fun getOrCreate(): String {
        val existing = storage.getItem(DEVICE_ID_KEY)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        storage.setItem(DEVICE_ID_KEY, newId)
        return newId
    }

    suspend fun get(): String? = storage.getItem(DEVICE_ID_KEY)
}
