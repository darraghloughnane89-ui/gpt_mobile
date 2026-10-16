package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException

internal fun userVisibleTransportError(error: Throwable): String {
    val match = generateSequence(error) { it.cause }.take(8).firstOrNull(::isKnownTransportFailure) ?: error
    val message = when (match) {
        is UnknownHostException -> "Network error: Unable to resolve host."
        is UnresolvedAddressException -> "Network error: Unable to resolve address. Check your internet connection."
        is ConnectException -> "Network error: Connection refused. Check the API URL."
        is HttpRequestTimeoutException -> "Request timed out."
        is SocketTimeoutException -> "Response timed out while waiting for the next chunk."
        is SSLException -> "Network error: SSL/TLS connection failed."
        is SocketException,
        is IOException -> "Network error: The connection dropped."
        else -> match.message?.takeIf { it.isNotBlank() } ?: "Unknown network error"
    }
    return if (message.contains("retry", ignoreCase = true)) message else "$message Retry to try again."
}

private fun isKnownTransportFailure(error: Throwable): Boolean = error is UnknownHostException ||
    error is UnresolvedAddressException ||
    error is ConnectException ||
    error is HttpRequestTimeoutException ||
    error is SocketTimeoutException ||
    error is SSLException ||
    error is SocketException ||
    error is IOException
