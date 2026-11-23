package com.sudokulab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Ink = Color(0xFF111127)
private val Muted = Color(0xFF534D78)
private val Tile = Color(0xFFF5F3FF)
private val Blue = Color(0xFF005FD3)
private val Purple = Color(0xFF4D20A8)
private val Amber = Color(0xFFB75A00)
private val ErrorRed = Color(0xFFB4232B)
private val Spanish = Locale.forLanguageTag("es-ES")
private val Bevel = RoundedCornerShape(18.dp)

class MainActivity : ComponentActivity() {
    private lateinit var store: GameStore
    private var session by mutableStateOf<Session?>(null)
    private var playing by mutableStateOf(false)
    private var gameClock by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = GameStore(this)
        session = store.load().also(store::save)
        setContent {
            val current = checkNotNull(session)
            var progressDay by remember { mutableStateOf(Calendar.getInstance()) }
            LaunchedEffect(playing, gameClock) {
                val clock = gameClock
                while (playing && clock == gameClock) {
                    delay(1000)
                    if (!playing || clock != gameClock) break
                    val now = Calendar.getInstance()
                    if (progressDay.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ||
                        progressDay.get(Calendar.YEAR) != now.get(Calendar.YEAR)) progressDay = now
                    val latest = checkNotNull(session)
                    if (!latest.game.finished) {
                        val elapsed = latest.copy(game = latest.game.copy(seconds = latest.game.seconds + 1))
                        session = elapsed
                        if (elapsed.game.seconds % 10 == 0) store.save(elapsed)
                    }
                }
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = Purple, onSurface = Ink, surface = Tile)) {
                SudokuScreen(current, current.progress.activeDays(progressDay), ::update)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playing = true
    }

    override fun onPause() {
        playing = false
        session?.let(store::save)
        super.onPause()
    }

    private fun update(next: Session) {
        val current = session ?: return
        if (next == current) return
        val sameGame = current.game.givens === next.game.givens
        val timed = if (sameGame) {
            next.copy(game = next.game.copy(seconds = current.game.seconds))
        } else next.copy(game = next.game.copy(seconds = 0))
        val saved = if (!current.game.complete && timed.game.complete) {
            timed.copy(progress = timed.progress.recordWin())
        } else timed
        session = saved
        if (!sameGame) gameClock++
        store.save(saved)
    }
}

