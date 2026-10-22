package com.mahad.openbuddy.data.remote.github

import org.json.JSONArray
import org.json.JSONObject

data class GitHubUser(
    val login: String,
    val id: Long,
    val avatarUrl: String?,
    val name: String?,
    val bio: String?,
    val publicRepos: Int,
    val totalPrivateRepos: Int? = null,
    val htmlUrl: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubUser {
            return GitHubUser(
                login = json.optString("login", ""),
                id = json.optLong("id", 0L),
                avatarUrl = json.optString("avatar_url").ifBlank { null },
                name = json.optString("name").ifBlank { null },
                bio = json.optString("bio").ifBlank { null },
                publicRepos = json.optInt("public_repos", 0),
                totalPrivateRepos = if (json.has("total_private_repos")) json.optInt("total_private_repos") else null,
                htmlUrl = json.optString("html_url").ifBlank { null }
            )
        }
    }
}

data class GitHubRepoOwner(
    val login: String,
    val avatarUrl: String?
) {
    companion object {
        fun fromJson(json: JSONObject?): GitHubRepoOwner {
            if (json == null) return GitHubRepoOwner("", null)
            return GitHubRepoOwner(
                login = json.optString("login", ""),
                avatarUrl = json.optString("avatar_url").ifBlank { null }
            )
        }
    }
}

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: GitHubRepoOwner,
    val isPrivate: Boolean,
    val description: String?,
    val defaultBranch: String,
    val language: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val updatedAt: String?,
    val htmlUrl: String
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubRepo {
            return GitHubRepo(
                id = json.optLong("id", 0L),
                name = json.optString("name", ""),
                fullName = json.optString("full_name", ""),
                owner = GitHubRepoOwner.fromJson(json.optJSONObject("owner")),
                isPrivate = json.optBoolean("private", false),
                description = json.optString("description").ifBlank { null },
                defaultBranch = json.optString("default_branch", "main").ifBlank { "main" },
                language = json.optString("language").ifBlank { null },
                stargazersCount = json.optInt("stargazers_count", 0),
                forksCount = json.optInt("forks_count", 0),
                updatedAt = json.optString("updated_at").ifBlank { null },
                htmlUrl = json.optString("html_url", "")
            )
        }
    }
}

data class GitHubBranch(
    val name: String,
    val commitSha: String,
    val isProtected: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubBranch {
            val commitObj = json.optJSONObject("commit")
            return GitHubBranch(
                name = json.optString("name", ""),
                commitSha = commitObj?.optString("sha", "") ?: "",
                isProtected = json.optBoolean("protected", false)
            )
        }
    }
}

data class GitHubTreeItem(
    val path: String,
    val mode: String,
    val type: String, // "blob" or "tree"
    val sha: String,
    val size: Long = 0L,
    val url: String? = null
) {
    val isDirectory: Boolean get() = type == "tree"
    val isFile: Boolean get() = type == "blob"

    companion object {
        fun fromJson(json: JSONObject): GitHubTreeItem {
            return GitHubTreeItem(
                path = json.optString("path", ""),
                mode = json.optString("mode", ""),
                type = json.optString("type", "blob"),
                sha = json.optString("sha", ""),
                size = json.optLong("size", 0L),
                url = json.optString("url").ifBlank { null }
            )
        }
    }
}

data class GitHubTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GitHubTreeItem>,
    val truncated: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubTreeResponse {
            val treeArray = json.optJSONArray("tree") ?: JSONArray()
            val items = mutableListOf<GitHubTreeItem>()
            for (i in 0 until treeArray.length()) {
                val itemObj = treeArray.optJSONObject(i)
                if (itemObj != null) {
                    items.add(GitHubTreeItem.fromJson(itemObj))
                }
            }
            return GitHubTreeResponse(
                sha = json.optString("sha", ""),
                url = json.optString("url", ""),
                tree = items,
                truncated = json.optBoolean("truncated", false)
            )
        }
    }
}

data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String,
    val content: String?,
    val encoding: String?,
    val downloadUrl: String?,
    val htmlUrl: String?
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubContent {
            return GitHubContent(
                name = json.optString("name", ""),
                path = json.optString("path", ""),
                sha = json.optString("sha", ""),
                size = json.optLong("size", 0L),
                type = json.optString("type", "file"),
                content = json.optString("content").ifBlank { null },
                encoding = json.optString("encoding").ifBlank { null },
                downloadUrl = json.optString("download_url").ifBlank { null },
                htmlUrl = json.optString("html_url").ifBlank { null }
            )
        }
    }
}

data class GitHubCommitResponse(
    val sha: String,
    val message: String?,
    val htmlUrl: String?
)

data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val isDraft: Boolean,
    val isMerged: Boolean,
    val headRef: String,
    val headSha: String,
    val baseRef: String,
    val authorLogin: String,
    val htmlUrl: String?,
    val createdAt: String?
) {
    companion object {
        fun fromJson(json: JSONObject): GitHubPullRequest {
            val head = json.optJSONObject("head")
            val base = json.optJSONObject("base")
            val user = json.optJSONObject("user")
            return GitHubPullRequest(
                number = json.optInt("number", 0),
                title = json.optString("title", ""),
                body = json.optString("body").ifBlank { null },
                state = json.optString("state", "open"),
                isDraft = json.optBoolean("draft", false),
                isMerged = json.optBoolean("merged", false) || !json.isNull("merged_at"),
                headRef = head?.optString("ref", "") ?: "",
                headSha = head?.optString("sha", "") ?: "",
                baseRef = base?.optString("ref", "") ?: "",
                authorLogin = user?.optString("login", "") ?: "",
                htmlUrl = json.optString("html_url").ifBlank { null },
                createdAt = json.optString("created_at").ifBlank { null }
            )
        }
    }
}

data class GitHubTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val sha: String,
    val size: Long = 0L,
    val children: MutableList<GitHubTreeNode> = mutableListOf(),
    var isExpanded: Boolean = false
)

data class ModifiedFile(
    val path: String,
    val originalContent: String,
    val newContent: String,
    val originalSha: String,
    val isNew: Boolean = false
) {
    val hasChanges: Boolean get() = originalContent != newContent || isNew
}

enum class DiffLineType {
    ADDED,
    REMOVED,
    UNCHANGED,
    HEADER
}

data class DiffItem(
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
    val type: DiffLineType
)

sealed class GitHubException(override val message: String, val statusCode: Int? = null) : Exception(message) {
    class InvalidToken(msg: String = "Bad credentials (401). Please check your GitHub Personal Access Token in Settings.") :
        GitHubException(msg, 401)

    class MissingPermissions(msg: String = "Insufficient permissions (403). Your PAT requires repository 'Contents: Read and write' scope.") :
        GitHubException(msg, 403)

    class RateLimited(val resetTimestamp: Long, msg: String = "GitHub API rate limit exceeded. Please wait before retrying.") :
        GitHubException(msg, 429)

    class MergeConflict(msg: String = "Conflict (409): The remote file or branch was updated by another commit. Please pull the latest version.") :
        GitHubException(msg, 409)

    class LargeFile(val sizeBytes: Long, msg: String = "File is too large (>1 MB) to be edited safely via GitHub REST API.") :
        GitHubException(msg)

    class NotFound(msg: String = "Resource not found (404). Verify repository name or branch.") :
        GitHubException(msg, 404)

    class NetworkError(msg: String) : GitHubException(msg)
    class GenericError(msg: String) : GitHubException(msg)
}
