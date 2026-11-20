package com.renkai.app

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.renkai.app.data.CatalogApi
import com.renkai.app.data.Comment
import com.renkai.app.data.CommentStore
import com.renkai.app.data.Episode
import com.renkai.app.data.Title
import com.renkai.app.ui.CommentComposer
import com.renkai.app.ui.CommentRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private data class PlayTarget(val title: Title, val episode: Episode)

/** ID judul sintetis untuk "Putar link"; komentar dimatikan untuk judul ini. */
private const val CUSTOM_LINK_ID = "tautan-kustom"

@Composable
fun RenkaiApp(autoTitle: String? = null, autoEp: Int = 1) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val playbackRequests = remember { PlaybackRequestGate() }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            // Subtitle otomatis mengikuti bahasa aplikasi (fallback: Indonesia, Inggris).
            // Kualitas video bersifat adaptif (ABR) selama tidak dikunci lewat menu pemutar.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguages(*AppLanguage.subtitlePreference(context))
                .build()
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<PlayTarget?>(null) }
    var selected by remember { mutableStateOf<Title?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val favs = remember { mutableStateListOf<Title>() }

    var catalog by remember { mutableStateOf<List<Title>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(retryKey) {
        try {
            catalog = CatalogApi.fetchCatalog(force = retryKey > 0)
            loadError = null
        } catch (e: Exception) {
            loadError = CatalogApi.lastError ?: (e.message ?: "Gagal memuat katalog")
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = "Gagal memutar: ${e.errorCodeName}" }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) error = null }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    fun play(t: Title, ep: Episode) {
        error = null
        playing = PlayTarget(t, ep)
        val requestId = playbackRequests.issue()
        // Setiap episode baru mulai lagi dari kualitas otomatis (ABR).
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        scope.launch {
            val url = CatalogApi.pickDoor(ep.playlist)
            if (!playbackRequests.isCurrent(requestId)) return@launch
            val mime = when {
                url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            player.setMediaItem(MediaItem.Builder().setUri(url).apply { mime?.let { setMimeType(it) } }.build())
            player.prepare(); player.play()
        }
    }
    var autoDone by remember { mutableStateOf(false) }
    LaunchedEffect(catalog) {
        val list = catalog
        if (list != null && autoTitle != null && !autoDone) {
            autoDone = true
            val t = list.firstOrNull { it.id == autoTitle || it.title.contains(autoTitle, ignoreCase = true) }
            val ep = t?.episodes?.firstOrNull { it.number == autoEp } ?: t?.episodes?.firstOrNull()
            if (t != null && ep != null) play(t, ep)
        }
    }

    fun playLink(url: String) {
        val t = Title(
            id = CUSTOM_LINK_ID, title = "Tautan kustom",
            episodes = listOf(Episode(1, url)),
        )
        play(t, t.episodes[0])
    }
    fun toggleFav(t: Title) {
        val i = favs.indexOfFirst { it.id == t.id }
        if (i >= 0) favs.removeAt(i) else favs.add(t)
    }
    fun closePlayer() { playbackRequests.invalidate(); player.pause(); playing = null }

    BackHandler(enabled = playing != null) { closePlayer() }
    BackHandler(enabled = playing == null && selected != null) { selected = null }
    BackHandler(enabled = playing == null && selected == null && searchOpen) { searchOpen = false }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = landscape && playing != null
    LaunchedEffect(fullscreen) {
        val w = (context as? Activity)?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        if (fullscreen) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    val cur = playing
    val sel = selected
    val cat = catalog
    when {
        fullscreen -> {
            // Tombol kualitas/subtitle hanya tampil bersama kontrol pemutar.
            var controlsVisible by remember { mutableStateOf(true) }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PlayerSurface(player, Modifier.fillMaxSize(), onControlsVisibility = { controlsVisible = it })
                if (controlsVisible) {
                    PlaybackSettingsButton(
                        player,
                        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                        compact = true,
                    )
                }
            }
        }
        cur != null -> PlayerScreen(
            player, cur, error,
            favs.any { it.id == cur.title.id },
            onBack = { closePlayer() },
            onFav = { toggleFav(cur.title) },
            onPlayEp = { ep -> play(cur.title, ep) },
        )
        sel != null -> DetailScreen(
            sel,
            favs.any { it.id == sel.id },
            onBack = { selected = null },
            onFav = { toggleFav(sel) },
            onPlayEp = { ep -> play(sel, ep) },
        )
        cat == null && loadError == null -> LoadingScreen()
        cat == null -> ErrorScreen(loadError ?: "Gagal memuat katalog") { retryKey++ }
        else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Konten memenuhi layar penuh (tembus ke belakang status bar dan navbar);
            // jarak aman diberikan lewat contentPadding, bukan memotong area konten.
            when {
                searchOpen -> SearchTab(cat, onSelect = { selected = it }, onPlayLink = ::playLink)
                tab == 0 -> HomeTab(
                    cat, favs, onSelect = { selected = it }, onPlay = { t, ep -> play(t, ep) },
                    onFav = ::toggleFav, onSearch = { searchOpen = true },
                )
                tab == 1 -> ScheduleTab(cat, onSelect = { selected = it }, onSearch = { searchOpen = true })
                tab == 2 -> CollectionTab(favs, onSelect = { selected = it }, onSearch = { searchOpen = true })
                tab == 3 -> DownloadsTab(onSearch = { searchOpen = true })
                else -> ProfileTab(favs.size, cat.size, onSearch = { searchOpen = true })
            }
            FloatingNavBar(
                MainNavItems, if (searchOpen) -1 else tab,
                onSelect = { tab = it; searchOpen = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Padding isi daftar: lewati status bar di atas dan navbar melayang di bawah. */
@Composable
private fun mainContentPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = top + 8.dp, bottom = bottom + FloatingNavBarHeight + 16.dp)
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Logo()
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("Memuat katalog…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Logo()
            Text("Katalog belum bisa dimuat.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(6.dp)); Text("Coba lagi", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeTab(
    catalog: List<Title>,
    favs: List<Title>,
    onSelect: (Title) -> Unit,
    onPlay: (Title, Episode) -> Unit,
    onFav: (Title) -> Unit,
    onSearch: () -> Unit,
) {
    val heroPicks = remember(catalog) {
        catalog.filter { it.banner != null }.sortedByDescending { it.score ?: 0.0 }.take(8)
    }
    val spotlight = remember(catalog) { catalog.sortedByDescending { it.score ?: 0.0 }.take(15) }
    val trending = remember(catalog) {
        catalog.sortedWith(compareByDescending<Title> { it.year ?: 0 }.thenByDescending { it.score ?: 0.0 }).take(15)
    }
    val mostPopular = remember(catalog) {
        catalog.sortedWith(compareByDescending<Title> { it.episodes.size }.thenByDescending { it.score ?: 0.0 }).take(15)
    }
    val mostWatched = remember(catalog) {
        catalog.filter { it.status == "FINISHED" }.sortedByDescending { it.score ?: 0.0 }.take(15)
    }
    val topAiring = remember(catalog) {
        val airing = catalog.filter { it.status == "RELEASING" }.sortedByDescending { it.score ?: 0.0 }
        val fill = catalog.filter { it.status != "RELEASING" }
            .sortedWith(compareByDescending<Title> { it.year ?: 0 }.thenByDescending { it.score ?: 0.0 })
        (airing + fill).take(15)
    }
    val cal = java.util.Calendar.getInstance()
    val top10Today = remember(catalog) {
        rotateTop(catalog.sortedByDescending { it.score ?: 0.0 }, cal.get(java.util.Calendar.DAY_OF_YEAR))
    }
    val top10Week = remember(catalog) {
        rotateTop(
            catalog.sortedWith(compareByDescending<Title> { it.episodes.size }.thenByDescending { it.score ?: 0.0 }),
            cal.get(java.util.Calendar.WEEK_OF_YEAR),
        )
    }
    val top10Month = remember(catalog) {
        rotateTop(
            catalog.sortedWith(compareByDescending<Title> { it.year ?: 0 }.thenByDescending { it.score ?: 0.0 }),
            cal.get(java.util.Calendar.MONTH) + 1,
        )
    }
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingNavBarHeight + 16.dp
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item { SpotlightHero(heroPicks, favs, onSelect = onSelect, onFav = onFav, onSearch = onSearch) }
        item { PosterRail("Spotlight", spotlight, onSelect) }
        item { PosterRail("Trending", trending, onSelect) }
        item { PosterRail("Most Popular", mostPopular, onSelect) }
        item { PosterRail("Most Watched", mostWatched, onSelect) }
        item { PosterRail("Top Airing", topAiring, onSelect) }
        item { PosterRail("Top 10 Today", top10Today, onSelect) }
        item { PosterRail("Top 10 Week", top10Week, onSelect) }
        item { PosterRail("Top 10 Month", top10Month, onSelect) }
        item {
            Text(
                "${catalog.size} judul · ${catalog.sumOf { it.episodes.size }} episode",
                Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
        }
    }
}

/** Geser jendela 10 besar dari kumpulan 40 teratas; benihnya hari/minggu/bulan berjalan. */
private fun rotateTop(sorted: List<Title>, seed: Int): List<Title> {
    val pool = sorted.take(40)
    if (pool.size <= 10) return pool
    val start = seed % pool.size
    return (pool.drop(start) + pool.take(start)).take(10)
}

/**
 * Panggung utama ala aplikasi contoh: banner penuh selebar layar sampai ke tepi atas,
 * judul besar di tengah, lencana, baris info, genre, lalu Bagikan · Detail · Koleksi.
 * Bergeser otomatis tiap 6 detik; bisa digeser manual. Warna tetap Renkai (kuning).
 */
@Composable
private fun SpotlightHero(
    picks: List<Title>,
    favs: List<Title>,
    onSelect: (Title) -> Unit,
    onFav: (Title) -> Unit,
    onSearch: () -> Unit,
) {
    if (picks.isEmpty()) {
        AppHeader(onSearch, Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp))
        return
    }
    val pagerState = rememberPagerState(pageCount = { picks.size })
    LaunchedEffect(pagerState, picks.size) {
        while (true) {
            delay(6000)
            if (!pagerState.isScrollInProgress && picks.size > 1) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % picks.size)
            }
        }
    }
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().height(560.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val t = picks[page]
            Box(Modifier.fillMaxSize()) {
                AsyncImage(t.banner ?: t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.32f),
                            0.42f to Color.Transparent,
                            0.76f to MaterialTheme.colorScheme.background.copy(alpha = 0.74f),
                            1.0f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        t.title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroChip(if (t.episodes.size == 1) "Film" else "Serial")
                        t.statusLabel?.let { HeroChip(it) }
                        t.score?.let { HeroChip(String.format(Locale.US, "%.1f", it)) }
                    }
                    val meta = listOfNotNull(
                        t.year?.toString(),
                        t.statusLabel,
                        t.score?.let { "★ " + String.format(Locale.US, "%.1f", it) },
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) {
                        Text(meta, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
                    }
                    if (t.genres.isNotEmpty()) {
                        Text(t.genres.take(3).joinToString(", "), color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        HeroSideAction(Icons.Rounded.Share, "Bagikan") {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Nonton ${t.title} di Renkai")
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan")) }
                        }
                        Button(
                            onClick = { onSelect(t) },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(50.dp).width(180.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Rounded.Info, null, Modifier.size(20.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Detail", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        val fav = favs.any { it.id == t.id }
                        HeroSideAction(
                            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Koleksi",
                            tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                        ) { onFav(t) }
                    }
                }
            }
        }
        AppHeader(onSearch, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 4.dp))
    }
}

@Composable
private fun HeroChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroSideAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = tint)
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/** Satu rak poster horizontal dengan judul besar, seperti aplikasi contoh. */
@Composable
private fun PosterRail(title: String, list: List<Title>, onSelect: (Title) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), fontSize = 21.sp, fontWeight = FontWeight.Black)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { t -> RailPoster(t, onSelect) }
        }
    }
}

