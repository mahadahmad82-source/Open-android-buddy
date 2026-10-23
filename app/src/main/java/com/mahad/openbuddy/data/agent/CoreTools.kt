package com.mahad.openbuddy.data.agent

import com.mahad.openbuddy.data.local.dao.MemoryDao
import com.mahad.openbuddy.data.local.entity.MemoryEntity
import com.mahad.openbuddy.data.media.MediaGenerationService
import com.mahad.openbuddy.data.remote.github.GitHubApiService
import com.mahad.openbuddy.data.repository.SupabaseRepository
import com.mahad.openbuddy.data.security.SecretStore
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.domain.model.StatementSafetyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Phase 2/6/7 — core agent tools: time, web fetch, GitHub, the Supabase
 * database agent, long-term memory, and media generation.
 */
class CoreTools(
    private val secretStore: SecretStore,
    private val gitHubApiService: GitHubApiService,
    private val supabaseRepository: SupabaseRepository,
    private val memoryDao: MemoryDao,
    private val mediaService: MediaGenerationService,
    /**
     * Supplies (provider config, api key) pairs for media tools, resolved
     * across ALL saved providers in preference order — so media generation
     * works even when a different provider is active for chat, and video
     * generation can fail over from one backend to the next.
     */
    private val mediaProviders: suspend (forVideo: Boolean) -> List<Pair<AiProviderConfig, String>>
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun all(): List<AgentTool> = listOf(

        object : AgentTool {
            override val name = "current_datetime"
            override val description = "Get the current date and time on the device. No arguments."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val now = Date()
                val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
                val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                return ToolOutcome("Today is ${dateFmt.format(now)}; the time is ${timeFmt.format(now)} (device timezone ${java.util.TimeZone.getDefault().id}).")
            }
        },

        object : AgentTool {
            override val name = "web_fetch"
            override val description = "Fetch a web page and return its readable text (for reading links, docs, articles). Arguments: {\"url\": \"https://...\"}."
            override suspend fun execute(args: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
                val url = args.optString("url").trim()
                if (!url.startsWith("http")) return@withContext ToolOutcome("A valid http(s) URL is required.")
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Android) OpenBuddy")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            return@use ToolOutcome("Fetch failed: HTTP ${response.code}.")
                        }
                        val text = body
                            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                            .replace(Regex("<[^>]+>"), " ")
                            .replace(Regex("&nbsp;"), " ")
                            .replace(Regex("&amp;"), "&")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        ToolOutcome(text.take(6000).ifBlank { "(page had no readable text)" })
                    }
                } catch (e: Exception) {
                    ToolOutcome("Fetch failed: ${e.message}")
                }
            }
        },

        object : AgentTool {
            override val name = "github_list_repos"
            override val description = "List the user's GitHub repositories (uses the token saved in Settings). No arguments."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val token = secretStore.getGitHubToken()
                    ?: return ToolOutcome("No GitHub token is configured. Tell the user to add one in Settings > Tokens Vault.")
                val result = gitHubApiService.getRepositories(token)
                return result.fold(
                    onSuccess = { repos ->
                        if (repos.isEmpty()) ToolOutcome("No repositories found.")
                        else ToolOutcome(
                            repos.take(60).joinToString("\n") { repo ->
                                "- ${repo.fullName} (${if (repo.isPrivate) "private" else "public"}, default branch ${repo.defaultBranch})${repo.description?.let { d -> " — $d" } ?: ""}"
                            }
                        )
                    },
                    onFailure = { ToolOutcome("GitHub error: ${it.message}") }
                )
            }
        },

        object : AgentTool {
            override val name = "github_read_file"
            override val description = "Read a file from a GitHub repository. Arguments: {\"owner\": \"...\", \"repo\": \"...\", \"path\": \"...\", \"branch\": \"main\" (optional)}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val token = secretStore.getGitHubToken()
                    ?: return ToolOutcome("No GitHub token is configured.")
                val owner = args.optString("owner")
                val repo = args.optString("repo")
                val path = args.optString("path")
                val branch = args.optString("branch", "main").ifBlank { "main" }
                if (owner.isBlank() || repo.isBlank() || path.isBlank()) {
                    return ToolOutcome("owner, repo and path are all required.")
                }
                val result = gitHubApiService.getFileContent(token, owner, repo, path, branch)
                return result.fold(
                    onSuccess = { content ->
                        val raw = content.content?.replace("\n", "") ?: ""
                        val decoded = try {
                            String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) {
                            "(binary or undecodable content)"
                        }
                        ToolOutcome(decoded.take(8000))
                    },
                    onFailure = { ToolOutcome("GitHub error: ${it.message}") }
                )
            }
        },

        object : AgentTool {
            override val name = "github_write_file"
            override val description = "Create or update a file in a GitHub repository (commits immediately). Arguments: {\"owner\": \"...\", \"repo\": \"...\", \"path\": \"...\", \"content\": \"...\", \"message\": \"commit message\", \"branch\": \"main\"}. Always asks the user to approve first."
            override val requiresApproval = true
            override fun approvalSummary(args: JSONObject) =
                "Write ${args.optString("path")} to ${args.optString("owner")}/${args.optString("repo")} (${args.optString("branch", "main")})"
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val token = secretStore.getGitHubToken()
                    ?: return ToolOutcome("No GitHub token is configured.")
                val owner = args.optString("owner")
                val repo = args.optString("repo")
                val path = args.optString("path")
                val content = args.optString("content")
                val branch = args.optString("branch", "main").ifBlank { "main" }
                val message = args.optString("message", "Update $path via Open Buddy")
                if (owner.isBlank() || repo.isBlank() || path.isBlank()) {
                    return ToolOutcome("owner, repo and path are all required.")
                }
                val result = gitHubApiService.createOrUpdateFile(token, owner, repo, path, content, message, branch, null)
                return result.fold(
                    onSuccess = { ToolOutcome("Committed $path to $owner/$repo ($branch), commit ${it.take(7)}.") },
                    onFailure = { ToolOutcome("GitHub error: ${it.message}") }
                )
            }
        },

        object : AgentTool {
            override val name = "supabase_sql"
            override val description = "Run ONE SQL statement against the user's connected Supabase/Postgres database and return the result rows. Read-only statements (SELECT etc.) run immediately. Write or destructive statements ask the user to approve first and run with safety checks on. Arguments: {\"sql\": \"SELECT ...\"}."
            override val requiresApproval: Boolean
                get() = false // Decided per-statement in approvalSummary/engine via isWriteStatement below.

            override suspend fun execute(args: JSONObject): ToolOutcome {
                val sql = args.optString("sql").trim()
                if (sql.isBlank()) return ToolOutcome("No SQL provided.")
                if (!supabaseRepository.hasCredentials()) {
                    return ToolOutcome("Supabase is not connected. Tell the user to add the project ref and token in Settings > Tokens Vault.")
                }
                val classification = supabaseRepository.classifyStatement(sql)
                val isWrite = classification.type != StatementSafetyType.READ_ONLY
                if (isWrite) {
                    // The engine already gated this call behind user approval
                    // (see AgentEngine.requiresApprovalFor). Enable write mode
                    // just for this statement, then restore it.
                    supabaseRepository.setWriteMode(true)
                    try {
                        return runSql(sql)
                    } finally {
                        supabaseRepository.setWriteMode(false)
                    }
                }
                return runSql(sql)
            }

            private suspend fun runSql(sql: String): ToolOutcome {
                val result = supabaseRepository.executeQuery(sql)
                return result.fold(
                    onSuccess = { queryResult ->
                        if (queryResult.error != null) {
                            ToolOutcome("SQL error: ${queryResult.error}")
                        } else if (queryResult.columns.isEmpty()) {
                            ToolOutcome("Statement executed successfully (${queryResult.executionTimeMs} ms). No result set.")
                        } else {
                            val header = queryResult.columns.joinToString(" | ")
                            val rowsText = queryResult.rows.take(50).joinToString("\n") { row -> row.joinToString(" | ") }
                            val more = if (queryResult.rowCount > 50) "\n… and ${queryResult.rowCount - 50} more rows" else ""
                            ToolOutcome("$header\n$rowsText$more\n(${queryResult.rowCount} rows, ${queryResult.executionTimeMs} ms)")
                        }
                    },
                    onFailure = { ToolOutcome("SQL error: ${it.message}") }
                )
            }
        },

        object : AgentTool {
            override val name = "remember_fact"
            override val description = "Save a durable fact about the user to long-term memory (preferences, names, projects, decisions). Arguments: {\"fact\": \"...\", \"category\": \"preference\" (optional)}."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val fact = args.optString("fact").trim()
                if (fact.isBlank()) return ToolOutcome("No fact provided.")
                memoryDao.insertMemory(
                    MemoryEntity(
                        id = UUID.randomUUID().toString(),
                        fact = fact,
                        category = args.optString("category", "general").ifBlank { "general" }
                    )
                )
                return ToolOutcome("Saved to memory: \"$fact\"")
            }
        },

        object : AgentTool {
            override val name = "recall_memories"
            override val description = "Search long-term memory for facts about the user. Arguments: {\"query\": \"keyword\"} — empty query returns the most recent memories."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val query = args.optString("query").trim()
                val memories = if (query.isBlank()) memoryDao.getRecentMemories(20)
                else memoryDao.searchMemories(query, 15)
                return if (memories.isEmpty()) ToolOutcome("No matching memories found.")
                else ToolOutcome(memories.joinToString("\n") { "- [${it.category}] ${it.fact}" })
            }
        },

        object : AgentTool {
            override val name = "forget_memory"
            override val description = "Delete memories whose text matches a keyword. Arguments: {\"query\": \"...\"}. Asks the user to approve first."
            override val requiresApproval = true
            override fun approvalSummary(args: JSONObject) = "Forget memories matching \"${args.optString("query")}\""
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val query = args.optString("query").trim()
                if (query.isBlank()) return ToolOutcome("A query is required to know what to forget.")
                val matches = memoryDao.searchMemories(query, 50)
                if (matches.isEmpty()) return ToolOutcome("No memories matched \"$query\".")
                matches.forEach { memoryDao.deleteMemoryById(it.id) }
                return ToolOutcome("Deleted ${matches.size} memor${if (matches.size == 1) "y" else "ies"} matching \"$query\".")
            }
        },

        object : AgentTool {
            override val name = "generate_image"
            override val description = "Generate an image from a text prompt and show it in the chat. The app automatically uses a saved image-capable provider (Gemini preferred) even if another provider is active for chat. Arguments: {\"prompt\": \"...\"}. Use photorealistic prompts when the user asks for photos."
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val prompt = args.optString("prompt").trim()
                if (prompt.isBlank()) return ToolOutcome("An image prompt is required.")
                val provider = mediaProviders(false).firstOrNull()
                    ?: return ToolOutcome("No image-capable provider with an API key was found. Tell the user to add a Gemini provider in Settings → AI Providers.")
                val result = mediaService.generateImage(provider.first, provider.second, prompt)
                return result.fold(
                    onSuccess = { ToolOutcome("Image generated and shown to the user in the chat.", mediaPath = it.absolutePath, mediaKind = "image") },
                    onFailure = { ToolOutcome("Image generation failed: ${it.message}") }
                )
            }
        },

        object : AgentTool {
            override val name = "generate_video"
            override val description = "Generate a short vertical (9:16) video and show it in the chat. Several backends are available — Google Veo (Gemini key), fal.ai (Kling, Luma, MiniMax), Replicate (Veo, Kling, Seedance) and OpenAI-style video gateways such as the user's OmniRoute (which can serve Veo through its Google Flow/Antigravity connection, Runway and more via /videos/generations) — and the app tries them in order until one succeeds, using the user's saved providers automatically even if a different provider is active for chat. Optional argument \"backend\": \"veo\", \"fal\", \"replicate\" or \"omniroute\" to force one. Optional argument \"model\": a specific video model id for the chosen backend (for OmniRoute e.g. \"googleflow/veo-3.1-fast-generate\"). Takes 1-6 minutes. Arguments: {\"prompt\": \"...\"}. Asks the user to approve first because it is slow and uses paid quota."
            override val requiresApproval = true
            override fun approvalSummary(args: JSONObject) = "Generate video: \"${args.optString("prompt").take(80)}\""
            override suspend fun execute(args: JSONObject): ToolOutcome {
                val prompt = args.optString("prompt").trim()
                if (prompt.isBlank()) return ToolOutcome("A video prompt is required.")
                var providers = mediaProviders(true)
                if (providers.isEmpty()) {
                    return ToolOutcome("No video provider with an API key was found. Tell the user: add a Gemini key (Veo), a fal.ai key, a Replicate token, or an OmniRoute/Custom gateway provider in Settings → AI Providers.")
                }
                // Optional backend forcing: "veo" / "fal" / "replicate" / "omniroute".
                val wanted = args.optString("backend").trim().lowercase()
                if (wanted.isNotBlank()) {
                    val filtered = providers.filter { (config, _) ->
                        when (wanted) {
                            "veo", "gemini", "google" -> config.type == com.mahad.openbuddy.domain.model.ProviderType.GEMINI
                            "fal", "fal.ai", "kling", "luma" -> config.type == com.mahad.openbuddy.domain.model.ProviderType.FAL
                            "replicate" -> config.type == com.mahad.openbuddy.domain.model.ProviderType.REPLICATE
                            "omniroute", "gateway", "flow", "googleflow" ->
                                config.type == com.mahad.openbuddy.domain.model.ProviderType.CUSTOM_OPENAI ||
                                    config.type == com.mahad.openbuddy.domain.model.ProviderType.KIMI
                            else -> true
                        }
                    }
                    if (filtered.isNotEmpty()) providers = filtered
                }
                val modelOverride = args.optString("model").trim().takeIf { it.isNotBlank() }
                val failures = mutableListOf<String>()
                for ((config, key) in providers) {
                    val result = mediaService.generateVideo(config, key, prompt, modelOverride)
                    val file = result.getOrNull()
                    if (file != null) {
                        return ToolOutcome(
                            "Video generated with ${config.name} and shown to the user in the chat.",
                            mediaPath = file.absolutePath,
                            mediaKind = "video"
                        )
                    }
                    failures += "${config.name}: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                }
                return ToolOutcome("Video generation failed on every available backend. " + failures.joinToString(" | "))
            }
        }
    )

    companion object {
        /** supabase_sql needs approval only for non-read-only statements. */
        fun supabaseNeedsApproval(supabaseRepository: SupabaseRepository, args: JSONObject): Boolean {
            val sql = args.optString("sql")
            if (sql.isBlank()) return false
            return supabaseRepository.classifyStatement(sql).type != StatementSafetyType.READ_ONLY
        }
    }
}