@Composable
private fun SudokuScreen(session: Session, streak: Int, update: (Session) -> Unit) {
    val game = session.game
    var choosing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var pendingLevel by remember { mutableStateOf<Level?>(null) }
    var celebrationDismissed by rememberSaveable(game.givens) { mutableStateOf(false) }
    fun change(next: Game) = update(session.copy(game = next))
    fun start(level: Level) = change(Game.create(level))

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE5E0FA), Color(0xFFF4F1FF), Color(0xFFE9E5FB))))) {
        HeaderWaves()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 590.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_sudoku), null, Modifier.size(34.dp), tint = Color.Unspecified)
                        Text("Sudoku", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        onClick = { settings = true }, modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
                        color = Tile, shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, "Ajustes", tint = Muted) } }
                }
                Box {
                    Surface(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth(), shape = Bevel, color = Tile, shadowElevation = 3.dp) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFE6DFF8)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.GridOn, null, tint = Muted)
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.width(2.dp).height(40.dp).background(Color(0xFFFFB229)))
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                val title = when {
                                    game.lost -> "Partida perdida"
                                    game.complete -> "Partida completada"
                                    else -> "Partida"
                                }
                                Text(title, color = if (game.lost) ErrorRed else Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Dificultad: ${game.level.label}", color = Muted, fontSize = 15.sp)
                            }
                            Icon(Icons.Rounded.ExpandMore, "Nueva partida", tint = Muted)
                        }
                    }
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                        Level.entries.forEach { level ->
                            DropdownMenuItem(text = { Text("Nueva partida · ${level.label}") }, onClick = {
                                choosing = false
                                if (!game.finished && (game.errors > 0 || game.board != Board(game.givens, List(81) { 0 }))) pendingLevel = level
                                else start(level)
                            })
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 405.dp
                    val minutes = game.seconds / 60
                    val timer = "%02d:%02d".format(Spanish, minutes, game.seconds % 60)
                    val errors = "${game.errors}/$ERROR_LIMIT"
                    val days = if (streak == 1) "1 día" else "$streak días"
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Stat("Dificultad", game.level.label, Icons.Rounded.BarChart, Modifier.weight(1f))
                                Stat("Tiempo", timer, Icons.Rounded.Schedule, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Stat("Errores", errors, Icons.Rounded.ErrorOutline, Modifier.weight(1f), ErrorRed)
                                Stat("Racha", days, Icons.Rounded.LocalFireDepartment, Modifier.weight(1f), Amber, true)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Stat("Dificultad", game.level.label, Icons.Rounded.BarChart, Modifier.weight(1f))
                            Stat("Tiempo", timer, Icons.Rounded.Schedule, Modifier.weight(1f))
                            Stat("Errores", errors, Icons.Rounded.ErrorOutline, Modifier.weight(1f), ErrorRed)
                            Stat("Racha", days, Icons.Rounded.LocalFireDepartment, Modifier.weight(1f), Amber, true)
                        }
                    }
                }
                SudokuBoard(game, session.highlightMatches) { change(game.copy(selected = it)) }
                val selected = game.selected
                val editable = !game.finished && selected in game.givens.indices && game.givens[selected] == 0
                val digit = game.board.values.getOrNull(selected) ?: 0
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1..5, 6..9).forEach { numbers ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            numbers.forEach { number ->
                                val active = digit == number
                                val placedInEveryBlock = game.isDigitComplete(number)
                                Surface(
                                    onClick = { change(game.enter(number)) }, enabled = editable && !placedInEveryBlock,
                                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
                                    color = if (active) Color(0xFFD0E5FF) else Tile, shadowElevation = 2.dp,
                                    border = if (active) androidx.compose.foundation.BorderStroke(2.dp, Blue) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val color = if (active) Purple else Ink
                                        Text(
                                            number.toString(), color = color.copy(alpha = if (placedInEveryBlock) .40f else 1f),
                                            fontSize = 24.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (numbers.last == 9) {
                                NotesKey(game.notesMode, !game.finished, Modifier.weight(1f)) {
                                    change(game.copy(notesMode = !game.notesMode))
                                }
                            }
                        }
                    }
                }
                val actions = listOf(
                    Action("Borrar", Icons.AutoMirrored.Rounded.Backspace, editable) { change(game.enter(0)) },
                    Action("Pista · ${game.hints}", Icons.Rounded.Lightbulb, !game.finished && game.hints > 0, tint = Amber) { change(game.hint()) },
                    Action("Auto notas", Icons.Rounded.AutoAwesome, !game.finished) { change(game.autoNotes()) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    actions.forEach { ActionButton(it, Modifier.weight(1f)) }
                }
                if (game.finished) Button(onClick = { start(game.level) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Jugar otra partida")
                }
            }
        }
    }
    if (settings) AlertDialog(
        onDismissRequest = { settings = false },
        title = { Text("Ajustes") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Resaltar números colocados iguales", Modifier.weight(1f))
                Switch(checked = session.highlightMatches, onCheckedChange = { update(session.copy(highlightMatches = it)) },
                    modifier = Modifier.semantics { contentDescription = "Resaltar números colocados iguales" })
            }
        },
        confirmButton = { TextButton(onClick = { settings = false }) { Text("Listo") } }
    )
    pendingLevel?.let { level ->
        AlertDialog(
            onDismissRequest = { pendingLevel = null },
            title = { Text("¿Empezar otra partida?") },
            text = { Text("Se sustituirá la partida actual por una de dificultad ${level.label.lowercase(Spanish)}.") },
            confirmButton = { TextButton(onClick = { pendingLevel = null; start(level) }) { Text("Empezar") } },
            dismissButton = { TextButton(onClick = { pendingLevel = null }) { Text("Seguir jugando") } }
        )
    }
    if (game.complete && !celebrationDismissed) {
        AlertDialog(
            onDismissRequest = { celebrationDismissed = true },
            title = { Text("Sudoku completo") },
            text = { Fireworks() },
            confirmButton = {
                Button(onClick = { start(game.level) }) { Text("Otra partida") }
            }
        )
    }
}

