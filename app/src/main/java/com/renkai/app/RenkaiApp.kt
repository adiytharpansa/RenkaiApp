package com.renkai.app

import android.app.Activity
import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.renkai.app.data.CatalogApi
import com.renkai.app.data.Episode
import com.renkai.app.data.Title
import kotlinx.coroutines.launch

@Composable
fun RenkaiApp(
    autoTitle: String? = null,
    autoEp: Int = 1,
    vm: RenkaiViewModel = viewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by vm.state.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val cat = state.catalog
    val cur = state.playing
    val p = player
    val favoriteIds = state.favoriteIds
    val favs = remember(cat, favoriteIds) {
        val byId = cat.orEmpty().associateBy { it.id }
        favoriteIds.mapNotNull { byId[it] }
    }
    val sel = cat?.firstOrNull { it.id == selectedId }

    // Ekstra intent untuk uji otomatis: langsung putar judul/episode setelah katalog siap.
    LaunchedEffect(cat) { vm.autoPlayOnce(autoTitle, autoEp) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> vm.onAppBackgrounded()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    BackHandler(enabled = cur != null) { vm.closePlayer() }
    BackHandler(enabled = cur == null && sel != null) { selectedId = null }
    BackHandler(enabled = cur == null && sel == null && searchOpen) { searchOpen = false }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = landscape && cur != null
    LaunchedEffect(fullscreen) {
        val w = (context as? Activity)?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        if (fullscreen) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    when {
        cur != null && p == null && state.playbackError != null ->
            ErrorScreen(state.playbackError ?: "Layanan pemutar tidak tersedia") {
                vm.play(cur.title, cur.episode)
            }
        cur != null && p == null -> LoadingScreen()
        cur != null && p != null && fullscreen ->
            Box(Modifier.fillMaxSize().background(Color.Black)) { PlayerSurface(p, Modifier.fillMaxSize()) }
        cur != null && p != null -> PlayerScreen(
            p, cur, state.playbackError,
            favoriteIds.contains(cur.title.id),
            onBack = { vm.closePlayer() },
            onFav = { vm.toggleFav(cur.title) },
            onPlayEp = { ep -> vm.play(cur.title, ep) },
        )
        sel != null -> DetailScreen(
            sel,
            favoriteIds.contains(sel.id),
            onBack = { selectedId = null },
            onFav = { vm.toggleFav(sel) },
            onPlayEp = { ep -> vm.play(sel, ep) },
        )
        cat == null && state.loadError == null -> LoadingScreen()
        cat == null -> ErrorScreen(state.loadError ?: "Gagal memuat katalog") { vm.loadCatalog(force = true) }
        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .shadow(10.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                RoundedCornerShape(28.dp),
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            Triple(0, "Beranda", Icons.Outlined.Home),
                            Triple(1, "Jadwal", Icons.Outlined.DateRange),
                            Triple(2, "Koleksi", Icons.Outlined.BookmarkBorder),
                            Triple(3, "Unduhan", Icons.Outlined.Download),
                            Triple(4, "Akun", Icons.Outlined.PersonOutline),
                        ).forEach { (i, label, icon) ->
                            val selected = !searchOpen && tab == i
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .selectable(
                                        selected = selected,
                                        onClick = { tab = i; searchOpen = false },
                                        role = Role.Tab,
                                    )
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(32.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(7.dp))
                                Box(
                                    Modifier
                                        .width(38.dp)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                        ),
                                )
                            }
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).statusBarsPadding()) {
                if (searchOpen) {
                    SearchTab(
                        cat,
                        onBack = { searchOpen = false },
                        onSelect = { selectedId = it.id },
                        onPlayLink = vm::playLink,
                    )
                } else {
                    when (tab) {
                        0 -> HomeTab(
                            cat,
                            favs,
                            onSelect = { selectedId = it.id },
                            onPlay = { t, ep -> vm.play(t, ep) },
                            onFav = vm::toggleFav,
                            onSearch = { searchOpen = true },
                        )
                        1 -> UnavailableTab("Jadwal", Icons.Rounded.DateRange)
                        2 -> CollectionTab(favs, onSelect = { selectedId = it.id })
                        3 -> UnavailableTab("Unduhan", Icons.Rounded.Download)
                        4 -> UnavailableTab("Akun", Icons.Rounded.Person)
                        else -> HomeTab(
                            cat,
                            favs,
                            onSelect = { selectedId = it.id },
                            onPlay = { t, ep -> vm.play(t, ep) },
                            onFav = vm::toggleFav,
                            onSearch = { searchOpen = true },
                        )
                    }
                }
            }
        }
    }
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
    val hero = remember(catalog) { catalog.filter { it.banner != null }.maxByOrNull { it.score ?: 0.0 } }
    val trending = remember(catalog) { catalog.sortedByDescending { it.score ?: 0.0 }.take(20) }
    val newest = remember(catalog) { catalog.asReversed().take(30) }
    val movies = remember(catalog) { catalog.filter { it.episodes.size == 1 }.take(24) }
    val topGenres = remember(catalog) {
        catalog.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(4).map { it.key }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Cari") }
            }
        }
        if (hero != null) {
            item { Hero(hero, favs.any { it.id == hero.id }, onPlay = { ep -> onPlay(hero, ep) }, onFav = { onFav(hero) }, onSelect = { onSelect(hero) }) }
        }
        item { Section("Sedang Tren") { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(trending) { WideCard(it, onSelect) } } } }
        item { Section("Baru Ditambahkan") { PosterRow(newest, onSelect) } }
        for (g in topGenres) {
            val row = catalog.filter { g in it.genres }.take(24)
            if (row.size >= 4) {
                item { Section(g) { PosterRow(row, onSelect) } }
            }
        }
        if (movies.isNotEmpty()) {
            item { Section("Film & Spesial") { PosterRow(movies, onSelect) } }
        }
        item {
            Text(
                "${catalog.size} judul · ${catalog.sumOf { it.episodes.size }} episode",
                Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SearchTab(
    catalog: List<Title>,
    onBack: () -> Unit,
    onSelect: (Title) -> Unit,
    onPlayLink: (String) -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    val isLink = q.trim().startsWith("http", true)
    val results = remember(q, catalog) {
        if (q.isBlank()) emptyList()
        else catalog.filter { it.title.contains(q, true) || it.genres.any { g -> g.contains(q, true) } }.take(60)
    }
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali ke beranda") }
                Text("Cari", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
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
private fun CollectionTab(favs: List<Title>, onSelect: (Title) -> Unit) {
    LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Logo() }
        item { Text("Koleksi", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (favs.isEmpty()) {
            item { Text("Belum ada koleksi. Buka judul lalu tekan “Koleksi” untuk menyimpannya di sini.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(favs) { t -> ListRow(t, onSelect) }
        }
    }
}

@Composable
private fun UnavailableTab(title: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Fitur $title belum tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailScreen(
    t: Title, fav: Boolean,
    onBack: () -> Unit, onFav: () -> Unit, onPlayEp: (Episode) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        item {
            Box(Modifier.fillMaxWidth().height(210.dp).background(Color.Black)) {
                if (t.banner != null) {
                    AsyncImage(t.banner, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
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
    val idx = t.episodes.indexOfFirst { it.number == target.episode.number }
    val prev = t.episodes.getOrNull(idx - 1)
    val next = t.episodes.getOrNull(idx + 1)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
            Text(t.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerSurface(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "Episode ${target.episode.number}" + (target.episode.title?.let { " — $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { prev?.let(onPlayEp) }, enabled = prev != null, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, null); Spacer(Modifier.size(4.dp)); Text("Sebelumnya")
                    }
                    OutlinedButton(onClick = { next?.let(onPlayEp) }, enabled = next != null, shape = RoundedCornerShape(14.dp)) {
                        Text("Berikutnya"); Spacer(Modifier.size(4.dp)); Icon(Icons.Rounded.SkipNext, null)
                    }
                    Button(onClick = onFav, shape = RoundedCornerShape(14.dp)) {
                        Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null)
                        Spacer(Modifier.size(4.dp)); Text(if (fav) "Di koleksi" else "Koleksi", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Text("Episode lainnya", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(t.episodes) { ep -> EpisodeRow(ep, current = ep.number == target.episode.number) { onPlayEp(ep) } }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, current: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
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

@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.size(10.dp))
        Text("RENKAI", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Hero(t: Title, fav: Boolean, onPlay: (Episode) -> Unit, onFav: () -> Unit, onSelect: () -> Unit) {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1A1A1C))) {
        if (t.banner != null) {
            AsyncImage(t.banner, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeBadge("UNGGULAN")
            Text(t.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                t.matchPercent?.let { "$it% cocok" }, t.year?.toString(), t.statusLabel, "${t.episodes.size} episode",
            ).joinToString(" · ")
            Text(meta, color = Color(0xFFE8E4D0), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { t.episodes.firstOrNull()?.let(onPlay) }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(4.dp)); Text("Putar", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSelect, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Icon(Icons.Rounded.Info, null); Spacer(Modifier.size(4.dp)); Text("Detail") }
                OutlinedButton(
                    onClick = onFav, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null); Spacer(Modifier.size(4.dp)); Text("Koleksi") }
            }
        }
    }
}

@Composable
private fun TypeBadge(text: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WideCard(t: Title, onSelect: (Title) -> Unit) {
    Box(Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF222225)).clickable { onSelect(t) }) {
        if (t.banner != null) {
            AsyncImage(t.banner, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))))
        Icon(Icons.Rounded.PlayArrow, null, Modifier.align(Alignment.Center).size(40.dp), tint = Color.White.copy(alpha = 0.9f))
        Text(t.title, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterRow(list: List<Title>, onSelect: (Title) -> Unit, hPad: androidx.compose.ui.unit.Dp = 20.dp) {
    LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { t ->
            Column(Modifier.width(120.dp).clickable { onSelect(t) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF222225))) {
                    if (t.poster != null) {
                        AsyncImage(t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    t.matchPercent?.let {
                        Box(Modifier.padding(8.dp)) { TypeBadge("$it%") }
                    }
                }
                Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
private fun PlayerSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).also { it.player = player } },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
