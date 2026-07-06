package com.polyalerts.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polyalerts.alerts.AlertScheduler
import com.polyalerts.data.Repository
import com.polyalerts.data.api.Market
import com.polyalerts.data.db.AlertRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)

    private val _markets = MutableStateFlow<List<Market>>(emptyList())
    val markets: StateFlow<List<Market>> = _markets.asStateFlow()

    private val _loading = MutableStateFlow(false)      // full-screen spinner (first load / category switch / search)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)   // pull-to-refresh indicator
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)  // appending the next page
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _endReached = MutableStateFlow(false)   // no more browse pages / showing search results
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    private val pageSize = 60
    private var currentTagId: Int? = null
    private var offset = 0
    private var searchQuery = ""

    val rules: StateFlow<List<AlertRule>> =
        repo.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Browse a category (null = all). Resets pagination and shows the spinner. */
    fun openCategory(tagId: Int?) {
        currentTagId = tagId
        searchQuery = ""
        reloadBrowse(showSpinner = true)
    }

    private fun reloadBrowse(showSpinner: Boolean) = viewModelScope.launch {
        offset = 0
        _endReached.value = false
        if (showSpinner) { _markets.value = emptyList(); _loading.value = true }
        val page = runCatching { repo.browse(pageSize, 0, currentTagId) }.getOrNull().orEmpty()
        _markets.value = page
        _endReached.value = page.size < pageSize
        _loading.value = false
    }

    /** Called (debounced by the UI) as the search box changes. Blank = back to browse. */
    fun runSearch(query: String) = viewModelScope.launch {
        searchQuery = query.trim()
        if (searchQuery.isBlank()) { reloadBrowse(showSpinner = true); return@launch }
        _markets.value = emptyList()
        _loading.value = true
        _markets.value = runCatching { repo.search(searchQuery) }.getOrNull().orEmpty()
        _endReached.value = true   // search returns a single result set, no paging
        _loading.value = false
    }

    /** Load the next page of the current category (no-op while searching or at the end). */
    fun loadMore() {
        if (searchQuery.isNotBlank() || _loading.value || _loadingMore.value || _endReached.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            val next = offset + pageSize
            val page = runCatching { repo.browse(pageSize, next, currentTagId) }.getOrNull().orEmpty()
            if (page.isNotEmpty()) {
                offset = next
                _markets.value = _markets.value + page
            }
            if (page.size < pageSize) _endReached.value = true
            _loadingMore.value = false
        }
    }

    /** Pull-to-refresh: re-run whichever view is active (search or browse). */
    fun pullRefresh() = viewModelScope.launch {
        _refreshing.value = true
        if (searchQuery.isNotBlank()) {
            runCatching { repo.search(searchQuery) }.getOrNull()?.let { _markets.value = it }
        } else {
            offset = 0
            _endReached.value = false
            runCatching { repo.browse(pageSize, 0, currentTagId) }.getOrNull()?.let {
                _markets.value = it
                _endReached.value = it.size < pageSize
            }
        }
        _refreshing.value = false
    }

    /** Silent auto-refresh of the currently loaded browse items (skips search mode). */
    fun autoRefresh() = viewModelScope.launch {
        if (searchQuery.isNotBlank()) return@launch
        val count = _markets.value.size.coerceAtLeast(pageSize)
        runCatching { repo.browse(count, 0, currentTagId) }.getOrNull()?.let { _markets.value = it }
    }

    /** Manually trigger the price check right now (one-shot worker). */
    fun checkNow() = AlertScheduler.runOnce(getApplication())

    /** Write all rules to the user-chosen file (SAF). No network — local file only. */
    fun exportRules(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val text = repo.exportRulesJson()
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray()); true
            } ?: false
        }.getOrDefault(false)
        onResult(if (ok) "Alerts exported" else "Export failed")
    }

    /** Merge rules from a user-chosen backup file (SAF). Reports how many were added. */
    fun importRules(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val text = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) { onResult("Couldn’t read that file"); return@launch }
        val n = runCatching { repo.importRulesJson(text) }.getOrNull()
        onResult(if (n != null) "Imported $n alert(s)" else "Not a valid backup file")
    }

    fun addAlert(rule: AlertRule) = viewModelScope.launch { repo.saveRule(rule) }
    fun toggleAlert(rule: AlertRule) =
        viewModelScope.launch { repo.updateRule(rule.copy(enabled = !rule.enabled, lastTriggeredAt = null)) }
    fun deleteAlert(rule: AlertRule) = viewModelScope.launch { repo.deleteRule(rule) }
}
