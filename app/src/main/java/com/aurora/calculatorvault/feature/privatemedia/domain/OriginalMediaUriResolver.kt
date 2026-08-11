package com.aurora.calculatorvault.feature.privatemedia.domain

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

object OriginalMediaUriResolver {
    fun resolveDeletableUri(
        originalUri: Uri,
        mediaType: VaultMediaType,
    ): Uri? {
        if (originalUri.scheme != "content") return null
        if (originalUri.authority == "media") {
            val pathSegments = originalUri.pathSegments
            val id = pathSegments.lastOrNull()?.toLongOrNull() ?: return originalUri
            return when {
                pathSegments.contains("picker") -> mediaStoreUri(mediaType, id)
                pathSegments.contains("images") || pathSegments.contains("video") -> originalUri
                else -> mediaStoreUri(mediaType, id)
            }
        }
        return originalUri
    }

    private fun mediaStoreUri(mediaType: VaultMediaType, id: Long): Uri =
        ContentUris.withAppendedId(
            when (mediaType) {
                VaultMediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                VaultMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            },
            id,
        )
}
