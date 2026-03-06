package com.rin.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.rin.RinLib
import com.rin.ui.theme.TerminalColorScheme
import com.rin.ui.theme.rememberTerminalColorScheme

@Composable
fun TerminalSurface(
    engineHandle: Long,
    ctrlPressed: Boolean,
    cursorBlinkEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onInput: (ByteArray) -> Unit = {},
    onViewReady: (android.view.View) -> Unit = {}
) {
    var fontSize by remember { mutableFloatStateOf(8f) }
    var ctrlState by remember { mutableStateOf(ctrlPressed) }
    var cursorVisible by remember { mutableStateOf(true) }
    val colorScheme = rememberTerminalColorScheme()

    var lastInputTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var viewRef by remember { mutableStateOf<TerminalCanvasView?>(null) }

    DisposableEffect(ctrlPressed) {
        ctrlState = ctrlPressed
        onDispose { }
    }

    val currentBlinkEnabled by androidx.compose.runtime.rememberUpdatedState(cursorBlinkEnabled)

    androidx.compose.runtime.LaunchedEffect(lastInputTime) {
        cursorVisible = true
        viewRef?.invalidate()
        kotlinx.coroutines.delay(500)
        while (true) {
            cursorVisible = if (currentBlinkEnabled) !cursorVisible else true
            viewRef?.invalidate()
            kotlinx.coroutines.delay(500)
        }
    }

    androidx.compose.runtime.LaunchedEffect(cursorBlinkEnabled) {
        if (!cursorBlinkEnabled) {
            cursorVisible = true
            viewRef?.invalidate()
        }
    }

    val wrappedOnInput: (ByteArray) -> Unit = { data ->
        lastInputTime = System.currentTimeMillis()
        cursorVisible = true
        onInput(data)
    }

    val resetBlink: () -> Unit = {
        lastInputTime = System.currentTimeMillis()
        cursorVisible = true
    }

    AndroidView(
        factory = { context ->
            TerminalCanvasView(context).apply {
                this.engineHandle = engineHandle
                this.fontSize = fontSize
                this.onInputCallback = wrappedOnInput
                this.onActivityCallback = resetBlink
                this.ctrlPressedProvider = { ctrlState }
                this.cursorVisibleProvider = { cursorVisible }
                this.colorScheme = colorScheme
                viewRef = this
                onViewReady(this)
            }
        },
        modifier = modifier,
        update = { view ->
            viewRef = view
            view.engineHandle = engineHandle
            view.fontSize = fontSize
            view.onInputCallback = wrappedOnInput
            view.onActivityCallback = resetBlink
            view.ctrlPressedProvider = { ctrlState }
            view.cursorVisibleProvider = { cursorVisible }
            view.colorScheme = colorScheme
            view.invalidate()
        }
    )
}

class TerminalCanvasView(context: Context) : View(context), View.OnCreateContextMenuListener {
    var engineHandle: Long = 0L
    var fontSize: Float = 8f
        set(value) {
            field = value
            updatePaint()
        }
    var onInputCallback: (ByteArray) -> Unit = {}
    var onActivityCallback: () -> Unit = {}
    var ctrlPressedProvider: () -> Boolean = { false }
    var cursorVisibleProvider: () -> Boolean = { true }
    var colorScheme: TerminalColorScheme? = null

    private var cols = 80
    private var rows = 24
    
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // Text selection
    private var isSelecting = false
    private var selectionStartX = 0
    private var selectionStartY = 0
    private var selectionEndX = 0
    private var selectionEndY = 0
    
    private val selectionPaint = Paint().apply {
        color = Color.argb(100, 100, 150, 255)
        style = Paint.Style.FILL
    }
    
