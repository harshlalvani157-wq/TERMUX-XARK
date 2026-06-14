package app.xodos2.ui.shell

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.xodos2.NativeBridge
import app.xodos2.PtyOutputRelay
import app.xodos2.R
import app.xodos2.RustPtySession
import app.xodos2.shell.ShellFonts
import app.xodos2.shell.ShellSessionClient
import app.xodos2.shell.ShellViewClient
import app.xodos2.wayland.input.InputRouteState
import com.termux.view.TerminalView

@Composable
fun ShellScreen(
    terminalFontKey: String,
    activeSessionId: Int,
    terminalSessionIds: List<Int>,
    rendererSessionResetEpoch: Int,
    showKeyboardTrigger: Int,
    onKeyboardTriggerConsumed: () -> Unit = {},
    onCloseCurrentSession: () -> Unit = {},
    onExitRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember { context as Activity }
    val imm = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    val sharedPrefs = remember {
        context.getSharedPreferences("xodos2_terminal_prefs", Context.MODE_PRIVATE)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showCloseSessionDialog by remember { mutableStateOf(false) }

    // Close session confirmation dialog
    if (showCloseSessionDialog) {
        AlertDialog(
            onDismissRequest = { showCloseSessionDialog = false },
            title = { Text("Close terminal") },
            text = { Text("Are you sure you want to close this terminal session?") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseSessionDialog = false
                    if (terminalSessionIds.size <= 1) {
                        onExitRequested()
                    } else {
                        onCloseCurrentSession()
                    }
                }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseSessionDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---------- Default text size and state ----------
    val defaultTextSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        24f,
        context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)

    var currentTextSize by remember { mutableIntStateOf(defaultTextSizePx) }

    // ---------- Debounced zoom save ----------
    val saveHandler = remember { Handler(Looper.getMainLooper()) }
    val saveRunnable = remember {
        Runnable {
            sharedPrefs.edit().putInt("terminal_zoom_size", currentTextSize).commit()
        }
    }

    fun scheduleZoomSave() {
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 500L)
    }

    fun saveZoomImmediately() {
        saveHandler.removeCallbacks(saveRunnable)
        sharedPrefs.edit().putInt("terminal_zoom_size", currentTextSize).commit()
    }

    // Save on pause / stop / dispose
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                saveZoomImmediately()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveZoomImmediately()
            PtyOutputRelay.unbind()
            InputRouteState.shellTerminalView = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        factory = { ctx ->
            // Root: vertical LinearLayout
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = false
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // TerminalView setup
            val tv = TerminalView(ctx, null)
            val controller = ShellSessionController(ctx, tv)
            root.setTag(R.id.xodos2_shell_controller, controller)

            val viewClient = ShellViewClient(tv)

            // ---------- Wrapped client to capture zoom changes ----------
            val wrappedClient = object : com.termux.view.TerminalViewClient by viewClient {
                override fun onScale(scale: Float): Float {
                    val newScale = viewClient.onScale(scale)
                    // Read the actual new text size from the public getter
                    currentTextSize = tv.mRenderer.getTextSizePx()
                    scheduleZoomSave()
                    return newScale
                }
            }
            tv.setTerminalViewClient(wrappedClient)

            // Load and apply saved zoom
            val savedTextSize = sharedPrefs.getInt("terminal_zoom_size", defaultTextSizePx)
            tv.setTextSize(savedTextSize)
            currentTextSize = savedTextSize

            tv.setTypeface(ShellFonts.typefaceForPref(ctx, terminalFontKey))
            InputRouteState.shellTerminalView = tv
            tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                tv.post { tv.updateSize() }
            }
            tv.post { tv.updateSize() }
            tv.isFocusable = true
            tv.isFocusableInTouchMode = true
            tv.keepScreenOn = true
            tv.setBackgroundColor(android.graphics.Color.BLACK)

            // Wrap terminal in a FrameLayout for the close button overlay
            val terminalFrame = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 0
                }
            }

            terminalFrame.addView(
                tv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            // Close button
            val closeButton = TextView(ctx).apply {
                text = "✕"
                setTextColor(Color.WHITE)
                textSize = 16f
                setBackgroundColor(Color.argb(150, 0, 0, 0))
                setPadding(12.dpToPx(ctx), 8.dpToPx(ctx), 12.dpToPx(ctx), 8.dpToPx(ctx))
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
            }

            val closeBtnParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, 6.dpToPx(ctx), 6.dpToPx(ctx), 0)
            }
            terminalFrame.addView(closeButton, closeBtnParams)

            closeButton.setOnClickListener {
                Handler(Looper.getMainLooper()).post {
                    showCloseSessionDialog = true
                }
            }

            root.addView(terminalFrame)

            // Extra keys bar
            val keysScroll = HorizontalScrollView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
            }
            val keysContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4.dpToPx(ctx), 4.dpToPx(ctx), 4.dpToPx(ctx), 4.dpToPx(ctx))
            }

            setupExtraKeys(ctx, tv, viewClient, keysContainer)

            keysScroll.addView(keysContainer)
            root.addView(
                keysScroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            root
        },
        update = { root ->
            val controller =
                root.getTag(R.id.xodos2_shell_controller) as ShellSessionController
            val prevEpoch = root.getTag(R.id.xodos2_renderer_session_epoch) as? Int ?: 0
            if (rendererSessionResetEpoch != prevEpoch) {
                root.setTag(R.id.xodos2_renderer_session_epoch, rendererSessionResetEpoch)
                controller.invalidateAllSessions()
            }
            controller.pruneSessionsExcept(terminalSessionIds.toSet())
            controller.attachSessionIfNeeded(activeSessionId)

            // Navigate into the FrameLayout to find the TerminalView
            val terminalFrame = root.getChildAt(0) as FrameLayout
            val tv = terminalFrame.getChildAt(0) as TerminalView

            val applied = root.getTag(R.id.xodos2_terminal_font_applied) as? String
            if (applied != terminalFontKey) {
                root.setTag(R.id.xodos2_terminal_font_applied, terminalFontKey)
                tv.setTypeface(ShellFonts.typefaceForPref(tv.context, terminalFontKey))
            }
            if (showKeyboardTrigger > 0) {
                tv.post {
                    tv.requestFocus()
                    imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
                }
                onKeyboardTriggerConsumed()
            }
            tv.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (InputRouteState.lorieX11DisplayVisible) return@setOnKeyListener false
                    onExitRequested()
                    true
                } else {
                    false
                }
            }
        }
    )
}

