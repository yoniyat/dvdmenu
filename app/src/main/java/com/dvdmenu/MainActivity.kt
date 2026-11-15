package com.dvdmenu

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint as APaint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size as ASize
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MaterialTheme { App() } }
    }
}

enum class S { MENU, MEDIA, DISPLAY, SKETCH, GAMES }

private val ITEMS = listOf(
    Triple(S.MEDIA, "Photos & Videos", "Browse your photos and videos."),
    Triple(S.SKETCH, "Sketch", "Draw something and save it."),
    Triple(S.GAMES, "Games", "Tiny games to pass the time."),
    Triple(S.DISPLAY, "Display", "Change the screen brightness.")
)

private val ICONS = listOf(R.drawable.ic_media, R.drawable.ic_sketch, R.drawable.ic_games, R.drawable.ic_display)

@Composable
fun App() {
    var s by remember { mutableStateOf(S.MENU) }
    BackHandler(s != S.MENU) { s = S.MENU }
    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF053078), Color(0xFF0A3F8C), Color(0xFF112D81), Color(0xFF010133))
                )
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x6618A0F0), Color.Transparent),
                        center = Offset(size.width * .2f, size.height * .45f), radius = size.width * .55f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x99081156), Color.Transparent),
                        center = Offset(size.width, 0f), radius = size.width * .5f
                    )
                )
            }
    ) {
        when (s) {
            S.MENU -> Menu { s = it }
            S.MEDIA -> Media()
            S.DISPLAY -> Display()
            S.SKETCH -> Sketch()
            S.GAMES -> Games()
        }
        if (s != S.MENU) {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            Box(
                Modifier.align(Alignment.BottomStart).padding(16.dp).size(44.dp)
                    .clip(CircleShape).background(Color(0x99000000))
                    .clickable { dispatcher?.onBackPressed() },
                contentAlignment = Alignment.Center
            ) { Text("◀", color = Color.White, fontSize = 18.sp) }
        }
    }
}

@Composable
fun Bar(text: String, on: Boolean, icon: Int? = null, click: () -> Unit) {
    val colors = if (on) listOf(Color(0xFFFFD27A), Color(0xFFF6A31A), Color(0xFFFFBF4A))
    else listOf(Color.White, Color(0xFFCFE0F2), Color(0xFFE8F1FA))
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).height(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(colors))
            .clickable { click() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 20.sp, color = Color(0xFF1B2230))
            if (icon != null) Image(painterResource(icon), null, Modifier.size(30.dp))
        }
    }
}

// A list of bars. The orange highlight follows the selection (arrow keys or a tap).
// A tap on another bar selects it; a tap on the selected bar (or Enter) opens it.
@Composable
fun Picker(items: List<Pair<String, Int?>>, footer: (Int) -> String = { "" }, onPick: (Int) -> Unit) {
    var sel by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    val ls = rememberLazyListState()
    LaunchedEffect(Unit) { fr.requestFocus() }
    LaunchedEffect(sel) { ls.animateScrollToItem(sel) }
    Box(
        Modifier.fillMaxSize().focusRequester(fr).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                Key.DirectionDown -> { sel = (sel + 1) % items.size; true }
                Key.DirectionUp -> { sel = (sel + items.size - 1) % items.size; true }
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onPick(sel); true }
                else -> false
            }
        }
    ) {
        LazyColumn(
            Modifier.align(Alignment.Center).fillMaxWidth(.44f).fillMaxHeight(.7f),
            state = ls, verticalArrangement = Arrangement.Center
        ) {
            itemsIndexed(items) { k, m ->
                Bar(m.first, k == sel, m.second) { if (k == sel) onPick(k) else sel = k }
            }
        }
        Text(
            footer(sel), Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            color = Color.White, fontSize = 18.sp
        )
    }
}

@Composable
fun Menu(go: (S) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Text("MENU", Modifier.padding(32.dp), color = Color(0xFFE8F2FF), fontSize = 28.sp)
        Picker(ITEMS.mapIndexed { k, t -> Pair<String, Int?>(t.second, ICONS[k]) }, { ITEMS[it].third }) { go(ITEMS[it].first) }
    }
}

// ---------- Photos & Videos ----------
data class Media(val uri: android.net.Uri, val name: String, val video: Boolean)

fun queryMedia(c: Context, video: Boolean): List<Media> {
    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val out = mutableListOf<Media>()
    c.contentResolver.query(
        base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
        null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    )?.use { cur ->
        while (cur.moveToNext()) out.add(Media(ContentUris.withAppendedId(base, cur.getLong(0)), cur.getString(1) ?: "", video))
    }
    return out
}

