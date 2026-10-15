package com.footpredict.local

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Point d’entrée de l’application. Initialise Hilt, la fabrique
 * des Workers (notifications push) et le chargeur d’images Coil :
 * SVG (blasons football-data.org) + cache disque agressif (512 Mo) +
 * cache mémoire plafonné (16 Mo) + décodage borné 128 px → 0 ms au
 * réaffichage, sans appel réseau, sans OOM sur petit heap.
 */
@HiltAndroidApp
class FootPredictApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .memoryCache {
                // Audit OOM 2026-10-14 : 16 Mo fixes au lieu de 25 % du heap
                // (32 Mo sur petit appareil = pression mortelle avec 6 ligues).
                MemoryCache.Builder(this)
                    .maxSizeBytes(16 * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("team_logos"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
