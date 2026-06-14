package app.xodos2.ui.drawer.pages

import android.content.SharedPreferences
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
fun WineDrawerPage(

    prefs: SharedPreferences,
    drawerState: DrawerState,
    scope: CoroutineScope,
    terminalSessionState: TerminalSessionController.State,
    onTerminalSessionStateChange: (TerminalSessionController.State) -> Unit,
    wineScriptEditorOpen: Boolean,
    onWineScriptEditorOpenChange: (Boolean) -> Unit,
    onEnterWineDesktop: () -> Unit,
    onEnterTerminal: () -> Unit,
    onExitDisplayModes: () -> Unit,
    hasWineRootfs: Boolean = true,
    onContainerManagerClick: () -> Unit
) {
val context = LocalContext.current
    if (!hasWineRootfs) {
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

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
           text = NativeInstallCoordinator.getContainerDisplayName(context, 3),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))

        // X11 Desktop button (tap to launch Wine desktop, long‑press to edit startup script)
        Text(
            text = "X11",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onWineScriptEditorOpenChange(true) },
                        onTap = {
                            scope.launch {
                                drawerState.close()
                                onEnterWineDesktop()
                            }
                        }
                    )
                }
                .padding(vertical = 12.dp)
        )

        // Scripts section for Wine startup script
        DrawerExpandableSection(title = "Scripts", defaultExpanded = false) {
            if (wineScriptEditorOpen) {
                DrawerScriptEditor(
                    title = "X11 startup script",
                    initialText = AppPrefs.readWineDesktopStartupScript(prefs), // create this helper if needed
                    onSave = {
                        AppPrefs.writeWineDesktopStartupScript(prefs, it)
                        onWineScriptEditorOpenChange(false)
                    }
                )
            } else {
                Text(
                    text = "Edit X11 startup script",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWineScriptEditorOpenChange(true) }
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
                        terminalSessionState.copy(activeSessionId = TerminalSessionIds.WINE_TERMINAL)
                    )
                    onExitDisplayModes()
                    onEnterTerminal()
                }
                .padding(vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

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