@Composable
fun Media() {
    val ctx = LocalContext.current
    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    var ok by remember { mutableStateOf(perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> ok = r.values.all { it } }
    LaunchedEffect(Unit) { if (!ok) ask.launch(perms) }
    var mode by remember { mutableStateOf<Boolean?>(null) }
    BackHandler(mode != null) { mode = null }
    when {
        !ok -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Allow access to photos and videos to continue.", color = Color.White, fontSize = 20.sp) }
        mode == null -> Picker(
            listOf<Pair<String, Int?>>("Photos" to R.drawable.ic_media, "Videos" to R.drawable.ic_media)
        ) { mode = it == 1 }
        else -> {
            val items = remember(mode) { queryMedia(ctx, mode == true) }
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing here yet.", color = Color.White, fontSize = 20.sp) }
            else LazyVerticalGrid(
                GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(items) { Thumb(it) } }
        }
    }
}

@Composable
fun Thumb(m: Media) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, m.uri) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.loadThumbnail(m.uri, ASize(320, 240), null) }.getOrNull() }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(4.dp)).background(Color(0x55000000))
            .clickable {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, if (m.video) "video/*" else "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
    ) {
        bmp?.let { Image(it.asImageBitmap(), m.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (m.video) Text("▶", Modifier.align(Alignment.Center), color = Color.White, fontSize = 30.sp)
    }
}

// ---------- Display ----------
@Composable
fun Display() {
    val act = LocalContext.current as Activity
    var v by remember { mutableFloatStateOf(act.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }) }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Brightness", color = Color.White, fontSize = 24.sp)
        Slider(v, {
            v = it.coerceAtLeast(0.02f)
            act.window.attributes = act.window.attributes.apply { screenBrightness = v }
        }, Modifier.fillMaxWidth(.6f))
        Text("${(v * 100).toInt()}%", color = Color.White, fontSize = 20.sp)
    }
}

// ---------- Sketch ----------
class Stroke(val color: Int, val pts: MutableList<Offset>)

fun saveSketch(c: Context, ss: List<Stroke>, sz: IntSize) {
    if (ss.isEmpty() || sz.width == 0 || sz.height == 0) return
    val bmp = Bitmap.createBitmap(sz.width, sz.height, Bitmap.Config.ARGB_8888)
    val cv = ACanvas(bmp)
    cv.drawColor(android.graphics.Color.WHITE)
    val p = APaint().apply { isAntiAlias = true; strokeWidth = 6f; strokeCap = APaint.Cap.ROUND }
    ss.forEach { s ->
        p.color = s.color
        if (s.pts.size == 1) cv.drawPoint(s.pts[0].x, s.pts[0].y, p)
        s.pts.zipWithNext { a, b -> cv.drawLine(a.x, a.y, b.x, b.y, p) }
    }
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "sketch_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sketches")
    }
    val u = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
    if (u != null) {
        c.contentResolver.openOutputStream(u)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(c, "Saved to Pictures/Sketches", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun Sketch() {
    val ctx = LocalContext.current
    val strokes = remember { mutableStateListOf<Stroke>() }
    var color by remember { mutableStateOf(Color.Black) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Color.Black, Color(0xFFD32F2F), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFF6A31A)).forEach { c ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c)
                        .border(if (c == color) 3.dp else 1.dp, Color.White, CircleShape).clickable { color = c }
                )
            }
            Spacer(Modifier.weight(1f))
            Button({ if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); tick++ } }) { Text("Undo") }
            Button({ strokes.clear(); tick++ }) { Text("Clear") }
            Button({ saveSketch(ctx, strokes, size) }) { Text("Save") }
        }
        Canvas(
            Modifier.fillMaxSize().padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(color) {
                    detectDragGestures(
                        onDragStart = { o -> strokes.add(Stroke(color.toArgb(), mutableListOf(o))); tick++ },
                        onDrag = { ch, _ -> strokes.lastOrNull()?.pts?.add(ch.position); tick++ }
                    )
                }
        ) {
            tick.let { }
            strokes.forEach { s ->
                if (s.pts.size == 1) drawCircle(Color(s.color), 3f, s.pts[0])
                s.pts.zipWithNext { a, b -> drawLine(Color(s.color), a, b, 6f, StrokeCap.Round) }
            }
        }
    }
}

