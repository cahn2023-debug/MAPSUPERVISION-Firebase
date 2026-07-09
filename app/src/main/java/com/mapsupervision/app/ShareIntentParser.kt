package com.mapsupervision.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.webkit.MimeTypeMap
import com.mapsupervision.app.workspace.IncomingSharePayload
import com.mapsupervision.domain.model.MediaType
import java.util.Locale
import java.util.UUID

internal fun parseIncomingSharePayload(intent: Intent?): IncomingSharePayload? {
    if (intent == null) return null
    val action = intent.action ?: return null
    val mimeType = intent.type?.trim().orEmpty()
    val uris = when (action) {
        Intent.ACTION_SEND -> buildList {
            intent.parcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { add(it) }
            extractUrisFromClipData(intent.clipData).forEach { add(it) }
        }
        Intent.ACTION_SEND_MULTIPLE -> buildList {
            intent.parcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM)?.forEach { add(it) }
            extractUrisFromClipData(intent.clipData).forEach { add(it) }
        }
        else -> emptyList()
    }
    return buildIncomingSharePayload(action, mimeType, uris)
}

internal fun parseIncomingSharePayload(
    context: Context,
    intent: Intent?
): IncomingSharePayload? {
    if (intent == null) return null
    val action = intent.action ?: return null
    val mimeType = intent.type?.trim().orEmpty()
    val uris = when (action) {
        Intent.ACTION_SEND -> buildList {
            intent.parcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { add(it) }
            extractUrisFromClipData(intent.clipData).forEach { add(it) }
        }
        Intent.ACTION_SEND_MULTIPLE -> buildList {
            intent.parcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM)?.forEach { add(it) }
            extractUrisFromClipData(intent.clipData).forEach { add(it) }
        }
        else -> emptyList()
    }
    return buildIncomingSharePayload(
        action = action,
        mimeType = mimeType,
        uriStrings = uris.map(Uri::toString),
        resolveMimeType = { uriString -> resolveIncomingShareMimeType(context, Uri.parse(uriString)) }
    )
}

internal fun buildIncomingSharePayload(
    action: String?,
    mimeType: String?,
    uris: List<Uri>
): IncomingSharePayload? {
    return buildIncomingSharePayload(
        action = action,
        mimeType = mimeType,
        uriStrings = uris.map(Uri::toString)
    )
}

internal fun buildIncomingSharePayload(
    action: String?,
    mimeType: String?,
    uriStrings: List<String>,
    resolveMimeType: ((String) -> String?)? = null
): IncomingSharePayload? {
    val normalizedUriStrings = buildIncomingShareUriStrings(
        action = action,
        mimeType = mimeType,
        uriStrings = uriStrings,
        resolveMimeType = resolveMimeType
    ) ?: return null

    return IncomingSharePayload(
        id = UUID.randomUUID().toString(),
        uris = normalizedUriStrings.map(Uri::parse),
        mimeType = mimeType?.trim().takeUnless { it.isNullOrBlank() }
    )
}

internal fun buildIncomingShareUriStrings(
    action: String?,
    mimeType: String?,
    uriStrings: List<String>,
    resolveMimeType: ((String) -> String?)? = null
): List<String>? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

    val declaredMimeType = mimeType?.trim().orEmpty()
    val supportsMedia = declaredMimeType.isBlank() ||
        declaredMimeType == "*/*" ||
        declaredMimeType.startsWith("image/", ignoreCase = true) ||
        declaredMimeType.startsWith("video/", ignoreCase = true)
    if (!supportsMedia) return null

    val normalizedUris = uriStrings
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalizedUris.isEmpty()) return null

    val filteredUris = when {
        resolveMimeType != null -> normalizedUris.filter { uriString ->
            val resolvedMimeType = resolveMimeType(uriString)
            isSupportedSharedMediaMimeType(resolvedMimeType) ||
                (resolvedMimeType.isNullOrBlank() &&
                    (declaredMimeType.startsWith("image/", ignoreCase = true) ||
                        declaredMimeType.startsWith("video/", ignoreCase = true)))
        }
        declaredMimeType.startsWith("image/", ignoreCase = true) ||
            declaredMimeType.startsWith("video/", ignoreCase = true) -> normalizedUris
        else -> emptyList()
    }
    if (filteredUris.isEmpty()) return null

    return filteredUris
}

internal fun resolveIncomingShareMimeType(context: Context, uri: Uri): String? {
    val resolved = context.contentResolver.getType(uri)?.trim().orEmpty()
    if (resolved.isNotBlank()) return resolved

    val extension = uri.path
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()
    if (extension.isBlank()) return null

    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.trim()
        ?.takeUnless { it.isNullOrBlank() }
}

internal fun resolveIncomingShareMediaType(
    context: Context,
    uri: Uri,
    fallbackMimeType: String? = null
): MediaType? {
    val resolvedMimeType = resolveIncomingShareMimeType(context, uri)
        ?: fallbackMimeType?.trim().takeUnless { it.isNullOrBlank() }
        ?: return null
    return when {
        resolvedMimeType.startsWith("video/", ignoreCase = true) -> MediaType.VIDEO
        resolvedMimeType.startsWith("image/", ignoreCase = true) -> MediaType.IMAGE
        else -> null
    }
}

internal fun isSupportedSharedMediaMimeType(mimeType: String?): Boolean {
    val normalized = mimeType?.trim().orEmpty()
    return normalized.startsWith("image/", ignoreCase = true) ||
        normalized.startsWith("video/", ignoreCase = true)
}

private fun extractUrisFromClipData(clipData: ClipData?): List<Uri> {
    if (clipData == null) return emptyList()
    return buildList {
        for (index in 0 until clipData.itemCount) {
            clipData.getItemAt(index)?.uri?.let { add(it) }
        }
    }
}

private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(name: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? T
    }
}

private inline fun <reified T : Parcelable> Intent.parcelableArrayListExtraCompat(name: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }
}
