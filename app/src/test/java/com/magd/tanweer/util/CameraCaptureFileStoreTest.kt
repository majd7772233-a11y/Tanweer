package com.magd.tanweer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.BuildConfig
import com.magd.tanweer.R
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraCaptureFileStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val fileUriFactory: (Context, File) -> Uri = { _, file -> Uri.fromFile(file) }

    @Test
    fun createsCaptureFileInsideDedicatedCacheDirectory() {
        val target = CameraCaptureFileStore.create(
            context = context,
            captureId = "full-resolution-test",
            uriFactory = fileUriFactory
        )
        try {
            assertTrue(target.file.isFile)
            assertEquals("file", target.contentUri.scheme)
            assertEquals(target.file.canonicalPath, File(target.contentUri.path!!).canonicalPath)
            assertTrue(
                target.file.canonicalPath.startsWith(
                    File(context.cacheDir, "camera_captures").canonicalPath
                )
            )
        } finally {
            CameraCaptureFileStore.delete(context, target.file.absolutePath)
        }
    }

    @Test
    fun providerAuthorityMatchesTheBuildVariantApplicationId() {
        assertEquals(
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            CameraCaptureFileStore.providerAuthority()
        )
    }

    @Test
    fun providerXmlContainsDedicatedCameraCaptureCachePath() {
        val parser = context.resources.getXml(R.xml.file_paths)
        var found = false
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                    parser.name == "cache-path" &&
                    parser.getAttributeValue(null, "name") == "camera_captures" &&
                    parser.getAttributeValue(null, "path") == "camera_captures/"
                ) {
                    found = true
                    break
                }
                parser.next()
            }
        } finally {
            parser.close()
        }
        assertTrue(found)
    }

    @Test
    fun cleanupDeletesOnlyManagedCameraCaptureFiles() {
        val target = CameraCaptureFileStore.create(context, "cleanup-test", fileUriFactory)
        val unrelated = File(context.cacheDir, "capture_unrelated.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val otherDirectory = File(context.cacheDir, "other").apply { mkdirs() }
        val outside = File(otherDirectory, "capture_outside.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        CameraCaptureFileStore.delete(context, target.file.absolutePath)
        CameraCaptureFileStore.delete(context, unrelated.absolutePath)
        CameraCaptureFileStore.delete(context, outside.absolutePath)

        assertFalse(target.file.exists())
        assertTrue(unrelated.exists())
        assertTrue(outside.exists())
        unrelated.delete()
        outside.delete()
    }

    @Test
    fun fullResolutionCaptureIsCopiedToPrivateDraftBeforeTempCleanup() = runBlocking {
        val target = CameraCaptureFileStore.create(context, "resolution-test", fileUriFactory)
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        try {
            val compressed = target.file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            assertTrue(compressed)
            assertTrue(target.file.length() > 0L)

            val draftUri = CameraCaptureFileStore.persistToDraft(
                context = context,
                pageId = "page-resolution-test",
                capturePath = target.file.absolutePath,
                uriFactory = fileUriFactory
            )

            assertNotNull(draftUri)
            assertFalse(target.file.exists())
            val draftFile = File(requireNotNull(draftUri).path!!)
            assertTrue(draftFile.isFile)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(draftFile.absolutePath, bounds)
            assertEquals(1600, bounds.outWidth)
            assertEquals(1200, bounds.outHeight)

            ImageProcessingUtils.deleteDraftSource(context, draftUri)
        } finally {
            bitmap.recycle()
            CameraCaptureFileStore.delete(context, target.file.absolutePath)
        }
    }

    @Test
    fun staleCaptureCleanupRemovesOnlyExpiredManagedFiles() {
        val target = CameraCaptureFileStore.create(context, "stale-test", fileUriFactory)
        target.file.writeBytes(byteArrayOf(1, 2, 3))
        target.file.setLastModified(1L)

        val removed = CameraCaptureFileStore.cleanupStale(
            context = context,
            nowMillis = 100_000L,
            maxAgeMillis = 10_000L
        )

        assertEquals(1, removed)
        assertFalse(target.file.exists())
    }
}
