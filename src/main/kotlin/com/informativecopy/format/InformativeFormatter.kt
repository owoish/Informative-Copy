package com.informativecopy.format

import com.informativecopy.settings.InformativeCopySettings
import com.informativecopy.settings.LineStyle
import com.informativecopy.settings.PathStyle
import com.informativecopy.settings.SingleLineStyle

data class CodeSnippet(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val contextTag: String? = null
)

data class FileChangeEntry(
    val status: String,
    val path: String,
    val oldPath: String? = null
)

data class CommitInfo(
    val shortHash: String,
    val fullHash: String,
    val author: String,
    val date: String,
    val message: String,
    val changedFiles: List<FileChangeEntry>,
    val diff: String? = null
)

data class DiagnosticEntry(
    val path: String?,
    val line: Int?,
    val message: String,
    val sourceLine: String? = null
)

data class FileEntry(
    val path: String,
    val isDirectory: Boolean,
    val content: String? = null
)

object InformativeFormatter {

    fun normalizePath(fullPath: String, basePath: String?, style: PathStyle): String {
        val normalizedFull = fullPath.replace('\\', '/').trimEnd('/')
        return when (style) {
            PathStyle.ABSOLUTE -> normalizedFull
            PathStyle.FILE_NAME -> normalizedFull.substringAfterLast('/')
            PathStyle.RELATIVE -> {
                if (basePath.isNullOrBlank()) {
                    normalizedFull
                } else {
                    val normalizedBase = basePath.replace('\\', '/').trimEnd('/')
                    val prefix = "$normalizedBase/"
                    if (normalizedFull.startsWith(prefix, ignoreCase = isWindowsStyle(normalizedBase))) {
                        normalizedFull.substring(prefix.length)
                    } else if (normalizedFull.equals(normalizedBase, ignoreCase = isWindowsStyle(normalizedBase))) {
                        normalizedFull.substringAfterLast('/')
                    } else {
                        normalizedFull
                    }
                }
            }
        }
    }

    private fun isWindowsStyle(path: String): Boolean {
        return path.length >= 2 && path[1] == ':'
    }

    fun formatLocation(path: String, startLine: Int, endLine: Int, lineStyle: LineStyle, contextTag: String? = null): String {
        val tagPart = if (!contextTag.isNullOrBlank()) " [$contextTag]" else ""
        val single = startLine == endLine
        val linePart = when (lineStyle) {
            LineStyle.PARENTHESES -> if (single) " (line $startLine)" else " (lines $startLine-$endLine)"
            LineStyle.COLON -> if (single) ":$startLine" else ":$startLine-$endLine"
            LineStyle.HASH_L -> if (single) "#L$startLine" else "#L$startLine-L$endLine"
        }
        return "$path$tagPart$linePart"
    }

    fun formatCodeSnippets(snippets: List<CodeSnippet>, state: InformativeCopySettings.State): String {
        return snippets.joinToString("\n\n") { snippet ->
            val header = formatLocation(
                path = snippet.path,
                startLine = snippet.startLine,
                endLine = snippet.endLine,
                lineStyle = state.lineStyle,
                contextTag = snippet.contextTag
            )
            val normalizedText = snippet.text.replace("\r\n", "\n").replace('\r', '\n')
            val isSingleLine = snippet.startLine == snippet.endLine && !normalizedText.contains('\n')

            val body = if (isSingleLine) {
                val content = if (state.trimSingleLine) normalizedText.trim() else normalizedText
                when (state.singleLineStyle) {
                    SingleLineStyle.DOUBLE_QUOTES -> "\"$content\""
                    SingleLineStyle.CJK_QUOTES -> "“$content”"
                    SingleLineStyle.CODE_FENCE -> wrapCodeFence(content, languageForPath(snippet.path))
                    SingleLineStyle.PLAIN -> content
                }
            } else {
                val processed = if (state.dedentMultiLine) dedent(normalizedText) else normalizedText.trimEnd('\n')
                wrapCodeFence(processed, languageForPath(snippet.path))
            }

            if (body.isEmpty()) header else "$header\n$body"
        }
    }

    fun formatCommits(commits: List<CommitInfo>, state: InformativeCopySettings.State): String {
        return commits.joinToString("\n\n---\n\n") { commit ->
            buildString {
                val metaParts = listOfNotNull(
                    commit.date.takeIf { it.isNotBlank() },
                    commit.author.takeIf { it.isNotBlank() }
                ).joinToString(", ")

                append("Commit ${commit.shortHash}")
                if (metaParts.isNotEmpty()) {
                    append(" ($metaParts)")
                }

                val cleanMessage = commit.message.replace("\r\n", "\n").trim()
                if (cleanMessage.isNotEmpty()) {
                    append('\n')
                    append(cleanMessage)
                }

                if (commit.changedFiles.isNotEmpty()) {
                    append("\n\nChanged files (${commit.changedFiles.size}):\n")
                    append(formatFileChangeLines(commit.changedFiles))
                }

                val diff = commit.diff?.replace("\r\n", "\n")?.trimEnd()
                if (!diff.isNullOrBlank()) {
                    val (truncated, omitted) = truncateLines(diff, state.maxDiffLines)
                    append("\n\n")
                    append(wrapCodeFence(truncated, "diff"))
                    if (omitted > 0) {
                        append("\n... ($omitted more diff lines omitted)")
                    }
                }
            }
        }
    }

