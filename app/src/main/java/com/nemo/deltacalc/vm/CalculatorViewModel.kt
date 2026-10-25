package com.nemo.deltacalc.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemo.deltacalc.core.CalcException
import com.nemo.deltacalc.core.EquationSolver
import com.nemo.deltacalc.core.ExpressionEngine
import com.nemo.deltacalc.core.Step
import com.nemo.deltacalc.data.HistoryEntity
import com.nemo.deltacalc.data.HistoryRepository
import com.nemo.deltacalc.ui.components.KeyAction
import com.nemo.deltacalc.ui.components.KeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CalcMode { CALC, QUADRATIC, SYSTEM }

data class CalcUiState(
    val mode: CalcMode = CalcMode.CALC,
    val input: String = "",
    val input2: String = "",
    val activeLine: Int = 0,
    val result: String = "",
    val approx: String? = null,
    val steps: List<Step> = emptyList(),
    val error: String? = null,
    val showSteps: Boolean = false,
    val busy: Boolean = false,
)

class CalculatorViewModel(private val historyRepository: HistoryRepository) : ViewModel() {

    private val engine = ExpressionEngine()
    private val _state = MutableStateFlow(CalcUiState())
    val state: StateFlow<CalcUiState> = _state.asStateFlow()

    fun onKey(key: KeySpec) {
        when (key.action) {
            KeyAction.Insert -> if (key.insert.isNotEmpty()) insert(key.insert)
            KeyAction.Backspace -> backspace()
            KeyAction.Clear -> clearAll()
            KeyAction.Evaluate -> evaluate()
        }
    }

    fun insert(token: String) {
        _state.update { s ->
            val target = if (s.mode == CalcMode.SYSTEM && s.activeLine == 1) s.input2 else s.input
            val updated = insertToken(target, token)
            val base = s.copy(error = null)
            if (s.mode == CalcMode.SYSTEM && s.activeLine == 1) base.copy(input2 = updated)
            else base.copy(input = updated)
        }
    }

    fun backspace() {
        _state.update { s ->
            val base = s.copy(error = null)
            if (s.mode == CalcMode.SYSTEM && s.activeLine == 1) {
                base.copy(input2 = deleteTail(s.input2))
            } else {
                base.copy(input = deleteTail(s.input))
            }
        }
    }

    fun clearAll() {
        _state.update {
            it.copy(
                input = "",
                input2 = "",
                result = "",
                approx = null,
                steps = emptyList(),
                error = null,
                showSteps = false,
            )
        }
    }

    fun switchMode(mode: CalcMode) {
        _state.value = CalcUiState(mode = mode)
    }

    fun focusLine(line: Int) {
        if (line != 0 && line != 1) return
        _state.update { it.copy(activeLine = line) }
    }

    fun toggleSteps() {
        _state.update { it.copy(showSteps = !it.showSteps) }
    }

    fun evaluate() {
        val snapshot = _state.value
        val mode = snapshot.mode
        val latex1 = snapshot.input.trim()
        val latex2 = snapshot.input2.trim()

        if (latex1.isBlank() || (mode == CalcMode.SYSTEM && latex2.isBlank())) {
            _state.update { it.copy(error = "还没有输入内容") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                when (mode) {
                    CalcMode.CALC -> {
                        val value = withContext(Dispatchers.Default) { engine.evaluate(latex1) }
                        val primary = value.primaryText()
                        val approx = value.secondaryText()
                        _state.update {
                            it.copy(
                                result = primary,
                                approx = approx,
                                steps = emptyList(),
                                showSteps = false,
                                busy = false,
                            )
                        }
                        historyRepository.add(latex1, primary, "", "calc")
                    }

                    CalcMode.QUADRATIC -> {
                        val solved = withContext(Dispatchers.Default) {
                            EquationSolver.solveQuadratic(latex1)
                        }
                        _state.update {
                            it.copy(
                                result = solved.plain,
                                approx = null,
                                steps = solved.steps,
                                showSteps = true,
                                busy = false,
                            )
                        }
                        historyRepository.add(
                            latex1,
                            solved.plain,
                            solved.steps.joinToString("\n") { "${it.title}：${it.detail}" },
                            "quadratic",
                        )
                    }

                    CalcMode.SYSTEM -> {
                        val solved = withContext(Dispatchers.Default) {
                            EquationSolver.solveLinearSystem(latex1, latex2)
                        }
                        _state.update {
                            it.copy(
                                result = solved.plain,
                                approx = null,
                                steps = solved.steps,
                                showSteps = true,
                                busy = false,
                            )
                        }
                        historyRepository.add(
                            "$latex1\n$latex2",
                            solved.plain,
                            solved.steps.joinToString("\n") { "${it.title}：${it.detail}" },
                            "system",
                        )
                    }
                }
            } catch (e: CalcException) {
                _state.update { it.copy(error = e.message, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "计算失败：${e.message ?: e.javaClass.simpleName}", busy = false) }
            }
        }
    }

    fun restore(item: HistoryEntity) {
        _state.value = when (item.mode) {
            "quadratic" -> CalcUiState(
                mode = CalcMode.QUADRATIC,
                input = item.latex,
                result = item.result,
            )
            "system" -> {
                val lines = item.latex.split('\n')
                CalcUiState(
                    mode = CalcMode.SYSTEM,
                    input = lines.getOrElse(0) { "" },
                    input2 = lines.getOrElse(1) { "" },
                    result = item.result,
                )
            }
            else -> CalcUiState(
                mode = CalcMode.CALC,
                input = item.latex,
                result = item.result,
            )
        }
    }

    // ---------- 输入字符串处理 ----------

    private fun insertToken(source: String, token: String): String {
        val index = source.indexOf(PLACEHOLDER)
        return if (index >= 0) {
            source.substring(0, index) + token + source.substring(index + PLACEHOLDER.length)
        } else {
            source + token
        }
    }

    private fun deleteTail(source: String): String {
        if (source.isEmpty()) return source
        for (pattern in TAIL_PATTERNS) {
            val match = pattern.find(source) ?: continue
            if (match.range.last == source.lastIndex) {
                return source.substring(0, match.range.first)
            }
        }
        return source.dropLast(1)
    }

    private companion object {
        const val PLACEHOLDER = "\\placeholder{}"

        /** 按「结构」退格：分数、根号、上标、占位符、命令都整块删除。 */
        val TAIL_PATTERNS = listOf(
            Regex("""\\frac\{[^{}]*\}\{[^{}]*\}$"""),
            Regex("""\\sqrt(\[[^\]]*\])?\{[^{}]*\}$"""),
            Regex("""\^\{[^{}]*\}$"""),
            Regex("""\\placeholder\{\}$"""),
            Regex("""\\[a-zA-Z]+$"""),
        )
    }
}
