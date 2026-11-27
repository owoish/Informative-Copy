package com.informativecopy.format

import com.informativecopy.settings.InformativeCopySettings
import com.informativecopy.settings.LineStyle
import com.informativecopy.settings.PathStyle
import com.informativecopy.settings.SingleLineStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InformativeFormatterTest {

    @Test
    fun `single line selection uses default path, line, and double quotes`() {
        val state = InformativeCopySettings.State()
        val result = InformativeFormatter.formatCodeSnippets(
            snippets = listOf(
                CodeSnippet(
                    path = "SomeFolder/SomeFile.cpp",
                    startLine = 37,
                    endLine = 37,
                    text = "    SomeFunction();  "
                )
            ),
            state = state
        )

        assertEquals(
            """
            SomeFolder/SomeFile.cpp (line 37)
            "SomeFunction();"
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `single line selection supports CJK quotes and colon line style`() {
        val state = InformativeCopySettings.State(
            lineStyle = LineStyle.COLON,
            singleLineStyle = SingleLineStyle.CJK_QUOTES
        )
        val result = InformativeFormatter.formatCodeSnippets(
            snippets = listOf(
                CodeSnippet(
                    path = "SomeFolder/SomeFile.cpp",
                    startLine = 37,
                    endLine = 37,
                    text = "SomeFunction"
                )
            ),
            state = state
        )

        assertEquals(
            """
            SomeFolder/SomeFile.cpp:37
            “SomeFunction”
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `multi-line snippet dedents and wraps with detected language`() {
        val state = InformativeCopySettings.State()
        val result = InformativeFormatter.formatCodeSnippets(
            snippets = listOf(
                CodeSnippet(
                    path = "Source/Game/Player.cpp",
                    startLine = 10,
                    endLine = 13,
                    text = "    void Tick() {\n        SomeFunction();\n    }\n"
                )
            ),
            state = state
        )

        assertEquals(
            """
            Source/Game/Player.cpp (lines 10-13)
            ```cpp
            void Tick() {
                SomeFunction();
            }
            ```
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `normalizePath handles Windows paths case-insensitively`() {
        val rel = InformativeFormatter.normalizePath(
            fullPath = "D:\\Projects\\MyGame\\Source\\Player.cpp",
            basePath = "d:/projects/mygame",
            style = PathStyle.RELATIVE
        )
        assertEquals("Source/Player.cpp", rel)

        val fileName = InformativeFormatter.normalizePath(
            fullPath = "D:\\Projects\\MyGame\\Source\\Player.cpp",
            basePath = "d:/projects/mygame",
            style = PathStyle.FILE_NAME
        )
        assertEquals("Player.cpp", fileName)
    }

    @Test
    fun `commit formatting includes metadata, changed files, and truncates long diffs`() {
        val state = InformativeCopySettings.State(maxDiffLines = 2)
        val commit = CommitInfo(
            shortHash = "a1b2c3d4",
            fullHash = "a1b2c3d4e5f6",
            author = "Alice",
            date = "2026-11-27 14:20",
            message = "Fix respawn crash\n\nGuard null controller during cutscene.",
            changedFiles = listOf(
                FileChangeEntry("M", "Source/Player.cpp"),
                FileChangeEntry("R", "Source/NewName.h", oldPath = "Source/OldName.h")
            ),
            diff = "line1\nline2\nline3\nline4"
        )

        val result = InformativeFormatter.formatCommits(listOf(commit), state)
        assertEquals(
            """
            Commit a1b2c3d4 (2026-11-27 14:20, Alice)
            Fix respawn crash

            Guard null controller during cutscene.

            Changed files (2):
            M Source/Player.cpp
            R Source/OldName.h -> Source/NewName.h

            ```diff
            line1
            line2
            ```
            ... (2 more diff lines omitted)
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `diagnostic formatting includes path, line, error message, and source line`() {
        val state = InformativeCopySettings.State()
        val result = InformativeFormatter.formatDiagnostics(
            entries = listOf(
                DiagnosticEntry(
                    path = "Source/Player.cpp",
                    line = 42,
                    message = "error C2065: 'SomeFunction': undeclared identifier",
                    sourceLine = "    SomeFunction();"
                )
            ),
            state = state
        )

        assertEquals(
            """
            Source/Player.cpp (line 42)
            error C2065: 'SomeFunction': undeclared identifier
            > SomeFunction();
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `local changes formatting handles multiple files cleanly`() {
        val state = InformativeCopySettings.State()
        val result = InformativeFormatter.formatLocalChanges(
            changes = listOf(
                FileChangeEntry("M", "Source/A.cpp"),
                FileChangeEntry("A", "Source/B.h")
            ),
            diff = null,
            state = state
        )

        assertEquals(
            """
            Local changes (2 files):
            M Source/A.cpp
            A Source/B.h
            """.trimIndent(),
            result
        )
    }
}
