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
        if (originalUri.authority == MEDIA_DOCUMENTS_AUTHORITY) {
            val documentId = originalUri.pathSegments
                .dropWhile { it != "document" }
                .drop(1)
                .firstOrNull()
                ?.let(Uri::decode)
                ?: originalUri.lastPathSegment?.let(Uri::decode)
            val documentParts = documentId?.split(':', limit = 2).orEmpty()
            val documentType = documentParts.getOrNull(0)
            val id = documentParts.getOrNull(1)?.toLongOrNull() ?: return originalUri
            return when (documentType) {
                "image" -> mediaStoreUri(VaultMediaType.IMAGE, id)
                "video" -> mediaStoreUri(VaultMediaType.VIDEO, id)
                else -> mediaStoreUri(mediaType, id)
            }
        }
        if (originalUri.authority == MEDIA_AUTHORITY) {
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

    private const val MEDIA_AUTHORITY = "media"
    private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
}