// ---------- Touch Repeating Listener ----------
private fun setRepeatClickListener(view: TextView, onClick: () -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var isHolding = false
    val initialDelay = 400L
    val repeatInterval = 50L

    val runnable = object : Runnable {
        override fun run() {
            if (isHolding) {
                onClick()
                handler.postDelayed(this, repeatInterval)
            }
        }
    }

    view.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isHolding = true
                v.isPressed = true
                onClick()
                handler.postDelayed(runnable, initialDelay)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHolding = false
                v.isPressed = false
                handler.removeCallbacks(runnable)
                true
            }
            else -> false
        }
    }
}

// ---------- Extra keys builder ----------
private fun setupExtraKeys(
    context: Context,
    terminalView: TerminalView,
    viewClient: ShellViewClient,
    parent: LinearLayout
) {
    var ctrlButton: TextView? = null
    var altButton: TextView? = null

    val bgColor = Color.parseColor("#1A1A2E")
    val textColor = Color.parseColor("#BB86FC")
    val strokeColor = Color.parseColor("#BB86FC")
    val activeTextColor = Color.parseColor("#FFD700")

    fun updateButtonColors() {
        ctrlButton?.setTextColor(if (viewClient.ctrlActive) activeTextColor else textColor)
        altButton?.setTextColor(if (viewClient.altActive) activeTextColor else textColor)
    }

    viewClient.onModifierReset = {
        terminalView.post { updateButtonColors() }
    }

    fun makeButton(label: String): TextView {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            setStroke(1.dpToPx(context), strokeColor)
            cornerRadius = 5f * context.resources.displayMetrics.density
        }
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            background = drawable
            textSize = 12f
            includeFontPadding = false
            isAllCaps = false
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(
                    2.dpToPx(context),
                    2.dpToPx(context),
                    2.dpToPx(context),
                    2.dpToPx(context)
                )
            }
            setPadding(
                0,
                6.dpToPx(context),
                0,
                6.dpToPx(context)
            )
            gravity = Gravity.CENTER
        }
    }

    fun addRow(keys: List<Pair<String, String>>): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        for ((label, action) in keys) {
            val btn = makeButton(label)
            when (action) {
                "CTRL_MOD" -> {
                    ctrlButton = btn
                    btn.setOnClickListener {
                        viewClient.ctrlActive = !viewClient.ctrlActive
                        if (viewClient.ctrlActive) viewClient.altActive = false
                        updateButtonColors()
                    }
                }
                "ALT_MOD" -> {
                    altButton = btn
                    btn.setOnClickListener {
                        viewClient.altActive = !viewClient.altActive
                        if (viewClient.altActive) viewClient.ctrlActive = false
                        updateButtonColors()
                    }
                }
                else -> {
                    val fireAction = {
                        sendModifiedSequence(
                            terminalView, action, viewClient.ctrlActive, viewClient.altActive,
                            onConsumed = {
                                viewClient.ctrlActive = false
                                viewClient.altActive = false
                                updateButtonColors()
                            }
                        )
                    }

                    if (label in listOf("↑", "↓", "←", "→")) {
                        setRepeatClickListener(btn, fireAction)
                    } else {
                        btn.setOnClickListener { fireAction() }
                    }
                }
            }
            row.addView(btn)
        }
        return row
    }

    parent.addView(
        addRow(
            listOf(
                "CTRL" to "CTRL_MOD",
                "ALT" to "ALT_MOD",
                "HOME" to "\u001B[H",
                "END" to "\u001B[F"
            )
        )
    )

    parent.addView(
        addRow(
            listOf(
                "ESC" to "\u001B",
                "TAB" to "\u0009",
                "↑" to "\u001B[A",
                "↓" to "\u001B[B",
                "←" to "\u001B[D",
                "→" to "\u001B[C"
            )
        )
    )
}

