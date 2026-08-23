package com.metrolist.music.playback.aa

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.metrolist.music.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkUriResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val MAX_CONCURRENT_DOWNLOADS = 10
        const val MAX_PENDING_PREFETCHES = 100
    }

    private val prefetchedUrls = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    fun resolve(
        url: String?,
        @DrawableRes placeholder: Int = R.drawable.music_note,
    ): Uri {
        if (url.isNullOrBlank()) {
            return drawableUri(placeholder)
        }

        if (isInCoilDiskCache(url)) {
            return ArtworkProvider.uriFor(context, url)
        }

        schedulePrefetch(url)
        return drawableUri(placeholder)
    }

    private fun isInCoilDiskCache(url: String): Boolean {
        return try {
            val snapshot = context.imageLoader.diskCache?.openSnapshot(url)
                ?: return false

            try {
                val file = snapshot.data.toFile()
                file.exists() && file.length() > 0L
            } finally {
                snapshot.close()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun schedulePrefetch(url: String) {
        val isScheduleAllowed = synchronized(prefetchedUrls) {
            prefetchedUrls.size < MAX_PENDING_PREFETCHES && prefetchedUrls.add(url)
        }
        if (!isScheduleAllowed) return

        scope.launch {
            try {
                semaphore.withPermit {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()

                    context.imageLoader.execute(request)
                }
            } catch (_: Throwable) {
            } finally {
                prefetchedUrls.remove(url)
            }
        }
    }

    private fun drawableUri(@DrawableRes id: Int): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.resources.getResourcePackageName(id))
            .appendPath(context.resources.getResourceTypeName(id))
            .appendPath(context.resources.getResourceEntryName(id))
            .build()
}