@Composable
private fun Fireworks() {
    Box(Modifier.fillMaxWidth().height(208.dp)) {
        listOf(Purple, Blue, Amber).forEachIndexed { index, color ->
            val progress = remember { Animatable(1f) }
            var position by remember { mutableStateOf(Offset.Zero) }
            var scale by remember { mutableFloatStateOf(1f) }
            LaunchedEffect(index) {
                delay(index * 140L)
                while (true) {
                    position = Offset(Random.nextFloat(), Random.nextFloat())
                    scale = 1f + Random.nextFloat() * 2f
                    val durationMillis = Random.nextInt(300, 535)
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val phase = progress.value
                if (phase >= 1f) return@Canvas
                val expansion = 1f - (1f - phase) * (1f - phase)
                val rise = 10.dp.toPx()
                val sparkPadding = 6.dp.toPx()
                val maxRadius = (minOf(size.width, size.height - rise) / 2f - sparkPadding).coerceAtLeast(0f)
                val burstRadius = (minOf(size.width * .16f, 29.12.dp.toPx()) * scale).coerceAtMost(maxRadius)
                val margin = burstRadius + sparkPadding
                val origin = Offset(
                    margin + (size.width - 2f * margin) * position.x,
                    margin + rise + (size.height - 2f * margin - rise) * position.y - rise * phase
                )
                val radius = burstRadius * expansion
                val sparkColor = color.copy(alpha = 1f - phase)
                repeat(10) { spark ->
                    val angle = (spark / 10.0 + index * .06) * 2 * PI
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    val end = origin + direction * radius * (if (spark % 2 == 0) 1f else .74f)
                    drawLine(
                        sparkColor,
                        start = origin + (end - origin) * .62f,
                        end = end,
                        strokeWidth = 2.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawCircle(sparkColor, radius = 1.5.dp.toPx(), center = end + direction * 4.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun HeaderWaves() {
    Canvas(Modifier.fillMaxWidth().height(155.dp)) {
        val w = size.width
        val h = size.height
        drawPath(Path().apply {
            moveTo(0f, h * .63f)
            cubicTo(w * .29f, 0f, w * .31f, h * 1.23f, w * .64f, h * .58f)
            quadraticTo(w * .87f, h * .05f, w, h * .12f)
            lineTo(w, 0f)
            lineTo(0f, 0f)
            close()
        }, Color(0xFFCFC7F0).copy(alpha = .60f))
        drawPath(Path().apply {
            moveTo(0f, h * .45f)
            cubicTo(w * .16f, h * .77f, w * .18f, h * .22f, w * .31f, h * .42f)
        }, Brush.horizontalGradient(listOf(Color(0xFFFFAC19), Color.Transparent), endX = w * .37f), style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun Stat(label: String, value: String, icon: ImageVector, modifier: Modifier, tint: Color = Muted, warm: Boolean = false) {
    Surface(modifier, shape = Bevel, color = if (warm) Color(0xFFFFEEDA) else Tile, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(23.dp), tint = tint)
            Spacer(Modifier.width(5.dp))
            Column {
                Text(label, color = Muted, fontSize = 11.sp, lineHeight = 13.sp)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
            }
        }
    }
}

private data class Action(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val tint: Color = Muted,
    val run: () -> Unit
)

@Composable
private fun NotesKey(active: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = modifier.height(48.dp).semantics { selected = active },
        shape = RoundedCornerShape(12.dp),
        color = if (active) Color(0xFFE8DDFB) else Tile, shadowElevation = 2.dp,
        border = if (active) androidx.compose.foundation.BorderStroke(2.dp, Purple) else null
    ) {
        val tint = if (active) Purple else Muted.copy(alpha = if (enabled) 1f else .50f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(17.dp), tint = tint)
            Text("Notas", color = tint, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionButton(action: Action, modifier: Modifier) {
    Surface(onClick = action.run, enabled = action.enabled, modifier = modifier.heightIn(min = 48.dp),
        shape = Bevel, color = Tile, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 5.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val alpha = if (action.enabled) 1f else .50f
            Icon(action.icon, null, Modifier.size(18.dp), tint = action.tint.copy(alpha = alpha))
            Spacer(Modifier.width(4.dp))
            Text(action.label, color = Ink.copy(alpha = alpha), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SudokuBoard(game: Game, matches: Boolean, select: (Int) -> Unit) {
    val edge = RoundedCornerShape(12.dp)
    val selectedDigit = game.board.values.getOrNull(game.selected) ?: 0
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(edge).background(Color(0xFFFAF9FF))) {
        val numeral = (maxWidth.value / 15f).sp
        val small = (maxWidth.value / 33f).sp
        Column(Modifier.fillMaxSize()) {
            repeat(9) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(9) { col ->
                        val cell = row * 9 + col
                        val number = game.board.values[cell]
                        val mask = game.board.notes[cell]
                        val given = game.givens[cell] != 0
                        val incorrect = !given && number != 0 && number != game.solution[cell]
                        val active = game.selected == cell
                        val equal = matches && selectedDigit != 0 && selectedDigit == number
                        val related = game.selected >= 0 && peers(game.selected, cell)
                        val bg = when {
                            active -> Color(0xFFC4DFFF)
                            incorrect -> Color(0xFFFFE5E7)
                            equal -> Color(0xFFBFD9FF)
                            related -> Color(0xFFDEEBFF)
                            else -> Color.Transparent
                        }
                        val notes = (1..9).filter { mask and (1 shl it) != 0 }
                        val spoken = when {
                            given -> "$number, fija"
                            incorrect -> "$number, incorrecto"
                            number != 0 -> number.toString()
                            notes.isNotEmpty() -> "notas ${notes.joinToString()}"
                            else -> "vacía"
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().background(bg)
                            .semantics { contentDescription = "Fila ${row + 1}, columna ${col + 1}, $spoken"; selected = active }
                            .clickable(role = Role.Button) { select(cell) }
                            .then(if (active) Modifier.border(2.dp, Blue, RoundedCornerShape(5.dp)) else Modifier),
                            contentAlignment = Alignment.Center) {
                            if (number != 0) {
                                Text(number.toString(), Modifier.clearAndSetSemantics {}, fontSize = numeral,
                                    lineHeight = numeral, fontWeight = FontWeight.SemiBold, color = when {
                                        given -> Ink
                                        incorrect -> ErrorRed
                                        else -> Purple
                                    })
                            } else if (notes.isNotEmpty()) {
                                Column(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                                    repeat(3) { noteRow ->
                                        Row(Modifier.weight(1f).fillMaxWidth()) {
                                            repeat(3) { noteCol ->
                                                val note = noteRow * 3 + noteCol + 1
                                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                    if (note in notes) {
                                                        val highlighted = selectedDigit == note
                                                        val highlight = if (highlighted) {
                                                            Modifier.fillMaxSize(.90f).background(
                                                                Color(0xFF07499E), RoundedCornerShape(3.dp)
                                                            )
                                                        } else Modifier
                                                        Box(highlight, contentAlignment = Alignment.Center) {
                                                            Text(
                                                                note.toString(), fontSize = small, lineHeight = small,
                                                                color = if (highlighted) Color.White else Muted,
                                                                fontWeight = if (highlighted) FontWeight.Black else FontWeight.Normal
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            for (line in 1..8) {
                val thick = line % 3 == 0
                val color = if (thick) Ink else Color(0xFFBCB7D1)
                val stroke = if (thick) 2.dp.toPx() else .65.dp.toPx()
                val x = size.width * line / 9
                val y = size.height * line / 9
                drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
                drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
            }
        }
        Box(Modifier.matchParentSize().border(2.dp, Ink, edge))
    }
}
