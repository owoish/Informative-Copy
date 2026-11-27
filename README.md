# Informative Copy

Context-aware high-information copy plugin for JetBrains IDEs (**Rider**, IntelliJ IDEA, CLion, PyCharm, WebStorm, etc.), built for fast AI Agent workflows.

## Features

One shortcut adapts automatically to whatever is currently focused in the IDE:

| Focus Context | `Informative Copy` (`Ctrl+Shift+C`) | `Informative Copy (With Diff / Context)` (`Ctrl+Alt+Shift+C`) |
| :--- | :--- | :--- |
| **Code Editor (Selection)** | Relative path + line/range + quoted line or fenced code block | Same (multi-line fenced code block) |
| **Code Editor (No Selection)** | Relative path + line + current line | Relative path + surrounding ±5 lines |
| **Diff Viewer** | File path + `[Diff: side]` + line/range + snippet | Same + surrounding context if no selection |
| **Git Log Commits** | Commit hash, date, author, full message, changed file list | Same + unified `diff` (auto-truncated) |
| **Git Log Changed Files Tree** | Commit metadata + specifically selected changed file(s) | Same + unified `diff` for those files |
| **Local Changes / Commit Tree** | Changed file status list (`M/A/D/R`) | Same + unified `diff` |
| **Problems / Build / Find / Debug Trees** | File path + line + message + source line preview | Same |
| **Console / Run Output** | `[Console]` + fenced selected output | Same |
| **Solution / Project Explorer** | Relative file/directory paths | Single text file also includes contents |

## Build & Install in Rider

1. Build the plugin zip:
   ```powershell
   .\gradlew.bat test buildPlugin
   ```
   Output zip: `build/distributions/informative-copy-0.1.0.zip`
2. Open **Rider** → **Settings (`Ctrl+Alt+S`)** → **Plugins** → ⚙️ Gear icon → **Install Plugin from Disk...** → select the `.zip` file → restart Rider.
3. Optional:
   - Customize format in **Settings → Tools → Informative Copy** (path style, line style, quote style `"..."` vs `“...”`, diff inclusion, max diff lines, notifications).
   - If `Ctrl+Shift+C` conflicts with Rider's built-in *Copy Paths*, open **Settings → Keymap**, search **Informative Copy**, and assign/override `Ctrl+Shift+C`.
