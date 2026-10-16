package dev.chungjungsoo.gptmobile.presentation.ui.home

internal const val HOME_SEARCH_QUERY_KEY = "home_search_query"
internal const val HOME_SEARCH_MODE_KEY = "home_search_mode"
internal const val HOME_SELECT_MODEL_KEY = "home_select_model"
internal const val HOME_DELETE_WARNING_KEY = "home_delete_warning"

internal data class HomeRestoredState(
    val query: String,
    val searchMode: Boolean
)

internal fun restoreHomeSearch(query: String?, searchMode: Boolean?): HomeRestoredState {
    val text = query.orEmpty()
    return HomeRestoredState(
        query = text,
        searchMode = searchMode == true && text.isNotBlank()
    )
}
