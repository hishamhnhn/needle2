package com.example.cactusterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cactus.CactusContextInitializer
import kotlinx.coroutines.launch

// Classic terminal palette
private val TermBlack = Color(0xFF000000)
private val TermGreen = Color(0xFF33FF66)
private val TermDim = Color(0xFF1F8A3E)
private val TermAmberError = Color(0xFFFF5555)

private sealed class Line(val text: String, val color: Color) {
    class Prompt(text: String) : Line("$ $text", TermGreen)
    class Status(text: String) : Line(text, TermDim)
    class ToolRun(text: String) : Line(text, Color(0xFF66CCFF))
    class Answer(text: String) : Line(text, TermGreen)
    class Err(text: String) : Line(text, TermAmberError)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required before any Cactus SDK call
        CactusContextInitializer.initialize(this)

        val toolExecutor = ToolExecutor(applicationContext)
        val cactus = CactusManager(toolExecutor)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = TermBlack)) {
                TerminalScreen(cactus)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalScreen(cactus: CactusManager) {
    val lines = remember {
        mutableStateListOf<Line>(
            Line.Status("Cactus Terminal — Needle 2 on-device agent"),
            Line.Status("Type a command below. e.g. \"what's my battery?\", \"turn on the flashlight\", \"open camera\"")
        )
    }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        busy = true
        try {
            cactus.ensureReady { status -> lines.add(Line.Status(status)) }
            modelReady = true
        } catch (e: Exception) {
            lines.add(Line.Err("Failed to load model: ${e.message}"))
        } finally {
            busy = false
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TermBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TermBlack)
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(lines) { line ->
                    Text(
                        text = line.text,
                        color = line.color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (busy) {
                    item {
                        Text(
                            text = "...",
                            color = TermDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = TermGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    enabled = !busy && modelReady,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TermGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TermBlack,
                        unfocusedContainerColor = TermBlack,
                        disabledContainerColor = TermBlack,
                        focusedIndicatorColor = TermDim,
                        unfocusedIndicatorColor = TermDim,
                        cursorColor = TermGreen
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            submit(input, lines, cactus, scope) { busy = it }
                            input = ""
                        }
                    ),
                    placeholder = {
                        Text(
                            if (modelReady) "type a command..." else "loading model...",
                            color = TermDim,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
                TextButton(
                    enabled = !busy && modelReady && input.isNotBlank(),
                    onClick = {
                        submit(input, lines, cactus, scope) { busy = it }
                        input = ""
                        keyboard?.hide()
                    }
                ) {
                    Text("RUN", color = TermGreen, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun submit(
    text: String,
    lines: MutableList<Line>,
    cactus: CactusManager,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit
) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    lines.add(Line.Prompt(trimmed))
    setBusy(true)
    scope.launch {
        try {
            val answer = cactus.send(trimmed) { toolName, output ->
                lines.add(Line.ToolRun("→ [$toolName] $output"))
            }
            lines.add(Line.Answer(answer))
        } catch (e: Exception) {
            lines.add(Line.Err("Error: ${e.message}"))
        } finally {
            setBusy(false)
        }
    }
}
