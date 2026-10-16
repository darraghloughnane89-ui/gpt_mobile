package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import dev.chungjungsoo.gptmobile.data.hermes.HermesAttachment
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesShareRequestTest {
    @Test
    fun shareRequest_textAndFile_keepsBoth() {
        val request = shareRequest(
            text = "Please read this",
            subject = "Lease",
            files = listOf(HermesAttachment("lease.pdf", "/tmp/lease.pdf", "application/pdf"))
        )
        assertEquals(HermesHandoffKind.FILE, request?.kind)
        assertEquals("Lease\n\nPlease read this", request?.text)
        assertEquals("lease.pdf", request?.attachments?.single()?.displayName)
    }

    @Test
    fun shareRequest_empty_returnsNull() {
        assertNull(shareRequest(null, "   ", emptyList()))
    }
}
