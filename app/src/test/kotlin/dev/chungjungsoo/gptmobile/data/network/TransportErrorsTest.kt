package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportErrorsTest {
    @Test
    fun knownFailuresNameTheProblemAndOfferRetry() {
        assertTrue(userVisibleTransportError(UnknownHostException("example.com")).startsWith("Network error: Unable to resolve host."))
        assertTrue(userVisibleTransportError(ConnectException("refused")).contains("Connection refused"))
        assertEquals(
            "Request timed out. Retry to try again.",
            userVisibleTransportError(HttpRequestTimeoutException("https://example.com", 30_000L))
        )
        assertTrue(userVisibleTransportError(SocketTimeoutException("read")).startsWith("Response timed out"))
        assertTrue(userVisibleTransportError(SSLException("handshake")).contains("SSL/TLS"))
        assertEquals(
            "Network error: The connection dropped. Retry to try again.",
            userVisibleTransportError(IOException("reset"))
        )
    }

    @Test
    fun nestedCauseIsMappedAndExistingRetryTextIsKept() {
        val nested = RuntimeException("wrapper", SocketTimeoutException("slow"))
        assertTrue(userVisibleTransportError(nested).startsWith("Response timed out"))

        assertEquals(
            "Please retry the request",
            userVisibleTransportError(IllegalStateException("Please retry the request"))
        )
    }
}
