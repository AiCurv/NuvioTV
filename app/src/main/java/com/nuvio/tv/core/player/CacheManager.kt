package com.nuvio.tv.core.player

import android.content.Context
import android.util.Log
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for the video disk cache backed by Media3's [SimpleCache].
 *
 * The cache directory is [Context.cacheDir]/`media_cache` so it lives inside the
 * app's own cache partition (protected from OS cleanup unless critical).
 *
 * Disk space is bounded by [LeastRecentlyUsedCacheEvictor] initialized with the
 * user-selected byte limit. When the user selects "Disabled" (0 bytes) the cache
 * is never created and all requests fall through to raw HTTP.
 *
 * Thread safety: [SimpleCache] is internally synchronized. This class wraps
 * creation/release with an additional [lock] so that concurrent callers
 * (e.g. clear-cache + player start) never race.
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CacheManager"
        /** Subdirectory under [Context.cacheDir] for media cache. */
        const val MEDIA_CACHE_DIR = "media_cache"
    }

    private val lock = Object()

    @Volatile
    private var _simpleCache: SimpleCache? = null

    @Volatile
    private var _configuredMaxBytes: Long = 0L

    /**
     * Returns the current [SimpleCache] if one has been initialized with the
     * exact [maxBytes] limit, or `null` if no cache exists or the limit changed.
     */
    fun getCache(maxBytes: Long): SimpleCache? {
        synchronized(lock) {
            val cache = _simpleCache ?: return null
            return if (_configuredMaxBytes == maxBytes) cache else null
        }
    }

    /**
     * Returns any [SimpleCache] regardless of configured size, or `null`.
     */
    fun getAnyCache(): SimpleCache? {
        synchronized(lock) {
            return _simpleCache
        }
    }

    /**
     * Returns the current cache if it matches [maxBytes], otherwise creates a
     * new one (releasing the old cache first if the size changed).
     *
     * If [maxBytes] is 0 (disabled), returns `null` without creating a cache.
     */
    fun getOrCreateCache(maxBytes: Long): SimpleCache? {
        synchronized(lock) {
            if (maxBytes <= 0L) {
                // Cache disabled – release any existing cache and return null.
                releaseInternal()
                return null
            }
            val existing = _simpleCache
            if (existing != null && _configuredMaxBytes == maxBytes) {
                return existing
            }
            // Size changed or first creation – release old cache.
            releaseInternal()
            return createCache(maxBytes)
        }
    }

    /**
     * Releases the [SimpleCache] and recursively deletes the cache directory,
     * then re-initializes a fresh cache with the same byte limit.
     *
     * Must be called on a background thread (Dispatchers.IO).
     */
    fun clearAndReinitialize() {
        synchronized(lock) {
            val previousMaxBytes = _configuredMaxBytes
            releaseInternal()
            deleteCacheDirectory()
            if (previousMaxBytes > 0L) {
                createCache(previousMaxBytes)
            }
        }
    }

    /**
     * Releases the [SimpleCache] and deletes the cache directory.
     * After this call the cache is fully disabled until [getOrCreateCache] is
     * called again.
     */
    fun releaseAndDelete() {
        synchronized(lock) {
            releaseInternal()
            deleteCacheDirectory()
        }
    }

    /**
     * Returns the current configured maximum cache size in bytes,
     * or 0 if the cache is not initialized.
     */
    val configuredMaxBytes: Long get() = _configuredMaxBytes

    private fun createCache(maxBytes: Long): SimpleCache? {
        return try {
            val cacheDir = context.cacheDir.resolve(MEDIA_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            // LeastRecentlyUsedCacheEvictor handles the 0-byte case safely:
            // if maxBytes is 0 it effectively allows no content, but we guard
            // against that at the call site (getOrCreateCache returns null for 0).
            val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
            val databaseProvider = ExoDatabaseProvider(context)
            val cache = SimpleCache(cacheDir, evictor, databaseProvider)
            _simpleCache = cache
            _configuredMaxBytes = maxBytes
            Log.d(TAG, "Created SimpleCache with maxBytes=$maxBytes, dir=${cacheDir.absolutePath}")
            cache
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SimpleCache", e)
            // Attempt cleanup on failure.
            deleteCacheDirectory()
            null
        }
    }

    private fun releaseInternal() {
        _simpleCache?.let { cache ->
            try {
                cache.release()
                Log.d(TAG, "Released SimpleCache")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing SimpleCache", e)
            }
        }
        _simpleCache = null
        _configuredMaxBytes = 0L
    }

    private fun deleteCacheDirectory() {
        try {
            val cacheDir = context.cacheDir.resolve(MEDIA_CACHE_DIR)
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "Deleted cache directory: ${cacheDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cache directory", e)
        }
    }
}
