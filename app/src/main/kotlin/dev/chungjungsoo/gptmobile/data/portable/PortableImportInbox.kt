package dev.chungjungsoo.gptmobile.data.portable

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the preview until the person confirms or dismisses it.
 * A cold start can publish the document before Compose is collecting.
 */
@Singleton
class PortableImportInbox @Inject constructor() {
    private val _preview = MutableStateFlow<PortablePreview?>(null)
    val preview = _preview.asStateFlow()

    private val _failure = MutableStateFlow<PortableFailure?>(null)
    val failure = _failure.asStateFlow()

    fun offer(result: PortableResult) {
        when (result) {
            is PortableResult.Ok -> {
                _preview.value = result.preview
                _failure.value = null
            }
            is PortableResult.Failure -> {
                _preview.value = null
                _failure.value = result.reason
            }
        }
    }

    fun clear() {
        _preview.value = null
        _failure.value = null
    }
}
