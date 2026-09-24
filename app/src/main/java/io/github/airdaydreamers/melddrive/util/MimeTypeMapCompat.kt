package io.github.airdaydreamers.melddrive.util

import android.webkit.MimeTypeMap
import java.util.Locale

object MimeTypeMapCompat {

    private val extensionToMimeTypeMap = mapOf(
        "3g2" to "video/3gpp2",
        "3gp" to "video/3gpp",
        "3gp2" to "video/3gpp2",
        "3gpp" to "video/3gpp",
        "3gpp2" to "video/3gpp2",
        "avi" to "video/avi",
        "bik" to "video/vnd.radgamettools.bink",
        "bk2" to "video/vnd.radgamettools.bink",
        "divx" to "video/divx",
        "f4v" to "video/mp4",
        "fli" to "video/fli",
        "flv" to "video/x-flv",
        "m2t" to "video/mpeg",
        "m2ts" to "video/mp2t",
        "m2v" to "video/mpeg",
        "m4v" to "video/mp4",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "mp4" to "video/mp4",
        "mp4v" to "video/mp4",
        "mpe" to "video/mpeg",
        "mpeg" to "video/mpeg",
        "mpeg1" to "video/mpeg",
        "mpeg2" to "video/mpeg",
        "mpeg4" to "video/mp4",
        "mpg" to "video/mpeg",
        "mpg4" to "video/mp4",
        "mpv" to "video/x-matroska",
        "mts" to "video/mp2t",
        "mxf" to "application/mxf",
        "ogv" to "video/ogg",
        "qt" to "video/quicktime",
        "rm" to "video/vnd.rn-realvideo",
        "rmvb" to "video/vnd.rn-realvideo",
        "smk" to "video/vnd.radgamettools.smacker",
        "ts" to "video/mp2ts",
        "vob" to "video/x-ms-vob",
        "webm" to "video/webm",
        "wmv" to "video/x-ms-wmv",
    )

    fun getMimeTypeFromExtension(extension: String): String? {
        if (extension.isBlank()) return null
        val cleanExt = extension.lowercase(Locale.ROOT).removePrefix(".")
        return extensionToMimeTypeMap[cleanExt] ?: try {
            MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(cleanExt)
        } catch (_: Throwable) {
            null
        }
    }

    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return getMimeTypeFromExtension(extension) ?: "*/*"
    }

    fun isVideoFile(fileNameOrPath: String): Boolean {
        val mimeType = getMimeType(fileNameOrPath)
        return mimeType.startsWith("video/")
    }
}
