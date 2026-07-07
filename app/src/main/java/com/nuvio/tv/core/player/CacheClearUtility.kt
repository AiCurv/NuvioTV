package com.nuvio.tv.core.player

import android.content.Context
import android.util.Log
import android.widget.Toast
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.nuvio.tv.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for clearing all cached data (video + image) asynchronously.
 *
 * Video cache: Releases the [SimpleCache] managed by [CacheManager],
 * recursively deletes the `media_cache` directory, then re-initializes.
 *
 * Image cache: Accesses the Coil [SingletonImageLoader] singleton and
 * clears both its memory cache and disk cache.
 */
@Singleton
class CacheClearUtility @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheManager: CacheManager
) {
    companion object {
        private const val TAG = "CacheClearUtility"
    }

    /**
     * Clears all video and image caches asynchronously on [Dispatchers.IO].
     * Shows a Toast on success or failure.
     */
    suspend fun clearAllCache() {
        withContext(Dispatchers.IO) {
            var videoSuccess = false
            var imageSuccess = false

            // 1. Clear video cache
            videoSuccess = try {
                cacheManager.clearAndReinitialize()
                Log.d(TAG, "Video cache cleared and re-initialized")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear video cache", e)
                false
            }

            // 2. Clear image cache (Coil)
            imageSuccess = try {
                val imageLoader = SingletonImageLoader.get(context.applicationContext)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                Log.d(TAG, "Image cache cleared")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear image cache", e)
                false
            }

            // 3. Show toast on main thread
            withContext(Dispatchers.Main) {
                if (videoSuccess && imageSuccess) {
                    Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                } else if (videoSuccess || imageSuccess) {
                    Toast.makeText(context, R.string.cache_cleared_partial, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.cache_clear_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