    fun formatLocalChanges(
        changes: List<FileChangeEntry>,
        diff: String?,
        state: InformativeCopySettings.State
    ): String {
        return buildString {
            val label = if (changes.size == 1) "1 file" else "${changes.size} files"
            append("Local changes ($label):\n")
            append(formatFileChangeLines(changes))

            val cleanDiff = diff?.replace("\r\n", "\n")?.trimEnd()
            if (!cleanDiff.isNullOrBlank()) {
                val (truncated, omitted) = truncateLines(cleanDiff, state.maxDiffLines)
                append("\n\n")
                append(wrapCodeFence(truncated, "diff"))
                if (omitted > 0) {
                    append("\n... ($omitted more diff lines omitted)")
                }
            }
        }
    }

    fun formatDiagnostics(entries: List<DiagnosticEntry>, state: InformativeCopySettings.State): String {
        return entries.joinToString("\n\n") { entry ->
            buildString {
                if (!entry.path.isNullOrBlank()) {
                    if (entry.line != null && entry.line > 0) {
                        append(formatLocation(entry.path, entry.line, entry.line, state.lineStyle))
                    } else {
                        append(entry.path)
                    }
                    if (entry.message.isNotBlank()) {
                        append('\n')
                    }
                }
                if (entry.message.isNotBlank()) {
                    append(entry.message.trim())
                }
                val src = entry.sourceLine?.trim()
                if (!src.isNullOrBlank()) {
                    append("\n> ")
                    append(src)
                }
            }
        }
    }

    fun formatFiles(files: List<FileEntry>, state: InformativeCopySettings.State): String {
        if (files.size == 1) {
            val file = files.first()
            val displayPath = if (file.isDirectory) "${file.path}/" else file.path
            val content = file.content?.replace("\r\n", "\n")?.trimEnd()
            return if (!content.isNullOrBlank()) {
                val (truncated, omitted) = truncateLines(content, state.maxDiffLines)
                buildString {
                    append(displayPath)
                    append('\n')
                    append(wrapCodeFence(truncated, languageForPath(file.path)))
                    if (omitted > 0) {
                        append("\n... ($omitted more lines omitted)")
                    }
                }
            } else {
                displayPath
            }
        }

        return buildString {
            append("Files (${files.size}):\n")
            files.forEachIndexed { index, file ->
                if (index > 0) append('\n')
                append(if (file.isDirectory) "${file.path}/" else file.path)
            }
        }
    }

    fun formatConsole(title: String, text: String): String {
        val clean = text.replace("\r\n", "\n").trimEnd('\n')
        return "[$title]\n${wrapCodeFence(clean, "")}"
    }

    fun formatFileChangeLines(changes: List<FileChangeEntry>): String {
        return changes.joinToString("\n") { change ->
            if (!change.oldPath.isNullOrBlank() && change.oldPath != change.path) {
                "${change.status} ${change.oldPath} -> ${change.path}"
            } else {
                "${change.status} ${change.path}"
            }
        }
    }

    fun dedent(text: String): String {
        val lines = text.trimEnd('\n').split('\n')
        val minIndent = lines
            .filter { it.isNotBlank() }
            .minOfOrNull { line -> line.indexOfFirst { it != ' ' && it != '\t' }.let { if (it == -1) 0 else it } }
            ?: 0

        if (minIndent == 0) return lines.joinToString("\n")
        return lines.joinToString("\n") { line ->
            if (line.length >= minIndent) line.substring(minIndent) else line.trimStart()
        }
    }

    fun truncateLines(text: String, maxLines: Int): Pair<String, Int> {
        if (maxLines <= 0) return text to 0
        val lines = text.split('\n')
        if (lines.size <= maxLines) return text to 0
        val kept = lines.take(maxLines).joinToString("\n")
        return kept to (lines.size - maxLines)
    }

    fun wrapCodeFence(content: String, language: String): String {
        val fence = if (content.contains("```")) "````" else "```"
        return "$fence$language\n$content\n$fence"
    }

    fun languageForPath(path: String): String {
        val fileName = path.substringAfterLast('/').lowercase()
        if (fileName == "cmakelists.txt") return "cmake"
        if (fileName == "dockerfile") return "dockerfile"
        if (fileName == "makefile") return "makefile"

        return when (fileName.substringAfterLast('.', "")) {
            "cpp", "cc", "cxx", "c++", "hpp", "hh", "hxx", "h", "inl", "ipp" -> "cpp"
            "c" -> "c"
            "cs", "csx" -> "csharp"
            "cshtml", "razor" -> "razor"
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "mjs", "cjs" -> "javascript"
            "jsx" -> "jsx"
            "ts", "mts", "cts" -> "typescript"
            "tsx" -> "tsx"
            "rs" -> "rust"
            "go" -> "go"
            "swift" -> "swift"
            "rb" -> "ruby"
            "php" -> "php"
            "lua" -> "lua"
            "hlsl", "usf", "ush", "fx" -> "hlsl"
            "glsl", "vert", "frag", "comp" -> "glsl"
            "shader" -> "hlsl"
            "json", "uproject", "uplugin" -> "json"
            "xml", "csproj", "fsproj", "vbproj", "props", "targets", "xaml", "axaml", "plist", "svg" -> "xml"
            "yaml", "yml" -> "yaml"
            "toml" -> "toml"
            "ini", "cfg", "conf" -> "ini"
            "md", "markdown" -> "markdown"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss" -> "scss"
            "sql" -> "sql"
            "sh", "bash", "zsh" -> "bash"
            "ps1", "psm1" -> "powershell"
            "bat", "cmd" -> "bat"
            "cmake" -> "cmake"
            "gradle" -> "groovy"
            "diff", "patch" -> "diff"
            else -> ""
        }
    }
}
