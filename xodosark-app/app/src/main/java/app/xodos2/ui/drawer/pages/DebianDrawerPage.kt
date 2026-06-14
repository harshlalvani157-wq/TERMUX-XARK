package app.xodos2.ui.drawer.pages

import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.xodos2.TerminalSessionIds
import app.xodos2.ui.drawer.menu.DrawerExpandableSection
import app.xodos2.ui.drawer.menu.DrawerScriptEditor
import app.xodos2.ui.prefs.AppPrefs
import app.xodos2.ui.runtime.TerminalSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import app.xodos2.ui.runtime.NativeInstallCoordinator

@Composable
fun DebianDrawerPage(

    prefs: SharedPreferences,
    drawerState: DrawerState,
    scope: CoroutineScope,
    terminalSessionState: TerminalSessionController.State,
    onTerminalSessionStateChange: (TerminalSessionController.State) -> Unit,
    x11ScriptEditorOpen: Boolean,
    onX11ScriptEditorOpenChange: (Boolean) -> Unit,
    onEnterDebianDesktop: () -> Unit,
    onEnterTerminal: () -> Unit,
    onExitDisplayModes: () -> Unit,
    hasDebianRootfs: Boolean = true,
    onContainerManagerClick: () -> Unit,
    onOpenX11Settings: () -> Unit = {}          // NEW parameter
) {
    val context = LocalContext.current

    if (!hasDebianRootfs) {
        // Not installed – just a hint and Container Manager
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Distro not installed.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Container Manager",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { drawerState.close() }
                        onContainerManagerClick()
                    }
                    .padding(vertical = 12.dp)
            )
        }
        return
    }

    // Installed – title, X11 button, Terminal button, X11 Settings button, Container Manager
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
           text = NativeInstallCoordinator.getContainerDisplayName(context, 2),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))

        // X11 Desktop button (tap to launch, long‑press to edit script)
        Text(
            text = "X11",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onX11ScriptEditorOpenChange(true) },
                        onTap = {
                            scope.launch {
                                drawerState.close()
                                onEnterDebianDesktop()
                            }
                        }
                    )
                }
                .padding(vertical = 12.dp)
        )

        // Script editor (expandable, shown when long‑pressed)
        DrawerExpandableSection(title = "Scripts", defaultExpanded = false) {
            if (x11ScriptEditorOpen) {
                DrawerScriptEditor(
                    title = "X11 startup script",
                    initialText = AppPrefs.readDebianDesktopStartupScript(prefs),
                    onSave = {
                        AppPrefs.writeDebianDesktopStartupScript(prefs, it)
                        onX11ScriptEditorOpenChange(false)
                    }
                )
            } else {
                Text(
                    text = "Edit X11 startup script",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onX11ScriptEditorOpenChange(true) }
                        .padding(vertical = 12.dp, horizontal = 12.dp)
                )
            }
        }

        // Terminal button
        Text(
            text = "Terminal",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch { drawerState.close() }
                    onTerminalSessionStateChange(
                        terminalSessionState.copy(activeSessionId = TerminalSessionIds.DEBIAN_TERMINAL)
                    )
                    onExitDisplayModes()
                    onEnterTerminal()
                }
                .padding(vertical = 12.dp)
        )

        // ── X11 Settings button ────────────────────────────────
        Text(
            text = "X11 Settings",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch { drawerState.close() }
                    onOpenX11Settings()
                }
                .padding(vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Container Manager always at the bottom
        Text(
            text = "Container Manager",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch { drawerState.close() }
                    onContainerManagerClick()
                }
                .padding(vertical = 12.dp)
        )
    }
}