package com.aurora.calculatorvault.feature.privatemedia.domain

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalMediaUriResolverTest {
    @Test
    fun mediaDocumentsImageUriResolvesToMediaStoreImageUri() {
        val original = Uri.parse("content://com.android.providers.media.documents/document/image%3A597")

        val resolved = OriginalMediaUriResolver.resolveDeletableUri(original, VaultMediaType.IMAGE)

        assertEquals("content://media/external/images/media/597", resolved.toString())
    }

    @Test
    fun mediaDocumentsVideoUriResolvesToMediaStoreVideoUri() {
        val original = Uri.parse("content://com.android.providers.media.documents/document/video%3A42")

        val resolved = OriginalMediaUriResolver.resolveDeletableUri(original, VaultMediaType.VIDEO)

        assertEquals("content://media/external/video/media/42", resolved.toString())
    }

    @Test
    fun photoPickerImageUriResolvesToMediaStoreImageUri() {
        val original = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/597")

        val resolved = OriginalMediaUriResolver.resolveDeletableUri(original, VaultMediaType.IMAGE)

        assertEquals("content://media/external/images/media/597", resolved.toString())
    }

    @Test
    fun mediaStoreImageUriRemainsUnchanged() {
        val original = Uri.parse("content://media/external/images/media/597")

        val resolved = OriginalMediaUriResolver.resolveDeletableUri(original, VaultMediaType.IMAGE)

        assertEquals(original, resolved)
    }
}
