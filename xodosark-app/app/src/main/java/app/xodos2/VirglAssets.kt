package app.xodos2

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object VirglAssets {
    private const val ASSET_ROOT = "virgl"
    private const val LIVE_LINK_NAME = "virgl"           // symlink to current payload
    private const val PAYLOAD_DIR = "virgl-payload"      // actual directory (stable name)
    private const val STAGING_DIR = "virgl-staging"      // temp while copying

    private val SHARED_LIBS = arrayOf("libvirglrenderer.so", "libepoxy.so")
    private val ANGLE_BACKENDS = arrayOf("vulkan", "gl", "vulkan-null")
    private val ANGLE_LIBS = arrayOf(
        "libEGL_angle.so", "libGLESv1_CM_angle.so", "libGLESv2_angle.so",
        "libfeature_support_angle.so",  "libcrcfix.so"
    )

    fun syncFromAssetsIfNeeded(context: Context) {
        synchronized(this) {
            val am = context.assets
            if (am.list(ASSET_ROOT).isNullOrEmpty()) return

            val abi = "arm64-v8a"
            val filesDir = context.filesDir
            val linkFile = File(filesDir, LIVE_LINK_NAME)
            val payloadDir = File(filesDir, PAYLOAD_DIR)

            // 1. Check if symlink already points to the payload and binaries exist
            val currentTarget = resolveSymlinkTarget(linkFile)
            val binInPayload = File(payloadDir, "bin/virgl_test_server_android")
            if (currentTarget == payloadDir && binInPayload.isFile && binInPayload.canExecute()) {
                // Already up to date – nothing to do
                return
            }

            // 2. Remove any leftover old symlink or directory at "virgl"
            if (linkFile.exists()) {
                if (linkFile.isDirectory && !isSymlink(linkFile)) {
                    // Migrate legacy plain directory to payload
                    if (!linkFile.renameTo(payloadDir)) {
                        // fallback: delete and rebuild
                        linkFile.deleteRecursively()
                    }
                } else {
                    linkFile.delete()
                }
            }

            // 3. Ensure payload directory exists (may already from migration)
            if (!payloadDir.exists()) {
                payloadDir.mkdirs()
            }

            // 4. Stage the new payload into a temporary folder (to avoid partial copies)
            val stagingRoot = File(filesDir, STAGING_DIR)
            stagingRoot.deleteRecursively()
            val stagingBinDir = File(stagingRoot, "bin").apply { mkdirs() }
            val stagingLibDir = File(stagingRoot, "lib").apply { mkdirs() }

            try {
                // Copy main binary
                copyAssetFile(am, "$ASSET_ROOT/$abi/virgl_test_server_android",
                    File(stagingBinDir, "virgl_test_server_android"))
                File(stagingBinDir, "virgl_test_server_android").setExecutable(true, false)

                // Copy render server if present
                try {
                    copyAssetFile(am, "$ASSET_ROOT/$abi/virgl_render_server",
                        File(stagingBinDir, "virgl_render_server"))
                    File(stagingBinDir, "virgl_render_server").setExecutable(true, false)
                } catch (_: IOException) {}

                // Shared libraries
                for (lib in SHARED_LIBS) {
                    try { copyAssetFile(am, "$ASSET_ROOT/$abi/$lib", File(stagingLibDir, lib)) } catch (_: IOException) {}
                    try { copyAssetFile(am, "$ASSET_ROOT/$abi/$lib", File(stagingBinDir, lib)) } catch (_: IOException) {}
                }

                // ANGLE libs
                for (backend in ANGLE_BACKENDS) {
                    val assetDir = "$ASSET_ROOT/$abi/angle/$backend"
                    val destDir = File(stagingRoot, "angle/$backend").apply { mkdirs() }
                    for (lib in ANGLE_LIBS) {
                        val assetPath = "$assetDir/$lib"
                        try { copyAssetFile(am, assetPath, File(destDir, lib)) } catch (_: IOException) {}
                    }
                }

                // 5. Replace payload atomically: delete old, rename staging -> payload
                payloadDir.deleteRecursively()
                if (!stagingRoot.renameTo(payloadDir)) {
                    // If rename fails, just use staging as payload (worst case)
                    payloadDir.deleteRecursively()
                    stagingRoot.renameTo(payloadDir)
                }
            } finally {
                stagingRoot.deleteRecursively() // clean up if anything left
            }

            // 6. Create/update the symlink: virgl -> virgl-payload (relative)
            try {
                if (linkFile.exists()) {
                    linkFile.delete()
                }
                Os.symlink(PAYLOAD_DIR, linkFile.absolutePath)
            } catch (_: ErrnoException) {
                // fallback: if symlink fails, try to rename payload dir to "virgl" directly
                payloadDir.renameTo(linkFile)
            }
        }
    }

    private fun resolveSymlinkTarget(link: File): File? {
        if (!link.exists()) return null
        if (!isSymlink(link)) return if (link.isDirectory) link else null
        return try {
            val target = Os.readlink(link.absolutePath)
            if (target.startsWith("/")) File(target) else File(link.parentFile, target)
        } catch (_: ErrnoException) {
            null
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val parent = file.parentFile
            if (parent == null) false
            else Os.lstat(file.absolutePath).st_mode and android.system.OsConstants.S_IFMT == android.system.OsConstants.S_IFLNK
        } catch (_: ErrnoException) {
            false
        }
    }

    private fun copyAssetFile(am: android.content.res.AssetManager, path: String, out: File) {
        out.parentFile?.mkdirs()
        am.open(path).use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        }
    }
}