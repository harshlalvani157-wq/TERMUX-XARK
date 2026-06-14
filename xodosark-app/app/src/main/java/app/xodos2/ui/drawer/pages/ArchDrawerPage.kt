package app.xodos2.ui.drawer.pages

import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.xodos2.TerminalSessionIds
import app.xodos2.shell.ShellFonts
import app.xodos2.ui.dialog.MOUSE_MODE_TABLET
import app.xodos2.ui.drawer.menu.DrawerExpandableSection
import app.xodos2.ui.drawer.menu.DrawerMenu
import app.xodos2.ui.drawer.menu.DrawerMenuActions
import app.xodos2.ui.drawer.menu.DrawerMenuLabels
import app.xodos2.ui.drawer.menu.DrawerMenuOptions
import app.xodos2.ui.drawer.menu.DrawerScriptEditor
import app.xodos2.ui.prefs.AppPrefs
import app.xodos2.ui.runtime.TerminalSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import app.xodos2.ui.runtime.NativeInstallCoordinator

// ----------------------------------------------------------------
// Helper: build the correct installation script for any distro
// ----------------------------------------------------------------
private fun buildDesktopInstallScript(distro: String, packages: String): String {
    val managerCmd = when (distro) {
        "debian", "ubuntu", "kali", "trisquel" ->
            "apt update && apt install -y"
        "archlinux", "manjaro", "artix" ->
            "pacman -Syu --noconfirm"
        "fedora", "almalinux", "rocky" ->
            "dnf update -y && dnf install -y"
        "alpine" ->
            "apk update && apk add"
        "void" ->
            "xbps-install -Su && xbps-install -y"
        "opensuse" ->
            "zypper refresh && zypper install -y"
        else ->
            "apt update && apt install -y"   // fallback
    }
    return "$managerCmd $packages\n" +
           "export PULSE_SERVER=127.0.0.1\n" +
           "echo 'Installation completed!'\n"
}

// ----------------------------------------------------------------
// ArchDrawerPage composable
// ----------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchDrawerPage(
    archX11ScriptEditorOpen: Boolean,
    onArchX11ScriptEditorOpenChange: (Boolean) -> Unit,
    onEnterArchX11Desktop: () -> Unit,
    prefs: SharedPreferences,
    drawerState: DrawerState,
    scope: CoroutineScope,
    terminalFontKey: String,
    terminalSessionState: TerminalSessionController.State,
    launcherDefault: String,
    desktopVulkanMode: String,
    desktopOpenGLMode: String,
    mouseMode: Int,
    resolutionPercent: Int,
    scalePercent: Int,
    waylandScriptEditorOpen: Boolean,
    onWaylandScriptEditorOpenChange: (Boolean) -> Unit,
    onEnterWaylandDesktop: () -> Unit,
    onEnterTerminal: () -> Unit,
    onLauncherDefaultSelect: (String) -> Unit,
    onDesktopVulkanSelect: (String) -> Unit,
    onDesktopOpenGLSelect: (String) -> Unit,
    onTerminalFontSelectLabel: (String) -> Unit,
    onTerminalSessionStateChange: (TerminalSessionController.State) -> Unit,
    onMouseModeSelectLabel: (String) -> Unit,
    onResolutionPercentSelectLabel: (String) -> Unit,
    onScalePercentSelectLabel: (String) -> Unit,
    vulkanOptions: List<String>,
    openGLOptions: List<String>,
    hasArchRootfs: Boolean = true,
    onContainerManagerClick: () -> Unit,
    onRequestKeyboard: () -> Unit = {},
    onExecuteCommand: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val containerDisplayName = NativeInstallCoordinator.getContainerDisplayName(context, 1)

    // Current container distro type
    val distroId = NativeInstallCoordinator.getContainerDistro(context, 1) ?: "linux"

    // ===================== Commands state =====================
    var showCommandsDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    val savedCommands = remember { mutableStateListOf<String>() }

    LaunchedEffect(showCommandsDialog) {
        if (showCommandsDialog) {
            savedCommands.clear()
            savedCommands.addAll(AppPrefs.loadCommands(prefs))
        }
    }

    fun persistCommands() {
        AppPrefs.saveCommands(prefs, savedCommands.toList())
    }

    // ===================== Desktop environment list =====================
    // Each entry contains only the package names – the correct manager is prepended automatically.
    data class DesktopEnv(val name: String, val packages: String)