    companion object {
        private const val MENU_COPY = 1
        private const val MENU_PASTE = 2
        private const val MENU_SELECT_ALL = 3
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val cursorPaint = Paint().apply {
        color = Color.WHITE
        alpha = 255
    }
    private val bgPaint = Paint()
    private val fgPaint = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val charWidth: Float
        get() = textPaint.measureText("W")

    private val lineHeight: Float
        get() {
            val metrics = textPaint.fontMetrics
            return metrics.descent - metrics.ascent
        }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newSize = fontSize * detector.scaleFactor
            if (newSize in 4f..30f) {
                fontSize = newSize
                handleResize()
                invalidate()
            }
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnCreateContextMenuListener(this)
        updatePaint()

        postDelayed(object : Runnable {
            override fun run() {
                if (engineHandle != 0L && RinLib.hasDirtyRows(engineHandle)) {
                    invalidate()
                }
                postDelayed(this, 33)
            }
        }, 33)
    }

    private fun updatePaint() {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSize,
            resources.displayMetrics
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        handleResize()
    }

    private fun handleResize() {
        if (width > 0 && height > 0 && charWidth > 0 && lineHeight > 0) {
            cols = (width / charWidth).toInt().coerceAtLeast(1)
            rows = (height / lineHeight).toInt().coerceAtLeast(1)
            if (engineHandle != 0L) {
                RinLib.resize(engineHandle, cols, rows)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = (event.x / charWidth).toInt().coerceIn(0, cols - 1)
                val y = (event.y / lineHeight).toInt().coerceIn(0, rows - 1)
                
                if (isSelecting) {
                    isSelecting = false
                    invalidate()
                } else {
                    selectionStartX = x
                    selectionStartY = y
                    selectionEndX = x
                    selectionEndY = y
                }
                
                requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.eventTime - event.downTime > 300) {
                    isSelecting = true
                    val x = (event.x / charWidth).toInt().coerceIn(0, cols - 1)
                    val y = (event.y / lineHeight).toInt().coerceIn(0, rows - 1)
                    selectionEndX = x
                    selectionEndY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isSelecting) {
                    showContextMenu()
                    return true
                }
            }
        }
        return true
    }
    
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        menu.setHeaderTitle("Terminal")
        
        if (isSelecting) {
            menu.add(0, MENU_COPY, 0, "Copy").setOnMenuItemClickListener {
                copySelectedText()
                isSelecting = false
                invalidate()
                true
            }
        }
        
        menu.add(0, MENU_PASTE, 0, "Paste").setOnMenuItemClickListener {
            pasteFromClipboard()
            true
        }
        
        menu.add(0, MENU_SELECT_ALL, 0, "Select All").setOnMenuItemClickListener {
            selectAll()
            true
        }
    }
    
    private fun selectAll() {
        isSelecting = true
        selectionStartX = 0
        selectionStartY = 0
        selectionEndX = cols - 1
        selectionEndY = rows - 1
        invalidate()
        showContextMenu()
    }
    
    private fun copySelectedText() {
        if (engineHandle == 0L || !isSelecting) return
        
        val textBuilder = StringBuilder()
        val (startY, startX, endY, endX) = if (selectionStartY < selectionEndY || 
            (selectionStartY == selectionEndY && selectionStartX <= selectionEndX)) {
            listOf(selectionStartY, selectionStartX, selectionEndY, selectionEndX)
        } else {
            listOf(selectionEndY, selectionEndX, selectionStartY, selectionStartX)
        }
        
        for (y in startY..endY) {
            val line = RinLib.getLine(engineHandle, y)
            
            when {
                y == startY && y == endY -> {
                    val start = startX.coerceIn(0, line.length)
                    val end = (endX + 1).coerceIn(0, line.length)
                    textBuilder.append(line.substring(start, end))
                }
                y == startY -> {
                    val start = startX.coerceIn(0, line.length)
                    textBuilder.append(line.substring(start).trimEnd())
                    textBuilder.append("\n")
                }
                y == endY -> {
                    val end = (endX + 1).coerceIn(0, line.length)
                    textBuilder.append(line.substring(0, end))
                }
                else -> {
                    textBuilder.append(line.trimEnd())
                    textBuilder.append("\n")
                }
            }
        }
        
        val clip = ClipData.newPlainText("terminal", textBuilder.toString())
        clipboardManager.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun pasteFromClipboard() {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: return
            onInputCallback(text.toByteArray())
            invalidate()
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            private var composingText = StringBuilder()

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                val newText = text.toString()
                if (newText.length > composingText.length) {
                    val newChars = newText.substring(composingText.length)
                    sendToTerminal(newChars)
                }
                composingText.clear()
                composingText.append(newText)
                return true
            }

            override fun finishComposingText(): Boolean {
                composingText.clear()
                return true
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                val committed = text.toString()
                if (composingText.isNotEmpty()) {
                    composingText.clear()
                } else {
                    sendToTerminal(committed)
                }
                return true
            }

            private fun sendToTerminal(text: String) {
                if (text.isEmpty()) return
                val data = if (ctrlPressedProvider() && text.length == 1) {
                    val char = text[0].lowercaseChar()
                    if (char in 'a'..'z') {
                        byteArrayOf((char.code - 96).toByte())
                    } else {
                        text.toByteArray()
                    }
                } else {
                    text.toByteArray()
                }
                onInputCallback(data)
                invalidate()
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onActivityCallback()
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            onInputCallback("\r".toByteArray())
                            invalidate()
                            return true
                        }
                        KeyEvent.KEYCODE_DEL -> {
                            onInputCallback(byteArrayOf(0x7F))
                            invalidate()
                            return true
                        }
                    }
                    val unicodeChar = event.unicodeChar
                    if (unicodeChar != 0) {
                        sendToTerminal(unicodeChar.toChar().toString())
                        return true
                    }
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    onInputCallback(byteArrayOf(0x7F))
                }
                invalidate()
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        onActivityCallback()
        val data = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP    -> byteArrayOf(0x1B, 0x5B, 0x41)
            KeyEvent.KEYCODE_DPAD_DOWN  -> byteArrayOf(0x1B, 0x5B, 0x42)
            KeyEvent.KEYCODE_DPAD_RIGHT -> byteArrayOf(0x1B, 0x5B, 0x43)
            KeyEvent.KEYCODE_DPAD_LEFT  -> byteArrayOf(0x1B, 0x5B, 0x44)
            KeyEvent.KEYCODE_TAB        -> "\t".toByteArray()
            else -> null
        }

        if (data != null) {
            onInputCallback(data)
            invalidate()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun rgbToAnsiIndex(r: Int, g: Int, b: Int): Int? {
        return when {
            r == 0 && g == 0 && b == 0 -> 0
            r == 205 && g == 49 && b == 49 -> 1
            r == 13 && g == 188 && b == 121 -> 2
            r == 229 && g == 229 && b == 16 -> 3
            r == 36 && g == 114 && b == 200 -> 4
            r == 188 && g == 63 && b == 188 -> 5
            r == 17 && g == 168 && b == 205 -> 6
            r == 229 && g == 229 && b == 229 -> 7
            r == 102 && g == 102 && b == 102 -> 8
            r == 241 && g == 76 && b == 76 -> 9
            r == 35 && g == 209 && b == 139 -> 10
            r == 245 && g == 245 && b == 67 -> 11
            r == 59 && g == 142 && b == 234 -> 12
            r == 214 && g == 112 && b == 214 -> 13
            r == 41 && g == 184 && b == 219 -> 14
            r == 255 && g == 255 && b == 255 -> 15
            else -> null
        }
    }

    private fun getMonetColor(index: Int): Int {
        val scheme = colorScheme ?: return Color.WHITE
        return when (index) {
            0 -> scheme.black
            1 -> scheme.red
            2 -> scheme.green
            3 -> scheme.yellow
            4 -> scheme.blue
            5 -> scheme.magenta
            6 -> scheme.cyan
            7 -> scheme.white
            8 -> scheme.brightBlack
            9 -> scheme.brightRed
            10 -> scheme.brightGreen
            11 -> scheme.brightYellow
            12 -> scheme.brightBlue
            13 -> scheme.brightMagenta
            14 -> scheme.brightCyan
            15 -> scheme.brightWhite
            else -> scheme.foreground
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scheme = colorScheme
        canvas.drawColor(scheme?.background ?: 0xFF0D0D0D.toInt())

        if (engineHandle == 0L) return

        fgPaint.textSize = textPaint.textSize

        for (y in 0 until rows) {
            val cellData = RinLib.getCellData(engineHandle, y)
            if (cellData.isEmpty()) continue

            val cells = cellData.split("\n").filter { it.isNotEmpty() }
            var xPos = 0

            for (cellStr in cells) {
                val parts = cellStr.split("\t")
                if (parts.size < 4) {
                    xPos++
                    continue
                }

                val char = parts[0]
                val fgParts = parts[1].split(",")
                val bgParts = parts[2].split(",")
                val flags = parts[3]

                val fgR = fgParts.getOrNull(0)?.toIntOrNull() ?: 255
                val fgG = fgParts.getOrNull(1)?.toIntOrNull() ?: 255
                val fgB = fgParts.getOrNull(2)?.toIntOrNull() ?: 255
                val bgR = bgParts.getOrNull(0)?.toIntOrNull() ?: 0
                val bgG = bgParts.getOrNull(1)?.toIntOrNull() ?: 0
                val bgB = bgParts.getOrNull(2)?.toIntOrNull() ?: 0

                val fgColor = rgbToAnsiIndex(fgR, fgG, fgB)?.let { getMonetColor(it) }
                    ?: Color.rgb(fgR, fgG, fgB)
                val bgColor = rgbToAnsiIndex(bgR, bgG, bgB)?.let { getMonetColor(it) }
                    ?: Color.rgb(bgR, bgG, bgB)

                val schemeBg = scheme?.background ?: 0xFF0D0D0D.toInt()

                if (isSelecting && isPositionInSelection(xPos, y)) {
                    canvas.drawRect(
                        xPos * charWidth,
                        y * lineHeight,
                        (xPos + 1) * charWidth,
                        (y + 1) * lineHeight,
                        selectionPaint
                    )
                } else if (bgColor != schemeBg && bgColor != Color.BLACK) {
                    bgPaint.color = bgColor
                    canvas.drawRect(
                        xPos * charWidth,
                        y * lineHeight,
                        (xPos + 1) * charWidth,
                        (y + 1) * lineHeight,
                        bgPaint
                    )
                }

                fgPaint.color = fgColor
                fgPaint.isFakeBoldText = flags.contains('b')
                fgPaint.textSkewX = if (flags.contains('i')) -0.25f else 0f
                fgPaint.alpha = if (flags.contains('d')) 150 else 255

                if (char.isNotEmpty() && char != " ") {
                    canvas.drawText(
                        char,
                        xPos * charWidth,
                        (y + 1) * lineHeight - fgPaint.descent(),
                        fgPaint
                    )
                }
                xPos += if (flags.contains('w')) 2 else 1
            }
        }

        if (cursorVisibleProvider() && !isSelecting) {
            val cx = RinLib.getCursorX(engineHandle)
            val cy = RinLib.getCursorY(engineHandle)
            if (cx < cols && cy < rows) {
                cursorPaint.color = scheme?.cursor ?: Color.WHITE
                cursorPaint.alpha = 255
                val cursorHeight = lineHeight * 0.15f
                val baseY = (cy + 1) * lineHeight
                canvas.drawRect(
                    cx * charWidth,
                    baseY - cursorHeight,
                    cx * charWidth + charWidth,
                    baseY,
                    cursorPaint
                )
            }
        }

        if (engineHandle != 0L) {
            RinLib.clearDirty(engineHandle)
        }
    }
    
    private fun isPositionInSelection(x: Int, y: Int): Boolean {
        val (startY, startX, endY, endX) = if (selectionStartY < selectionEndY || 
            (selectionStartY == selectionEndY && selectionStartX <= selectionEndX)) {
            listOf(selectionStartY, selectionStartX, selectionEndY, selectionEndX)
        } else {
            listOf(selectionEndY, selectionEndX, selectionStartY, selectionStartX)
        }
        
        return when {
            y < startY || y > endY -> false
            y == startY && y == endY -> x in startX..endX
            y == startY -> x >= startX
            y == endY -> x <= endX
            else -> true
        }
    }
}