// ---------- Games: Snake ----------
@Composable
fun Snake() {
    val w = 24
    val h = 14
    var snake by remember { mutableStateOf(listOf(5 to 7, 4 to 7, 3 to 7)) }
    var dir by remember { mutableStateOf(1 to 0) }
    var food by remember { mutableStateOf(12 to 7) }
    var dead by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }

    fun turn(d: Pair<Int, Int>) { if (!(d.first == -dir.first && d.second == -dir.second)) dir = d }
    fun reset() { snake = listOf(5 to 7, 4 to 7, 3 to 7); dir = 1 to 0; food = 12 to 7; score = 0; dead = false }

    LaunchedEffect(dead) {
        while (!dead) {
            delay(140)
            val hd = snake[0]
            val n = ((hd.first + dir.first + w) % w) to ((hd.second + dir.second + h) % h)
            if (n in snake) { dead = true } else {
                val ate = n == food
                val ns = listOf(n) + if (ate) snake else snake.dropLast(1)
                snake = ns
                if (ate) {
                    score++
                    food = (0 until w * h).map { it % w to it / w }.filter { it !in ns }.random()
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().focusRequester(fr).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionUp -> { turn(0 to -1); true }
                    Key.DirectionDown -> { turn(0 to 1); true }
                    Key.DirectionLeft -> { turn(-1 to 0); true }
                    Key.DirectionRight -> { turn(1 to 0); true }
                    Key.Enter, Key.NumPadEnter -> { if (dead) reset(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { _, d ->
                    if (abs(d.x) > abs(d.y)) turn((if (d.x > 0) 1 else -1) to 0) else turn(0 to (if (d.y > 0) 1 else -1))
                }
            }
            .clickable { if (dead) reset() }
    ) {
        Text("Snake  $score", Modifier.padding(24.dp), color = Color.White, fontSize = 24.sp)
        Canvas(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 64.dp)) {
            val c = min(size.width / w, size.height / h)
            drawRect(Color(0x33000000), size = Size(c * w, c * h))
            snake.forEach { drawRoundRect(Color(0xFF6FE3FF), Offset(it.first * c, it.second * c), Size(c - 2, c - 2), CornerRadius(4f)) }
            drawCircle(Color(0xFFFFBF4A), c / 2 - 2, Offset(food.first * c + c / 2, food.second * c + c / 2))
        }
        if (dead) Text(
            "Game over. Tap to play again.", Modifier.align(Alignment.Center),
            color = Color.White, fontSize = 28.sp
        )
    }
}

// ---------- Games: Snake + HTML games (single .html or a .zip with an index.html) ----------
class Entry(val label: String, val url: String?) // url == null means Snake

private fun nice(f: String) = f.removeSuffix(".html").replace('_', ' ').replace('-', ' ')

private fun unzip(input: java.io.InputStream, dest: java.io.File) {
    java.util.zip.ZipInputStream(input).use { z ->
        var e = z.nextEntry
        while (e != null) {
            val out = java.io.File(dest, e.name)
            if (out.canonicalPath.startsWith(dest.canonicalPath + java.io.File.separator)) {
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { z.copyTo(it) }
                }
            }
            e = z.nextEntry
        }
    }
}

private fun findIndex(dir: java.io.File): java.io.File? {
    val htmls = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".html", true) }.toList()
    return htmls.filter { it.name.equals("index.html", true) }.minByOrNull { it.path.length }
        ?: htmls.minByOrNull { it.path.length }
}

@Composable
fun Games() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val assetFiles = remember { (ctx.assets.list("games") ?: emptyArray()).filter { it.endsWith(".html") }.sorted() }
    val userDir = remember { java.io.File(ctx.filesDir, "games").apply { mkdirs() } }
    val userFiles = remember(rev) { userDir.listFiles()?.filter { it.isFile && it.name.endsWith(".html") }?.sortedBy { it.name } ?: emptyList() }
    val userDirs = remember(rev) { userDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList() }
    var play by remember { mutableStateOf<String?>(null) }
    BackHandler(play != null) { play = null }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val raw = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "game.html"
            val low = raw.lowercase()
            val base = raw.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
            try {
                if (low.endsWith(".html")) {
                    ctx.contentResolver.openInputStream(uri)?.use { i ->
                        java.io.File(userDir, "$base.html").outputStream().use { o -> i.copyTo(o) }
                    }
                    rev++
                    Toast.makeText(ctx, "Added $base", Toast.LENGTH_SHORT).show()
                } else if (low.endsWith(".zip")) {
                    val dir = java.io.File(userDir, base)
                    dir.deleteRecursively(); dir.mkdirs()
                    ctx.contentResolver.openInputStream(uri)?.use { unzip(it, dir) }
                    if (findIndex(dir) == null) {
                        dir.deleteRecursively()
                        Toast.makeText(ctx, "No .html file found in that zip", Toast.LENGTH_LONG).show()
                    } else {
                        rev++
                        Toast.makeText(ctx, "Added $base", Toast.LENGTH_SHORT).show()
                    }
                } else Toast.makeText(ctx, "Please pick an .html or .zip file", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not add that file", Toast.LENGTH_LONG).show()
            }
        }
    }

    val entries = listOf(Entry("Snake", null)) +
        assetFiles.map { Entry(nice(it), "file:///android_asset/games/" + android.net.Uri.encode(it)) } +
        userFiles.map { Entry(nice(it.name), android.net.Uri.fromFile(it).toString()) } +
        userDirs.mapNotNull { d -> findIndex(d)?.let { Entry(nice(d.name), android.net.Uri.fromFile(it).toString()) } }

    val cur = play
    if (cur == null) {
        Box(Modifier.fillMaxSize()) {
            Picker(entries.map { Pair<String, Int?>(it.label, R.drawable.ic_games) }) { k ->
                play = entries[k].url ?: "snake"
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFD27A), Color(0xFFF6A31A))))
                    .clickable { pick.launch(arrayOf("*/*")) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text("+ Add game", color = Color(0xFF201400), fontSize = 16.sp) }
        }
    } else if (cur == "snake") Snake() else GameView(cur)
}

@Composable
fun GameView(url: String) {
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mediaPlaybackRequiresUserGesture = false
                loadUrl(url)
                requestFocus()
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.loadUrl("about:blank"); it.onPause(); it.destroy() }
    )
}
