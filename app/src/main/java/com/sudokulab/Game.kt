package com.sudokulab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

internal const val ERROR_LIMIT = 3

internal enum class Level(val label: String, val puzzle: String) {
    EASY("Fácil", "530070000600195000098000060800060003400803001700020006060000280000419005000080079"),
    MEDIUM("Media", "030000000670000040198342000859701000420050001000900000000037200200000635300000100"),
    HARD("Difícil", "100007090030020008009600500005300900010080002600004000300000010040000007007000300")
}

internal data class Board(val values: List<Int>, val notes: List<Int>) {
    fun change(cell: Int, value: Int, mask: Int) = Board(
        values.toMutableList().apply { this[cell] = value },
        notes.toMutableList().apply { this[cell] = mask }
    )
}

internal data class Game(
    val level: Level,
    val givens: List<Int>,
    val solution: List<Int>,
    val board: Board,
    val selected: Int = -1,
    val notesMode: Boolean = false,
    val hints: Int = 3,
    val seconds: Int = 0,
    val complete: Boolean = false,
    val errors: Int = 0
) {
    val lost: Boolean get() = errors >= ERROR_LIMIT
    val finished: Boolean get() = complete || lost

    fun isDigitComplete(digit: Int): Boolean =
        solution.indices.all { solution[it] != digit || board.values[it] == digit }

    fun enter(value: Int): Game {
        if (finished || selected !in givens.indices || givens[selected] != 0) return this
        if (notesMode && value != 0) {
            if (board.values[selected] != 0) return this
            return edit(selected, 0, board.notes[selected] xor (1 shl value))
        }
        val edited = edit(selected, value)
        return if (value != 0 && value != solution[selected] && value != board.values[selected]) {
            edited.copy(errors = errors + 1)
        } else edited
    }

    fun hint(): Game {
        if (finished || hints == 0) return this
        val target = selected.takeIf { it in givens.indices && board.values[it] != solution[it] }
            ?: givens.indices.firstOrNull { board.values[it] != solution[it] }
            ?: return this
        return copy(selected = target, hints = hints - 1).edit(target, solution[target])
    }

    fun autoNotes(): Game {
        if (finished) return this
        val values = board.values
        val notes = values.mapIndexed { cell, value ->
            if (value != 0) return@mapIndexed 0
            var candidates = 0b1111111110
            values.forEachIndexed { neighbor, digit ->
                if (digit != 0 && peers(cell, neighbor)) {
                    candidates = candidates and (1 shl digit).inv()
                }
            }
            candidates
        }
        return copy(board = board.copy(notes = notes))
    }

    private fun edit(cell: Int, value: Int, mask: Int = 0): Game {
        val next = board.change(cell, value, mask)
        if (next == board) return this
        val updated = if (value != 0 && value == solution[cell]) {
            val remaining = (1 shl value).inv()
            next.copy(notes = next.notes.mapIndexed { index, candidates ->
                if (peers(cell, index)) candidates and remaining else candidates
            })
        } else next
        return copy(board = updated, complete = updated.values == solution)
    }

    companion object {
        fun create(level: Level = Level.EASY, random: Random = Random.Default): Game {
            fun shuffledGroups() = (0..2).shuffled(random).flatMap { group ->
                (0..2).shuffled(random).map { group * 3 + it }
            }
            val rows = shuffledGroups()
            val columns = shuffledGroups()
            val digits = listOf(0) + (1..9).shuffled(random)
            val givens = rows.flatMap { row ->
                columns.map { column -> digits[level.puzzle[row * 9 + column].digitToInt()] }
            }
            val solution = solveUnique(givens)
            return Game(level, givens, solution, Board(givens, List(81) { 0 }))
        }
    }
}

internal fun peers(a: Int, b: Int): Boolean =
    a / 9 == b / 9 || a % 9 == b % 9 ||
        (a / 27 == b / 27 && a % 9 / 3 == b % 9 / 3)

private fun solveUnique(givens: List<Int>): List<Int> {
    val values = givens.toMutableList()
    val neighbors = givens.indices.map { cell -> givens.indices.filter { it != cell && peers(it, cell) } }
    val solutions = mutableListOf<List<Int>>()
    fun search() {
        if (solutions.size == 2) return
        val empty = givens.indices.filter { values[it] == 0 }
        if (empty.isEmpty()) {
            solutions += values.toList()
            return
        }
        val (cell, candidates) = empty.map { cell ->
            cell to (1..9).filter { digit -> neighbors[cell].none { values[it] == digit } }
        }.minBy { it.second.size }
        for (digit in candidates) {
            values[cell] = digit
            search()
            values[cell] = 0
            if (solutions.size == 2) return
        }
    }
    search()
    return solutions.single()
}

internal data class Progress(val lastDay: String = "", val days: Int = 0) {
    fun activeDays(now: Calendar = Calendar.getInstance()): Int =
        if (lastDay == dayKey(now) || lastDay == yesterday(now)) days else 0

    fun recordWin(now: Calendar = Calendar.getInstance()): Progress {
        val today = dayKey(now)
        if (lastDay == today) return this
        return Progress(today, activeDays(now) + 1)
    }

    private fun yesterday(now: Calendar) = dayKey((now.clone() as Calendar).apply { add(Calendar.DATE, -1) })

    private fun dayKey(date: Calendar) =
        "${date.get(Calendar.YEAR)}-${date.get(Calendar.DAY_OF_YEAR)}"
}

internal data class Session(
    val game: Game,
    val progress: Progress = Progress(),
    val highlightMatches: Boolean = true
)

internal class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("sudoku", Context.MODE_PRIVATE)

    fun load(): Session {
        val saved = prefs.getString("session", null) ?: return Session(Game.create())
        return runCatching {
            val json = JSONObject(saved)
            val givens = json.getJSONArray("givens").numbers()
            val game = Game(
                level = Level.valueOf(json.getString("level")),
                givens = givens,
                solution = solveUnique(givens),
                board = json.getJSONObject("board").board(),
                selected = json.getInt("selected"),
                notesMode = json.getBoolean("notesMode"),
                hints = json.getInt("hints"),
                seconds = json.getInt("seconds").coerceAtLeast(0),
                complete = json.getBoolean("complete"),
                errors = json.optInt("errors", 0).coerceIn(0, ERROR_LIMIT)
            )
            Session(game, Progress(json.getString("lastDay"), json.getInt("days")), json.optBoolean("matches", true))
        }.getOrElse { Session(Game.create()) }
    }

    fun save(session: Session) {
        val game = session.game
        val json = JSONObject().apply {
            put("level", game.level.name)
            put("givens", JSONArray(game.givens))
            put("board", game.board.json())
            put("selected", game.selected)
            put("notesMode", game.notesMode)
            put("hints", game.hints)
            put("seconds", game.seconds)
            put("complete", game.complete)
            put("errors", game.errors)
            put("lastDay", session.progress.lastDay)
            put("days", session.progress.days)
            put("matches", session.highlightMatches)
        }
        prefs.edit().putString("session", json.toString()).apply()
    }

    private fun Board.json() = JSONObject().put("values", JSONArray(values)).put("notes", JSONArray(notes))
    private fun JSONObject.board() = Board(getJSONArray("values").numbers(), getJSONArray("notes").numbers())
    private fun JSONArray.numbers() = List(length()) { getInt(it) }
}