private fun sendModifiedSequence(
    terminalView: TerminalView,
    baseSequence: String,
    ctrlActive: Boolean,
    altActive: Boolean,
    onConsumed: () -> Unit
) {
    val session = terminalView.currentSession ?: return
    val text = buildString {
        if (altActive) append('\u001B')
        if (ctrlActive && baseSequence.length == 1) {
            val ch = baseSequence[0]
            append((ch.code and 0x1F).toChar())
        } else {
            append(baseSequence)
        }
    }
    session.write(text)
    onConsumed()
}

private fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

// ---------- ShellSessionController ----------
private class ShellSessionController(
    private val context: Context,
    private val terminalView: TerminalView
) {
    private val clients = mutableMapOf<Int, ShellSessionClient>()
    private val sessions = mutableMapOf<Int, RustPtySession>()
    private var attachedId: Int = -1

    fun sessionFor(id: Int): RustPtySession {
        return sessions.getOrPut(id) {
            val client = clients.getOrPut(id) { ShellSessionClient(context, terminalView, id) }
            RustPtySession(context, client, terminalView, id)
        }
    }

    fun attachSessionIfNeeded(id: Int) {
        if (id == attachedId) return
        attachedId = id
        val s = sessionFor(id)
        terminalView.attachSession(s)
        PtyOutputRelay.bind(s, terminalView)
        terminalView.post { terminalView.updateSize() }
    }

    fun pruneSessionsExcept(keep: Set<Int>) {
        val removed = sessions.keys.filter { it !in keep }
        for (id in removed) {
            NativeBridge.closeSession(id)
            sessions.remove(id)
            clients.remove(id)
            PtyOutputRelay.discardSessionQueue(id)
        }
        if (attachedId !in keep) {
            attachedId = -1
        }
    }

    fun invalidateAllSessions() {
        for (id in sessions.keys.toList()) {
            NativeBridge.closeSession(id)
            sessions.remove(id)
            clients.remove(id)
            PtyOutputRelay.discardSessionQueue(id)
        }
        attachedId = -1
    }
}