@Composable
private fun RailPoster(t: Title, onSelect: (Title) -> Unit) {
    Box(
        Modifier.width(122.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222225)).clickable { onSelect(t) },
    ) {
        if (t.poster != null) {
            AsyncImage(t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.52f to Color.Transparent, 1.0f to Color.Black.copy(alpha = 0.84f)),
            ),
        )
        Text(
            t.title, Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchTab(catalog: List<Title>, onSelect: (Title) -> Unit, onPlayLink: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val isLink = q.trim().startsWith("http", true)
    val results = remember(q, catalog) {
        if (q.isBlank()) emptyList()
        else catalog.filter { it.title.contains(q, true) || it.genres.any { g -> g.contains(q, true) } }.take(60)
    }
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = mainContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Logo() }
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), label = { Text("Cari judul, genre, atau tempel link .m3u8 / .mpd") },
            )
        }
        if (isLink) {
            val url = q.trim()
            item {
                Button(
                    onClick = { onPlayLink(url) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text("Putar link", fontWeight = FontWeight.Bold) }
            }
        } else if (q.isBlank()) {
            item { Text("Ketik untuk mencari di ${catalog.size} judul.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (results.isEmpty()) {
            item { Text("Tidak ada judul yang cocok dengan “$q”.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(results) { t -> ListRow(t, onSelect) }
        }
    }
}

@Composable
private fun CollectionTab(favs: List<Title>, onSelect: (Title) -> Unit, onSearch: () -> Unit) {
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = mainContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AppHeader(onSearch) }
        item { Text("Koleksi", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (favs.isEmpty()) {
            item { Text("Belum ada koleksi. Buka judul lalu tekan “Koleksi” untuk menyimpannya di sini (tersimpan selama aplikasi terbuka).", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(favs) { t -> ListRow(t, onSelect) }
        }
    }
}

@Composable
private fun ScheduleTab(catalog: List<Title>, onSelect: (Title) -> Unit, onSearch: () -> Unit) {
    val airing = remember(catalog) { catalog.filter { it.status == "RELEASING" }.take(60) }
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = mainContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AppHeader(onSearch) }
        item { Text("Jadwal", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { Text("Judul yang sedang tayang.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (airing.isEmpty()) {
            item { Text("Belum ada judul yang sedang tayang di katalog.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(airing) { t -> ListRow(t, onSelect) }
        }
    }
}

@Composable
private fun DownloadsTab(onSearch: () -> Unit) {
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = mainContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AppHeader(onSearch) }
        item { Text("Unduhan", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item {
            Text(
                "Belum ada unduhan. Fitur menonton offline belum tersedia di versi ini.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileTab(favCount: Int, titleCount: Int, onSearch: () -> Unit) {
    val context = LocalContext.current
    fun langName(code: String) = Locale.forLanguageTag(code)
        .getDisplayLanguage(Locale.forLanguageTag("id")).replaceFirstChar { it.titlecase(Locale.ROOT) }
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = mainContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AppHeader(onSearch) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Text("Tamu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Profil lokal di perangkat ini", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { InfoRow("Koleksi", "$favCount judul") }
        item { InfoRow("Katalog", "$titleCount judul") }
        item { InfoRow("Bahasa aplikasi", langName(AppLanguage.current(context))) }
        item { InfoRow("Bahasa subtitle", AppLanguage.subtitlePreference(context).joinToString(", ") { langName(it) }) }
        item { InfoRow("Versi", BuildConfig.VERSION_NAME) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailScreen(
    t: Title, fav: Boolean,
    onBack: () -> Unit, onFav: () -> Unit, onPlayEp: (Episode) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(210.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).background(Color.Black)) {
                if (t.banner != null) {
                    AsyncImage(t.banner, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
                IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White)
                }
                Text(
                    t.title, Modifier.align(Alignment.BottomStart).padding(20.dp),
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val meta = listOfNotNull(
                    t.matchPercent?.let { "$it% cocok" },
                    t.year?.toString(),
                    t.statusLabel,
                    "${t.episodes.size} episode",
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (t.genres.isNotEmpty()) Text(t.genres.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { t.episodes.firstOrNull()?.let(onPlayEp) },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(50.dp),
                    ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(4.dp)); Text("Putar", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = onFav, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(50.dp)) {
                        Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null); Spacer(Modifier.size(4.dp)); Text("Koleksi")
                    }
                }
                t.synopsis?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) }
                Text("Episode", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        items(t.episodes) { ep -> EpisodeRow(ep, current = false) { onPlayEp(ep) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlayerScreen(
    player: Player, target: PlayTarget, error: String?, fav: Boolean,
    onBack: () -> Unit, onFav: () -> Unit, onPlayEp: (Episode) -> Unit,
) {
    val t = target.title
    val epNo = target.episode.number
    val idx = t.episodes.indexOfFirst { it.number == epNo }
    val prev = t.episodes.getOrNull(idx - 1)
    val next = t.episodes.getOrNull(idx + 1)

    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val store = remember { CommentStore(context) }
    val canComment = t.id != CUSTOM_LINK_ID
    val comments = remember(t.id, epNo) {
        mutableStateListOf<Comment>().apply { addAll(store.load(t.id, epNo)) }
    }
    var draft by rememberSaveable(t.id, epNo) { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val showComments = canComment && tab == 1

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        comments.add(0, store.add(t.id, epNo, text))
        draft = ""
        focus.clearFocus()
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
            Text(t.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerSurface(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Episode $epNo" + (target.episode.title?.let { " — $it" } ?: ""),
                            fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(t.year?.toString(), t.statusLabel, "${t.episodes.size} episode").joinToString(" · ")
                        Text(meta, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onFav, shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(if (fav) "Di koleksi" else "Koleksi", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            if (error != null) {
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { prev?.let(onPlayEp) }, enabled = prev != null,
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, null); Spacer(Modifier.size(4.dp)); Text("Sebelumnya", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { next?.let(onPlayEp) }, enabled = next != null,
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Berikutnya", maxLines = 1); Spacer(Modifier.size(4.dp)); Icon(Icons.Rounded.SkipNext, null)
                    }
                }
            }
            item { PlaybackSettingsButton(player, Modifier.fillMaxWidth()) }
            if (canComment) {
                item {
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Tab(
                            selected = tab == 0, onClick = { tab = 0 },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Episode (${t.episodes.size})", fontWeight = FontWeight.Bold) },
                        )
                        Tab(
                            selected = tab == 1, onClick = { tab = 1 },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Komentar (${comments.size})", fontWeight = FontWeight.Bold) },
                        )
                    }
                }
            } else {
                item { Text("Episode", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            if (!showComments) {
                items(t.episodes) { ep ->
                    EpisodeRow(ep, current = ep.number == epNo, modifier = Modifier) { onPlayEp(ep) }
                }
            } else if (comments.isEmpty()) {
                item {
                    Text(
                        "Belum ada komentar di Episode $epNo. Jadilah yang pertama!\nKomentar tersimpan di perangkat ini.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(comments, key = { it.id }) { c ->
                    CommentRow(c, onDelete = { store.remove(t.id, epNo, c.id); comments.remove(c) })
                }
            }
        }
        if (showComments) {
            CommentComposer(draft = draft, onDraftChange = { draft = it }, onSend = ::send)
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode, current: Boolean,
    modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, null,
                    tint = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Episode ${ep.number}", fontWeight = FontWeight.SemiBold)
                ep.title?.let { Text(it, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (current) Text("Diputar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Logo di kiri, ikon cari di kanan atas. */
@Composable
private fun AppHeader(onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Logo()
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, "Cari", Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.size(10.dp))
        Text("RENKAI", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
    }
}

@Composable
private fun ListRow(t: Title, onSelect: (Title) -> Unit) {
    Surface(onClick = { onSelect(t) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
                if (t.poster != null) {
                    AsyncImage(t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(t.year?.toString(), "${t.episodes.size} episode", t.score?.let { s -> "★ $s" }).joinToString(" · ")
                Text(meta, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    onControlsVisibility: ((Boolean) -> Unit)? = null,
) {
    val visibilityCallback by rememberUpdatedState(onControlsVisibility)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).also { pv ->
                pv.player = player
                pv.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { v -> visibilityCallback?.invoke(v == View.VISIBLE) },
                )
            }
        },
        update = { it.player = player },
        onRelease = { it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?); it.player = null },
    )
}
