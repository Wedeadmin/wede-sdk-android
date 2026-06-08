package pt.wede.sdk.cache

/**
 * Abstract storage interface — implement with SharedPreferences, DataStore, or Room.
 * Follows the same pattern as SDK JS/RN WedeStorage.
 */
interface WedeStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String)
}