val desktopEnvs = remember(distroId) {
    listOf(
        DesktopEnv("XFCE Desktop", "xfce4 xfce4-goodies pulseaudio pavucontrol mesa-utils xorg-server-xwayland libvulkan-dev mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 vulkan-tools"),
        DesktopEnv("LXQt Desktop", "lxqt oxygen-icons pulseaudio pavucontrol mesa-utils xorg-server-xwayland libvulkan-dev mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 vulkan-tools"),
        DesktopEnv("KDE Plasma", "plasma-meta kde-applications-meta pulseaudio pavucontrol mesa-utils xorg-server-xwayland libvulkan-dev mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 vulkan-tools"),
        DesktopEnv("GNOME", "gnome gnome-extra pulseaudio pavucontrol mesa-utils xorg-server-xwayland libvulkan-dev mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 vulkan-tools"),
        DesktopEnv("MATE", "mate mate-extra pulseaudio pavucontrol mesa-utils xorg-server-xwayland libvulkan-dev mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 vulkan-tools"),
        DesktopEnv("Cinnamon", "cinnamon nemo-fileroller pulseaudio pavucontrol mesa-utils xorg-server-xwayland libvulkan-dev mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 vulkan-tools")
    )
}

    // ===================== Existing UI =====================
    if (!hasArchRootfs) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Linux distro not installed.",
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

    val terminalFontLabel = remember(terminalFontKey) {
        ShellFonts.options.find { it.id == terminalFontKey }?.label
            ?: ShellFonts.options.firstOrNull()?.label
            ?: terminalFontKey
    }
    val terminalSessionLabel = remember(terminalSessionState.activeSessionId) {
        TerminalSessionIds.sessionPickerLine(terminalSessionState.activeSessionId, context)
    }
    val mouseModeLabel = remember(mouseMode) {
        if (mouseMode == MOUSE_MODE_TABLET) "Tablet" else "Touchpad"
    }
    val resolutionLabel = remember(resolutionPercent) { "${resolutionPercent.coerceIn(10, 100)}%" }
    val scaleLabel = remember(scalePercent) { "${scalePercent.coerceIn(100, 1000)}%" }
    val launcherMenuLabel = remember(launcherDefault) { AppPrefs.launcherPrefToMenuLabel(launcherDefault) }

    DrawerMenu(
        title = containerDisplayName,
        labels = DrawerMenuLabels(
            launcherDefaultLabel = launcherMenuLabel,
            desktopVulkanLabel = desktopVulkanMode,
            desktopOpenGLLabel = desktopOpenGLMode,
            terminalFontLabel = terminalFontLabel,
            terminalSessionLabel = terminalSessionLabel,
            mouseModeLabel = mouseModeLabel,
            resolutionPercentLabel = resolutionLabel,
            scalePercentLabel = scaleLabel,
        ),
        options = DrawerMenuOptions(
            launcherDefaultOptions = listOf(
                AppPrefs.LAUNCHER_MENU_WAYLAND,
                AppPrefs.LAUNCHER_MENU_TERMINAL,
                AppPrefs.LAUNCHER_MENU_X11,
            ),
            desktopVulkanOptions = vulkanOptions,
            desktopOpenGLOptions = openGLOptions,
            terminalFontOptions = ShellFonts.options.map { it.label },
            terminalSessionOptions = buildList {
                add(TerminalSessionIds.sessionPickerLine(TerminalSessionIds.FIRST_TERMINAL, context))
                terminalSessionState.sessionIds
                    .sorted()
                    .filter { it != TerminalSessionIds.FIRST_TERMINAL }
                    .forEach { add(TerminalSessionIds.sessionPickerLine(it, context)) }
                add("New session")
                add("Close current session")
            },
            mouseModeOptions = listOf("Touchpad", "Tablet"),
            resolutionPercentOptions = (10..100 step 10).map { "${it}%" },
            scalePercentOptions = (100..1000 step 100).map { "${it}%" },
        ),
        actions = DrawerMenuActions(
            onDesktopClick = {
                scope.launch { drawerState.close() }
                onEnterWaylandDesktop()
            },
            onDesktopLongPress = {
                onWaylandScriptEditorOpenChange(true)
            },
            onDebianDesktopClick = {
                scope.launch { drawerState.close() }
                onEnterArchX11Desktop()
            },
            onDebianDesktopLongPress = {
                onArchX11ScriptEditorOpenChange(true)
            },
            onTerminalClick = {
                scope.launch { drawerState.close() }
                onTerminalSessionStateChange(terminalSessionState.copy(activeSessionId = TerminalSessionIds.ARCH_TERMINAL))
                onEnterTerminal()
            },
            onViewClick = { scope.launch { drawerState.close() } },
            onAppearanceClick = { scope.launch { drawerState.close() } },
            onSessionClick = { scope.launch { drawerState.close() } },
            onKeyboardClick = {
    onRequestKeyboard()
    scope.launch { drawerState.close() }
},
            onLauncherDefaultSelect = onLauncherDefaultSelect,
            onDesktopVulkanSelect = onDesktopVulkanSelect,
            onDesktopOpenGLSelect = onDesktopOpenGLSelect,
            onTerminalFontSelect = onTerminalFontSelectLabel,
            onTerminalSessionSelect = { label ->
                val next = when (label) {
                    "New session" ->
                        TerminalSessionController.addNewInteractiveSession(terminalSessionState, TerminalSessionIds.NS_ARCH)
                    "Close current session" ->
                        TerminalSessionController.closeCurrentSession(terminalSessionState)
                    else ->
                        TerminalSessionController.selectFromPickerLine(terminalSessionState, label)
                }
                onTerminalSessionStateChange(next)
            },
            onMouseModeSelect = onMouseModeSelectLabel,
            onResolutionPercentSelect = onResolutionPercentSelectLabel,
            onScalePercentSelect = onScalePercentSelectLabel,
            onCloseDrawerRequest = { scope.launch { drawerState.close() } },
        ),
        showDebianDesktop = true,
        extraContent = {
            // Existing scripts section
            ArchExtraContent(
                prefs = prefs,
                waylandScriptEditorOpen = waylandScriptEditorOpen,
                onWaylandScriptEditorOpenChange = onWaylandScriptEditorOpenChange,
                hasArchRootfs = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            DrawerExpandableSection(title = "X11 Scripts", defaultExpanded = false) {
                if (archX11ScriptEditorOpen) {
                    DrawerScriptEditor(
                        title = "X11 startup script",
                        initialText = AppPrefs.readArchX11DesktopStartupScript(prefs),
                        onSave = {
                            AppPrefs.writeArchX11DesktopStartupScript(prefs, it)
                            onArchX11ScriptEditorOpenChange(false)
                        },
                    )
                } else {
                    Text(
                        text = "Edit Arch X11 startup script",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArchX11ScriptEditorOpenChange(true) }
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                    )
                }
            }

            // ===================== Install Desktop section (SMART) =====================
            DrawerExpandableSection(title = "Install Desktop", defaultExpanded = false) {
                desktopEnvs.forEach { de ->
                    Text(
                        text = de.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                val script = buildDesktopInstallScript(distroId, de.packages)
                                onExecuteCommand(script)
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    )
                }
            }

            // ===================== Commands button =====================
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Commands",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCommandsDialog = true }
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            )

            // Container Manager button
            Spacer(modifier = Modifier.height(8.dp))
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
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            )
        }
    )

    // ===================== Commands dialogs (unchanged) =====================
    if (showCommandsDialog) {
        AlertDialog(
            onDismissRequest = { showCommandsDialog = false },
            title = { Text("Saved Commands") },
            text = {
                Column {
                    TextButton(onClick = {
                        editingIndex = null
                        editText = ""
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add command")
                    }
                    Spacer(Modifier.height(8.dp))

                    if (savedCommands.isEmpty()) {
                        Text(
                            "No saved commands.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn {
                            items(savedCommands.size) { index ->
                                val cmd = savedCommands[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                onExecuteCommand(cmd)
                                                showCommandsDialog = false
                                            },
                                            onLongClick = {
                                                showDeleteConfirm = index
                                            }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        cmd,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    IconButton(onClick = {
                                        editingIndex = index
                                        editText = cmd
                                        showAddEditDialog = true
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommandsDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAddEditDialog) {
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (editingIndex == null) "Add command" else "Edit command") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Shell command") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cmd = editText.trim()
                    if (cmd.isNotEmpty()) {
                        if (editingIndex != null) {
                            savedCommands[editingIndex!!] = cmd
                        } else {
                            savedCommands.add(cmd)
                        }
                        persistCommands()
                    }
                    showAddEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm != null) {
        val idx = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete command?") },
            text = { Text("Remove \"${savedCommands[idx]}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    savedCommands.removeAt(idx)
                    persistCommands()
                    showDeleteConfirm = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ArchExtraContent(
    prefs: SharedPreferences,
    waylandScriptEditorOpen: Boolean,
    onWaylandScriptEditorOpenChange: (Boolean) -> Unit,
    hasArchRootfs: Boolean
) {
    DrawerExpandableSection(title = "Scripts", defaultExpanded = false) {
        if (waylandScriptEditorOpen) {
            DrawerScriptEditor(
                title = "Wayland desktop startup script",
                initialText = prefs.getString("desktop_startup_script", "") ?: "",
                onSave = {
                    prefs.edit().putString("desktop_startup_script", it).apply()
                    onWaylandScriptEditorOpenChange(false)
                },
            )
        } else {
            Text(
                text = "Edit Wayland startup script",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onWaylandScriptEditorOpenChange(true) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
            )
        }
    }
}