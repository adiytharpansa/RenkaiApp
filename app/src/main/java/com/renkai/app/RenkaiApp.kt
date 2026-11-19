package com.renkai.app

import android.app.Activity
import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

data class Stream(val title: String, val type: String, val url: String, val tone: Color)

private val streams = listOf(
    Stream("Apple Bip-Bop", "HLS", "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8", Color(0xFF3D3200)),
    Stream("Big Buck Bunny", "HLS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", Color(0xFF26323A)),
    Stream("Big Buck Bunny", "DASH", "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd", Color(0xFF4A3F00)),
    Stream("Tears of Steel", "DASH", "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd", Color(0xFF2E2A3A)),
)

@Composable
fun RenkaiApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Stream?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favs = remember { mutableStateListOf<Stream>() }

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

    fun open(s: Stream) {
        error = null
        val mime = when {
            s.url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            s.url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        player.setMediaItem(MediaItem.Builder().setUri(s.url).apply { mime?.let { setMimeType(it) } }.build())
        player.prepare(); player.play()
        playing = s
    }
    fun toggleFav(s: Stream) {
        val i = favs.indexOfFirst { it.url == s.url }
        if (i >= 0) favs.removeAt(i) else favs.add(s)
    }
    fun close() { player.pause(); playing = null }

    BackHandler(enabled = playing != null) { close() }

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
    when {
        fullscreen -> Box(Modifier.fillMaxSize().background(Color.Black)) { PlayerSurface(player, Modifier.fillMaxSize()) }
        cur != null -> PlayerScreen(player, cur, error, favs.any { it.url == cur.url }, { close() }, { toggleFav(cur) }, ::open)
        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("Beranda" to Icons.Rounded.Home, "Cari" to Icons.Rounded.Search, "Koleksi" to Icons.Rounded.Favorite)
                        .forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(icon, label) }, label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                ),
                            )
                        }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).statusBarsPadding()) {
                when (tab) {
                    0 -> HomeTab(favs, ::open, ::toggleFav)
                    1 -> SearchTab(::open)
                    else -> CollectionTab(favs, ::open)
                }
            }
        }
    }
}

@Composable
private fun HomeTab(favs: List<Stream>, onOpen: (Stream) -> Unit, onFav: (Stream) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Logo(Modifier.padding(start = 20.dp, top = 16.dp)) }
        item { Hero(streams[0], favs.any { it.url == streams[0].url }, onOpen, onFav) }
        item { Section("Rekomendasi") { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(streams) { WideCard(it, onOpen) } } } }
        item { Section("Stream HLS") { PosterRow(streams.filter { it.type == "HLS" }, onOpen) } }
        item { Section("Stream DASH") { PosterRow(streams.filter { it.type == "DASH" }, onOpen) } }
    }
}

@Composable
private fun SearchTab(onOpen: (Stream) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val isLink = q.trim().startsWith("http", true)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Logo()
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), label = { Text("Cari judul atau tempel link .m3u8 / .mpd") },
        )
        if (isLink) {
            val url = q.trim()
            Button(
                onClick = { onOpen(Stream("Link kustom", if (url.contains(".mpd", true)) "DASH" else "HLS", url, Color(0xFF3D3200))) },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text("Putar link", fontWeight = FontWeight.Bold) }
        } else {
            streams.filter { q.isBlank() || it.title.contains(q, true) || it.type.contains(q, true) }
                .forEach { ListRow(it, onOpen) }
        }
    }
}

@Composable
private fun CollectionTab(favs: List<Stream>, onOpen: (Stream) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Logo()
        Text("Koleksi", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (favs.isEmpty()) Text("Belum ada koleksi. Tekan “Koleksi” pada stream untuk menyimpannya.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        favs.forEach { ListRow(it, onOpen) }
    }
}

@Composable
private fun PlayerScreen(
    player: Player, s: Stream, error: String?, fav: Boolean,
    onBack: () -> Unit, onFav: () -> Unit, onOpen: (Stream) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerSurface(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("${s.type} · Streaming adaptif", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onFav, shape = RoundedCornerShape(14.dp)) {
                Icon(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, null)
                Spacer(Modifier.size(6.dp)); Text(if (fav) "Di koleksi" else "Koleksi", fontWeight = FontWeight.Bold)
            }
            Text("Stream lainnya", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            PosterRow(streams.filter { it.url != s.url }, onOpen, 0.dp)
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
private fun Hero(s: Stream, fav: Boolean, onOpen: (Stream) -> Unit, onFav: (Stream) -> Unit) {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(240.dp).clip(RoundedCornerShape(28.dp)).background(s.tone)) {
        Icon(Icons.Rounded.PlayArrow, null, Modifier.align(Alignment.TopEnd).size(170.dp).alpha(0.14f), tint = Color.White)
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeBadge("UNGGULAN")
            Text(s.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("${s.type} · Contoh stream", color = Color(0xFFE8E4D0), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onOpen(s) }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.size(4.dp)); Text("Putar", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onFav(s) }, shape = RoundedCornerShape(14.dp),
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
private fun WideCard(s: Stream, onOpen: (Stream) -> Unit) {
    Box(Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(20.dp)).background(s.tone).clickable { onOpen(s) }) {
        Icon(Icons.Rounded.PlayArrow, null, Modifier.align(Alignment.Center).size(44.dp), tint = Color.White.copy(alpha = 0.9f))
        Box(Modifier.padding(10.dp)) { TypeBadge(s.type) }
        Text(s.title, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PosterRow(list: List<Stream>, onOpen: (Stream) -> Unit, hPad: androidx.compose.ui.unit.Dp = 20.dp) {
    LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { s ->
            Column(Modifier.width(120.dp).clickable { onOpen(s) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(s.tone)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.align(Alignment.Center).size(36.dp), tint = Color.White.copy(alpha = 0.9f))
                    Box(Modifier.padding(8.dp)) { TypeBadge(s.type) }
                }
                Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ListRow(s: Stream, onOpen: (Stream) -> Unit) {
    Surface(onClick = { onOpen(s) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(s.tone), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.SemiBold)
                Text(s.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TypeBadge(s.type)
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
