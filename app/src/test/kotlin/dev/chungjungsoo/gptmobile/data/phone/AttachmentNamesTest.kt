package dev.chungjungsoo.gptmobile.data.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentNamesTest {
    @Test
    fun sanitize_stripsPathTraversal() {
        assertEquals("photo.jpg", AttachmentNames.sanitize("../../photo.jpg", "fallback.jpg"))
    }

    @Test
    fun sanitize_emptyName_usesFallback() {
        assertEquals("fallback.jpg", AttachmentNames.sanitize("...", "fallback.jpg"))
    }
}
