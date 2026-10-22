package com.mahad.openbuddy.data.remote.github

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val BASE_URL = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
    }

    private fun buildRequest(
        token: String,
        path: String,
        method: String = "GET",
        bodyJson: String? = null
    ): Request {
        val cleanToken = token.trim()
        val authHeader = when {
            cleanToken.startsWith("Bearer ", ignoreCase = true) -> cleanToken
            cleanToken.startsWith("token ", ignoreCase = true) -> cleanToken
            else -> "Bearer $cleanToken"
        }

        val requestBuilder = Request.Builder()
            .url(if (path.startsWith("http")) path else "$BASE_URL$path")
            .header("Authorization", authHeader)
            .header("Accept", "application/vnd.github.v3+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "Open-Buddy-Android")

        when (method.uppercase()) {
            "POST" -> requestBuilder.post((bodyJson ?: "{}").toRequestBody(jsonMediaType))
            "PUT" -> requestBuilder.put((bodyJson ?: "{}").toRequestBody(jsonMediaType))
            "PATCH" -> requestBuilder.patch((bodyJson ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> requestBuilder.delete((bodyJson ?: "").toRequestBody(jsonMediaType))
            else -> requestBuilder.get()
        }

        return requestBuilder.build()
    }

    private fun handleHttpError(response: Response, responseBody: String): GitHubException {
        val code = response.code
        val message = try {
            val json = JSONObject(responseBody)
            json.optString("message", "HTTP $code")
        } catch (_: Exception) {
            "HTTP error $code: ${response.message}"
        }

        return when (code) {
            401 -> GitHubException.InvalidToken("Bad credentials (401). Please check your Personal Access Token in Settings.")
            403 -> {
                val remaining = response.header("x-ratelimit-remaining")
                val reset = response.header("x-ratelimit-reset")?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 3600)
                if (remaining == "0" || message.contains("rate limit", ignoreCase = true)) {
                    GitHubException.RateLimited(reset, "GitHub API rate limit reached. Limit will reset at ${java.text.SimpleDateFormat.getTimeInstance().format(java.util.Date(reset * 1000))}.")
                } else {
                    GitHubException.MissingPermissions("Access denied (403): $message. Check if your PAT has 'Contents: Read and write' permissions.")
                }
            }
            404 -> GitHubException.NotFound("Resource not found (404). Verify repository or file path.")
            409 -> GitHubException.MergeConflict("Conflict (409): The file or branch has been updated remotely. Please reload before committing.")
            else -> GitHubException.GenericError("GitHub API error ($code): $message")
        }
    }

    suspend fun getUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(token, "/user")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    Result.success(GitHubUser.fromJson(json))
                } else {
                    Result.failure(handleHttpError(response, body))
                }
            }
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRepositories(
        token: String,
        sort: String = "updated",
        perPage: Int = 100
    ): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            // Follow pagination — previously only the first 100 repos were ever shown.
            val repos = mutableListOf<GitHubRepo>()
            var page = 1
            while (page <= 10) {
                val request = buildRequest(token, "/user/repos?sort=$sort&per_page=$perPage&page=$page&affiliation=owner,collaborator,organization_member")
                var pageCount = 0
                val pageResult = client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val array = JSONArray(body)
                        pageCount = array.length()
                        for (i in 0 until array.length()) {
                            val repoObj = array.optJSONObject(i)
                            if (repoObj != null) {
                                repos.add(GitHubRepo.fromJson(repoObj))
                            }
                        }
                        true
                    } else {
                        if (page == 1) {
                            return@withContext Result.failure(handleHttpError(response, body))
                        }
                        false
                    }
                }
                if (!pageResult || pageCount < perPage) break
                page++
            }
            Result.success(repos)
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBranches(
        token: String,
        owner: String,
        repo: String
    ): Result<List<GitHubBranch>> = withContext(Dispatchers.IO) {
        try {
            // Follow pagination — previously only the first 100 branches were shown.
            val branches = mutableListOf<GitHubBranch>()
            var page = 1
            while (page <= 10) {
                val request = buildRequest(token, "/repos/$owner/$repo/branches?per_page=100&page=$page")
                var pageCount = 0
                val pageOk = client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val array = JSONArray(body)
                        pageCount = array.length()
                        for (i in 0 until array.length()) {
                            val branchObj = array.optJSONObject(i)
                            if (branchObj != null) {
                                branches.add(GitHubBranch.fromJson(branchObj))
                            }
                        }
                        true
                    } else {
                        if (page == 1) {
                            return@withContext Result.failure(handleHttpError(response, body))
                        }
                        false
                    }
                }
                if (!pageOk || pageCount < 100) break
                page++
            }
            Result.success(branches)
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTree(
        token: String,
        owner: String,
        repo: String,
        branchOrSha: String,
        recursive: Boolean = true
    ): Result<GitHubTreeResponse> = withContext(Dispatchers.IO) {
        try {
            val path = "/repos/$owner/$repo/git/trees/$branchOrSha" + if (recursive) "?recursive=1" else ""
            val request = buildRequest(token, path)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    Result.success(GitHubTreeResponse.fromJson(json))
                } else {
                    Result.failure(handleHttpError(response, body))
                }
            }
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileContent(
        token: String,
        owner: String,
        repo: String,
        path: String,
        ref: String
    ): Result<GitHubContent> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = path.trimStart('/').split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            val request = buildRequest(token, "/repos/$owner/$repo/contents/$encodedPath?ref=$ref")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val size = json.optLong("size", 0L)
                    if (size > 2 * 1024 * 1024) {
                        return@use Result.failure<GitHubContent>(GitHubException.LargeFile(size))
                    }
                    Result.success(GitHubContent.fromJson(json))
                } else {
                    Result.failure(handleHttpError(response, body))
                }
            }
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBranch(
        token: String,
        owner: String,
        repo: String,
        newBranch: String,
        baseSha: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("ref", "refs/heads/$newBranch")
                put("sha", baseSha)
            }
            val request = buildRequest(token, "/repos/$owner/$repo/git/refs", method = "POST", bodyJson = payload.toString())
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 201) {
                    Result.success(Unit)
                } else {
                    Result.failure(handleHttpError(response, body))
                }
            }
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String,
        sha: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = path.trimStart('/').split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            val base64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            var currentSha = sha

            // If sha is missing, fetch latest sha from remote to prevent conflict
            if (currentSha.isNullOrBlank()) {
                val getReq = buildRequest(token, "/repos/$owner/$repo/contents/$encodedPath?ref=$branch")
                client.newCall(getReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        currentSha = JSONObject(body).optString("sha", null)
                    }
                }
            }

            val payload = JSONObject().apply {
                put("message", message.trim().ifBlank { "Update $path via Open Buddy" })
                put("content", base64Content)
                put("branch", branch)
                if (!currentSha.isNullOrBlank()) {
                    put("sha", currentSha)
                }
            }

            var request = buildRequest(token, "/repos/$owner/$repo/contents/$encodedPath", method = "PUT", bodyJson = payload.toString())
            var response = client.newCall(request).execute()
            var body = response.body?.string() ?: ""

            // If 409 conflict, query remote for fresh sha and retry once
            if (response.code == 409) {
                response.close()
                val getReq = buildRequest(token, "/repos/$owner/$repo/contents/$encodedPath?ref=$branch")
                client.newCall(getReq).execute().use { freshResp ->
                    if (freshResp.isSuccessful) {
                        val freshBody = freshResp.body?.string() ?: ""
                        val freshSha = JSONObject(freshBody).optString("sha", null)
                        if (!freshSha.isNullOrBlank()) {
                            payload.put("sha", freshSha)
                            request = buildRequest(token, "/repos/$owner/$repo/contents/$encodedPath", method = "PUT", bodyJson = payload.toString())
                            response = client.newCall(request).execute()
                            body = response.body?.string() ?: ""
                        }
                    }
                }
            }

            response.use { resp ->
                if (resp.isSuccessful || resp.code == 200 || resp.code == 201) {
                    val json = JSONObject(body)
                    val commitObj = json.optJSONObject("commit")
                    val commitSha = commitObj?.optString("sha", "") ?: "committed"
                    Result.success(commitSha)
                } else {
                    Result.failure(handleHttpError(resp, body))
                }
            }
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun commitMultipleFiles(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        commitMessage: String,
        modifiedFiles: List<ModifiedFile>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (modifiedFiles.isEmpty()) {
                return@withContext Result.failure(GitHubException.GenericError("No files modified to commit."))
            }

            // High-reliability fast path for single-file edits (standard for AI code edits)
            if (modifiedFiles.size == 1) {
                val single = modifiedFiles[0]
                val directResult = createOrUpdateFile(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = single.path,
                    content = single.newContent,
                    message = commitMessage,
                    branch = branch,
                    sha = single.originalSha
                )
                if (directResult.isSuccess) {
                    return@withContext directResult
                }
                // If direct single-file update returned 409 or conflict, fallback to git tree
            }

            // 1. Get latest commit SHA and base tree SHA for the target branch
            val branchRequest = buildRequest(token, "/repos/$owner/$repo/branches/$branch")
            var latestCommitSha = ""
            var baseTreeSha = ""

            client.newCall(branchRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val commitObj = json.optJSONObject("commit")
                    latestCommitSha = commitObj?.optString("sha", "") ?: ""
                    val innerCommit = commitObj?.optJSONObject("commit")
                    val treeObj = innerCommit?.optJSONObject("tree")
                    baseTreeSha = treeObj?.optString("sha", "") ?: ""
                }
            }

            // Fallback if branch endpoint did not include tree sha
            if (latestCommitSha.isBlank()) {
                val refRequest = buildRequest(token, "/repos/$owner/$repo/git/refs/heads/$branch")
                client.newCall(refRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(handleHttpError(response, body))
                    }
                    val json = JSONObject(body)
                    latestCommitSha = json.getJSONObject("object").getString("sha")
                }
            }

            if (baseTreeSha.isBlank()) {
                val commitRequest = buildRequest(token, "/repos/$owner/$repo/git/commits/$latestCommitSha")
                client.newCall(commitRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(handleHttpError(response, body))
                    }
                    val json = JSONObject(body)
                    baseTreeSha = json.getJSONObject("tree").getString("sha")
                }
            }

            // 3. Create blob for each modified file
            val treeEntries = JSONArray()
            for (file in modifiedFiles) {
                val blobPayload = JSONObject().apply {
                    put("content", file.newContent)
                    put("encoding", "utf-8")
                }
                val blobRequest = buildRequest(token, "/repos/$owner/$repo/git/blobs", method = "POST", bodyJson = blobPayload.toString())
                var blobSha: String
                client.newCall(blobRequest).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(handleHttpError(response, body))
                    }
                    val json = JSONObject(body)
                    blobSha = json.getString("sha")
                }

                val treeItem = JSONObject().apply {
                    put("path", file.path.trimStart('/'))
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                }
                treeEntries.put(treeItem)
            }

            // 4. Create new Git Tree
            val treePayload = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", treeEntries)
            }
            val createTreeRequest = buildRequest(token, "/repos/$owner/$repo/git/trees", method = "POST", bodyJson = treePayload.toString())
            var newTreeSha: String
            client.newCall(createTreeRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(handleHttpError(response, body))
                }
                val json = JSONObject(body)
                newTreeSha = json.getString("sha")
            }

            // 5. Create new Git Commit
            val commitPayload = JSONObject().apply {
                put("message", commitMessage.trim())
                put("tree", newTreeSha)
                put("parents", JSONArray().put(latestCommitSha))
            }
            val createCommitRequest = buildRequest(token, "/repos/$owner/$repo/git/commits", method = "POST", bodyJson = commitPayload.toString())
            var newCommitSha: String
            client.newCall(createCommitRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(handleHttpError(response, body))
                }
                val json = JSONObject(body)
                newCommitSha = json.getString("sha")
            }

            // 6. Update reference refs/heads/{branch}
            val updateRefPayload = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }
            val updateRefRequest = buildRequest(token, "/repos/$owner/$repo/git/refs/heads/$branch", method = "PATCH", bodyJson = updateRefPayload.toString())
            client.newCall(updateRefRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // If 404, the branch might be new and needs POST to create ref
                    if (response.code == 404) {
                        val createRefPayload = JSONObject().apply {
                            put("ref", "refs/heads/$branch")
                            put("sha", newCommitSha)
                        }
                        val createRefRequest = buildRequest(token, "/repos/$owner/$repo/git/refs", method = "POST", bodyJson = createRefPayload.toString())
                        client.newCall(createRefRequest).execute().use { createRefResp ->
                            val createRefBody = createRefResp.body?.string() ?: ""
                            if (!createRefResp.isSuccessful && createRefResp.code != 201) {
                                return@withContext Result.failure(handleHttpError(createRefResp, createRefBody))
                            }
                        }
                    } else {
                        return@withContext Result.failure(handleHttpError(response, body))
                    }
                }
            }

            Result.success(newCommitSha)
        } catch (e: IOException) {
            Result.failure(GitHubException.NetworkError("Network error during push: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
