package com.metrolist.music.playback.aa

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.media3.common.util.SystemClock
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.metrolist.music.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
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
        const val PREFETCH_DECODE_SIZE = 100
        const val MAX_FAILED_URLS = 500
        const val FAILED_RETRY_DELAY_MS = 10 * 60 * 1000L
    }

    private val prefetchedUrls = ConcurrentHashMap.newKeySet<String>()
    private val failedUrls = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    fun resolve(
        url: String?,
        @DrawableRes placeholder: Int = R.drawable.music_note,
    ): Uri {
        if (url.isNullOrBlank() || isFailedRecently(url)) {
            return drawableUri(placeholder)
        }

        if (isInCoilDiskCache(url)) {
            return ArtworkProvider.uriFor(context, url)
        }

        schedulePrefetch(url)
        return drawableUri(placeholder)
    }

    private fun isInCoilDiskCache(url: String): Boolean {
        val diskCache = context.imageLoader.diskCache ?: return false

        val snapshot = try {
            diskCache.openSnapshot(url)
        } catch (_: Throwable) {
            null
        } ?: return false

        val isValid = try {
            val file = snapshot.data.toFile()
            file.exists() && file.length() > 0L && isValidImageFile(file)
        } catch (_: Throwable) {
            false
        }
        snapshot.close()

        if (!isValid) {
            runCatching { diskCache.remove(url) }
        }
        return isValid
    }

    private fun isValidImageFile(file: File): Boolean {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun schedulePrefetch(url: String) {
        if (context.imageLoader.diskCache == null) return

        val isScheduleAllowed = synchronized(prefetchedUrls) {
            prefetchedUrls.size < MAX_PENDING_PREFETCHES && prefetchedUrls.add(url)
        }
        if (!isScheduleAllowed) return

        scope.launch {
            try {
                semaphore.withPermit {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(PREFETCH_DECODE_SIZE)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.WRITE_ONLY)
                        .build()

                    val result = context.imageLoader.execute(request)
                    if (result is ErrorResult) {
                        addFailedUrl(url)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                prefetchedUrls.remove(url)
            }
        }
    }

    private fun isFailedRecently(url: String): Boolean {
        val failedAt = failedUrls[url] ?: return false
        val now = SystemClock.DEFAULT.elapsedRealtime()

        if (now - failedAt >= FAILED_RETRY_DELAY_MS) {
            failedUrls.remove(url, failedAt)
            return false
        }

        return true
    }

    private fun addFailedUrl(url: String) {
        val now = SystemClock.DEFAULT.elapsedRealtime()
        failedUrls[url] = now

        if (failedUrls.size <= MAX_FAILED_URLS) return

        while (failedUrls.size > MAX_FAILED_URLS) {
            val oldest = failedUrls.entries.minByOrNull { it.value } ?: break
            failedUrls.remove(oldest.key, oldest.value)
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