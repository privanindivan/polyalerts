package com.polyalerts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.polyalerts.R
import com.polyalerts.data.api.Market
import com.polyalerts.ui.theme.BrandBlue
import com.polyalerts.ui.theme.Gold
import com.polyalerts.ui.theme.NoRed
import com.polyalerts.ui.theme.NoRedBg
import com.polyalerts.ui.theme.Outline
import com.polyalerts.ui.theme.SurfaceElevated
import com.polyalerts.ui.theme.TextMuted
import com.polyalerts.ui.theme.TextSecondary
import com.polyalerts.ui.theme.YesGreen
import com.polyalerts.ui.theme.YesGreenBg

/** A category chip mapped to its real Polymarket Gamma tag id (null = all markets). */
private data class Cat(val label: String, val tagId: Int?)

private val categories = listOf(
    Cat("All", null),
    Cat("Politics", 2),
    Cat("Sports", 1),
    Cat("Crypto", 21),
    Cat("Economy", 100328),
    Cat("Tech", 1401),
    Cat("World", 101970),
    Cat("Business", 107),
    Cat("Pop", 596),
)

private const val AUTO_REFRESH_MS = 20_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: AppViewModel) {
    val markets by vm.markets.collectAsStateSafe()
    val loading by vm.loading.collectAsStateSafe()
    val refreshing by vm.refreshing.collectAsStateSafe()
    val loadingMore by vm.loadingMore.collectAsStateSafe()
    val context = LocalContext.current

    // rememberSaveable so the search text and selected category survive tab switches.
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val selected = categories[selectedIndex]
    var alertFor by remember { mutableStateOf<Pair<Market, Int>?>(null) }
    var showSaved by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Show the scroll-to-top button once the user has scrolled a few cards down.
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex >= 4 } }

    // Auto-dismiss the "Alert saved" confirmation after a short beat.
    LaunchedEffect(showSaved) { if (showSaved) { delay(1400); showSaved = false } }

    // Search box (debounced) drives the list. The initial browse load happens once in the ViewModel,
    // so this effect only reacts to the user actually changing the query — and no-ops when the query
    // already matches what's loaded (e.g. on returning to this tab), preserving the list and scroll.
    LaunchedEffect(query) {
        val q = query.trim()
        when {
            q.isBlank() && vm.currentSearch.isNotBlank() -> vm.runSearch("") // user cleared the search
            q.isNotBlank() && q != vm.currentSearch -> { delay(350); vm.runSearch(q) }
        }
    }

    // Silent auto-refresh of live prices while this tab is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_REFRESH_MS)
            vm.autoRefresh()
        }
    }

    // Infinite scroll: load the next page when the last item is nearly visible.
    LaunchedEffect(listState, markets.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (markets.isNotEmpty() && lastVisible >= markets.size - 3) vm.loadMore()
            }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Pinned header — logo + search + category chips stay put while the list scrolls.
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header()
                SearchBar(query) { query = it }
                CategoryChips(selected) { cat ->
                    query = ""
                    selectedIndex = categories.indexOf(cat)
                    vm.openCategory(cat.tagId)
                }
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.pullRefresh() },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (loading && markets.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandBlue)
                            }
                        }
                    } else if (!loading && markets.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (query.isBlank()) "No markets in this category right now."
                                    else "No markets match “$query”.",
                                    color = TextMuted,
                                )
                            }
                        }
                    }

                    items(markets, key = { it.id }) { m ->
                        MarketCard(
                            market = m,
                            onOpen = { openOnPolymarket(context, m.webUrl) },
                            onPickOutcome = { idx -> alertFor = m to idx },
                        )
                    }

                    if (loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandBlue, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // Scroll-to-top button — fades in once you're a few cards deep.
        AnimatedVisibility(
            visible = showScrollTop,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = BrandBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }

        AnimatedVisibility(
            visible = showSaved,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center),
        ) { SavedPopup() }
    }

    alertFor?.let { (m, idx) ->
        SetAlertDialog(
            market = m,
            initialOutcome = idx,
            onDismiss = { alertFor = null },
            onConfirm = { rule ->
                vm.addAlert(rule)
                alertFor = null
                showSaved = true
            },
        )
    }
}

@Composable
private fun SavedPopup() {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = YesGreen,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Alert saved",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text("See the Alerts tab", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Same mark as the launcher icon: the white "P" foreground on the icon's blue background.
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "PolyAlerts",
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("PolyAlerts", fontSize = 22.sp, fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search markets", color = TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceElevated,
            unfocusedContainerColor = SurfaceElevated,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun CategoryChips(selected: Cat, onSelect: (Cat) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { cat ->
            val isSel = cat.label == selected.label
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (isSel) BrandBlue else SurfaceElevated)
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    cat.label,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun MarketCard(market: Market, onOpen: () -> Unit, onPickOutcome: (Int) -> Unit) {
    val outcomes = market.outcomeList().ifEmpty { listOf("Yes", "No") }
    val prices = market.priceList()
    val isLive = market.question?.contains("live", ignoreCase = true) == true

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .clickable { onPickOutcome(0) }   // tapping the card = set an alert (the app's job)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketIcon(market)
            Spacer(Modifier.width(10.dp))
            Text(
                market.question ?: market.slug ?: market.id,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            prices.firstOrNull()?.let { p ->
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${(p * 100).roundToInt()}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    Text("chance", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            outcomes.take(2).forEachIndexed { i, label ->
                val pct = prices.getOrNull(i)?.let { "${(it * 100).toInt()}%" } ?: "—"
                OutcomeButton(
                    label = label,
                    price = pct,
                    yes = i == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickOutcome(i) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isLive) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(NoRed))
                Spacer(Modifier.width(5.dp))
                Text("LIVE", color = NoRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(volumeLabel(market.volume), color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .clickable { onOpen() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Polymarket ↗", color = BrandBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MarketIcon(market: Market) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (!market.image.isNullOrBlank()) {
            AsyncImage(
                model = market.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Text(
                (market.question?.firstOrNull() ?: 'M').uppercase(),
                color = Gold, fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun OutcomeButton(
    label: String,
    price: String,
    yes: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg = if (yes) YesGreen else NoRed
    val bg = if (yes) YesGreenBg else NoRedBg
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text(price, color = fg, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.NotificationsNone,
            contentDescription = "Set alert",
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun volumeLabel(raw: String?): String {
    val v = raw?.toDoubleOrNull() ?: return "—"
    return when {
        v >= 1_000_000_000 -> "$%.1fB Vol".format(v / 1_000_000_000)
        v >= 1_000_000 -> "$%.1fM Vol".format(v / 1_000_000)
        v >= 1_000 -> "$%.0fK Vol".format(v / 1_000)
        else -> "$%.0f Vol".format(v)
    }
}
