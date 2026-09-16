package com.example.cactusterminal

import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import com.cactus.CactusLM
import com.cactus.ChatMessage

/**
 * Thin wrapper around CactusLM: finds the Needle 2 tool-calling model, loads it once,
 * and runs the "ask model -> execute any tool calls -> ask model again" loop so the
 * terminal only ever has to deal with plain text in and plain text out.
 */
class CactusManager(private val toolExecutor: ToolExecutor) {

    private val lm = CactusLM()
    private var modelSlug: String = "needle" // resolved for real in ensureReady()
    private var ready = false

    /** Downloads + initializes the model on first call. Safe to call repeatedly. */
    suspend fun ensureReady(onStatus: (String) -> Unit) {
        if (ready) return

        onStatus("Looking up available models...")
        val models = runCatching { lm.getModels() }.getOrDefault(emptyList())

        // Needle 2 is Cactus's tiny agentic tool-calling model. Slugs can shift between
        // SDK releases, so match on name/slug rather than hardcoding one string.
        val needle = models.firstOrNull { it.name.contains("Needle", ignoreCase = true) }
            ?: models.firstOrNull { it.slug.contains("needle", ignoreCase = true) }
            ?: models.firstOrNull { it.supports_tool_calling }

        modelSlug = needle?.slug ?: "needle"

        onStatus("Downloading $modelSlug (first run only, ~14MB)...")
        lm.downloadModel(modelSlug)

        onStatus("Loading $modelSlug into memory...")
        lm.initializeModel(CactusInitParams(model = modelSlug, contextSize = 2048))

        ready = true
        onStatus("Ready.")
    }

    /**
     * Sends the user's message to the model. If the model wants to call a tool,
     * this executes it via [toolExecutor] and feeds the result back automatically,
     * returning only the final natural-language answer.
     */
    suspend fun send(userText: String, onToolRun: (String, String) -> Unit): String {
        val history = mutableListOf(ChatMessage(content = userText, role = "user"))

        repeat(4) { // hard cap so a confused model can't loop forever
            val result = lm.generateCompletion(
                messages = history,
                params = CactusCompletionParams(
                    tools = toolExecutor.allTools,
                    maxTokens = 200
                )
            ) ?: return "No response from model."

            val calls = result.toolCalls.orEmpty()
            if (calls.isEmpty()) {
                return result.response ?: "(empty response)"
            }

            // Execute every requested tool call and feed the results back as context.
            for (call in calls) {
                val output = toolExecutor.execute(call.name, call.arguments)
                onToolRun(call.name, output)
                history.add(
                    ChatMessage(
                        content = "Tool ${call.name} returned: $output",
                        role = "user"
                    )
                )
            }
        }
        return "(stopped after several tool calls without a final answer)"
    }

    fun unload() {
        if (ready) lm.unload()
    }
}
