package com.polyalerts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polyalerts.alerts.AlertScheduler
import com.polyalerts.data.Repository
import com.polyalerts.data.ShareRule
import com.polyalerts.data.signature
import com.polyalerts.data.api.Market
import com.polyalerts.data.db.AlertKind
import com.polyalerts.data.db.AlertRule
import com.polyalerts.data.db.Comparator
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

    /** The active search text (blank = browsing a category). Lets the UI avoid re-fetching on return. */
    val currentSearch: String get() = searchQuery

    init {
        // Load the initial "All" browse once, when the ViewModel is created. Because the ViewModel
        // outlives tab switches, the list is not re-fetched every time the Markets tab is reopened.
        openCategory(null)
    }

    val rules: StateFlow<List<AlertRule>> =
        repo.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live market data for the markets referenced by saved alerts (Alerts tab), keyed by marketId.
    private val _alertMarkets = MutableStateFlow<Map<String, Market>>(emptyMap())
    val alertMarkets: StateFlow<Map<String, Market>> = _alertMarkets.asStateFlow()

    // Alerts decoded from a scanned QR, held for the user to preview before adding.
    private val _incoming = MutableStateFlow<List<AlertRule>?>(null)
    val incoming: StateFlow<List<AlertRule>?> = _incoming.asStateFlow()

    /** Fetch current prices for the markets behind the saved alerts, so the Alerts tab can show
     *  the live probability next to each target. Per-market fetch; one failure doesn't sink the rest. */
    fun refreshAlertPrices() = viewModelScope.launch {
        val ids = rules.value.map { it.marketId }.distinct()
        if (ids.isEmpty()) { _alertMarkets.value = emptyMap(); return@launch }
        val fetched = _alertMarkets.value.toMutableMap()
        for (id in ids) runCatching { repo.market(id) }.getOrNull()?.let { fetched[id] = it }
        _alertMarkets.value = fetched.filterKeys { it in ids }   // drop no-longer-referenced markets
    }

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

    /** Resolve scanned share-rules into full alerts (re-fetching each market's question/image
     *  by marketId), and hold them for the user to preview before adding. */
    fun prepareIncoming(shared: List<ShareRule>) = viewModelScope.launch {
        _incoming.value = shared.map { sr ->
            val market = runCatching { repo.market(sr.m) }.getOrNull()
            AlertRule(
                marketId = sr.m,
                slug = sr.s.ifBlank { market?.slug ?: "" },
                question = market?.question ?: sr.s.ifBlank { "Shared alert" },
                imageUrl = market?.image,
                outcomeIndex = sr.o,
                outcomeLabel = sr.l,
                kind = runCatching { AlertKind.valueOf(sr.k) }.getOrDefault(AlertKind.THRESHOLD),
                comparator = runCatching { Comparator.valueOf(sr.c) }.getOrDefault(Comparator.ABOVE),
                target = sr.t,
                baselinePrice = sr.b,
            )
        }
    }

    /** Save the alerts the receiver chose from a scanned code, skipping ones that already exist. */
    fun confirmIncoming(selected: List<AlertRule>, onResult: (Int) -> Unit) = viewModelScope.launch {
        val existing = rules.value.map { it.signature() }.toHashSet()
        var added = 0
        selected.forEach { r ->
            if (existing.add(r.signature())) { repo.saveRule(r); added++ }
        }
        _incoming.value = null
        onResult(added)
    }

    /** Discard scanned alerts without adding them. */
    fun cancelIncoming() { _incoming.value = null }

    fun addAlert(rule: AlertRule) = viewModelScope.launch { repo.saveRule(rule) }
    fun toggleAlert(rule: AlertRule) =
        viewModelScope.launch { repo.updateRule(rule.copy(enabled = !rule.enabled, lastTriggeredAt = null)) }
    fun deleteAlert(rule: AlertRule) = viewModelScope.launch { repo.deleteRule(rule) }
}
