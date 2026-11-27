package com.informativecopy.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class PathStyle(val displayName: String) {
    RELATIVE("Relative to project root (e.g. Source/Foo.cpp)"),
    ABSOLUTE("Absolute path"),
    FILE_NAME("File name only (e.g. Foo.cpp)")
}

enum class LineStyle(val displayName: String) {
    PARENTHESES("Parentheses: (line 37) / (lines 37-45)"),
    COLON("Colon: :37 / :37-45"),
    HASH_L("GitHub style: #L37 / #L37-L45")
}

enum class SingleLineStyle(val displayName: String) {
    DOUBLE_QUOTES("Double quotes: \"code\""),
    CJK_QUOTES("CJK quotes: “code”"),
    CODE_FENCE("Markdown code block"),
    PLAIN("Plain text")
}

@Service(Service.Level.APP)
@State(
    name = "InformativeCopySettings",
    storages = [Storage("informativeCopy.xml")]
)
class InformativeCopySettings : PersistentStateComponent<InformativeCopySettings.State> {

    data class State(
        var pathStyle: PathStyle = PathStyle.RELATIVE,
        var lineStyle: LineStyle = LineStyle.PARENTHESES,
        var singleLineStyle: SingleLineStyle = SingleLineStyle.DOUBLE_QUOTES,
        var dedentMultiLine: Boolean = true,
        var trimSingleLine: Boolean = true,
        var includeCommitDiffByDefault: Boolean = false,
        var maxDiffLines: Int = 250,
        var contextLinesWhenDetailed: Int = 5,
        var showNotification: Boolean = true
    )

    private var currentState = State()

    override fun getState(): State = currentState

    override fun loadState(state: State) {
        currentState = state
    }

    companion object {
        fun getInstance(): InformativeCopySettings {
            return ApplicationManager.getApplication().getService(InformativeCopySettings::class.java)
        }
    }
}
