package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R

class AccessibilityOverlayShield(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var activeCountdownRunnable: Runnable? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        appName: String,
        feedLabel: String,
        useDarkTheme: Boolean = false,
        hapticsEnabled: Boolean = true,
        strictModeEnabled: Boolean = true,
        todayLoopsBroken: Int = 1,
        timeKeptText: String = "3m",
        streakDays: Int = 1,
        preferredPauseMinutes: Int = 15,
        primaryUsefulActionLabel: String = "Open DMs",
        secondaryUsefulActionLabel: String = "Go to Search",
        usefulPartsSummary: String = "DMs and Search are still open.",
        onExitFeed: () -> Unit,
        onAllowOnce: () -> Unit,
        onSnoozeMinutes: (Int) -> Unit = {},
        onAttachmentFailed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (overlayView != null) return@post

            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()
            fun dpF(value: Float): Float = value * density

            val headingTypeface = runCatching {
                ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
            }.getOrNull() ?: Typeface.DEFAULT_BOLD
            val bodyTypeface = runCatching {
                ResourcesCompat.getFont(context, R.font.inter)
            }.getOrNull() ?: Typeface.SANS_SERIF

            // Light Tonal Bento tokens matching Outloop-ui overlays
            val scrimColor = Color.parseColor("#D6141C16")
            val modalBg = Color.parseColor("#FAF8F2")
            val modalStroke = Color.parseColor("#141A221C")
            val darkHeroBg = Color.parseColor("#28352C")
            val darkPillBg = Color.parseColor("#3E4C42")
            val sageAccent = Color.parseColor("#A9C7A1")
            val heroTrack = Color.parseColor("#3C4B41")
            val mintTileBg = Color.parseColor("#D2E4CE")
            val peachTileBg = Color.parseColor("#F0D7CA")
            val inkPrimary = Color.parseColor("#1A221C")
            val inkSecondary = Color.parseColor("#565E57")
            val ivoryInk = Color.parseColor("#FAF8F2")
            val outlineStroke = Color.parseColor("#331A221C")

            val root = FrameLayout(context).apply {
                setBackgroundColor(scrimColor)
                setPadding(dp(18), dp(24), dp(18), dp(24))
                isFocusable = true
                isFocusableInTouchMode = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "Loop broken. Outloop paused $appName $feedLabel."
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    accessibilityPaneTitle = "Outloop Loop Guard"
                }
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        dismiss()
                        onExitFeed()
                        true
                    } else {
                        false
                    }
                }
            }

            val scrollContainer = ScrollView(context).apply {
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalScrollBarEnabled = false
            }

            val modalCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(16))
                background = GradientDrawable().apply {
                    setColor(modalBg)
                    cornerRadius = dpF(28f)
                    setStroke(dp(1), modalStroke)
                }
            }

            lateinit var buildPauseGuardFn: () -> View

            fun buildFeedBlockedContent(): View {
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // Dark forest header bento card
                val heroRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(18), dp(18), dp(18))
                    background = GradientDrawable().apply {
                        setColor(darkHeroBg)
                        cornerRadius = dpF(22f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val heroLeft = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val pillChip = TextView(context).apply {
                    text = "$appName \u00B7 $feedLabel"
                    textSize = 12f
                    typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
                    setTextColor(ivoryInk.let { Color.argb(230, Color.red(it), Color.green(it), Color.blue(it)) })
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                    background = GradientDrawable().apply {
                        setColor(darkPillBg)
                        cornerRadius = dpF(100f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val heroHeadline = TextView(context).apply {
                    text = "Loop\nbroken"
                    textSize = 27f
                    lineHeight = dp(31)
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(ivoryInk)
                    setPadding(0, dp(12), 0, 0)
                    isFocusable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }

                heroLeft.addView(pillChip)
                heroLeft.addView(heroHeadline)

                val markView = object : View(context) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        color = sageAccent
                    }

                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val strokeW = dpF(10f)
                        paint.strokeWidth = strokeW
                        val inset = strokeW / 2f + dp(4)
                        val rect = RectF(inset, inset, width - inset, height - inset)
                        canvas.drawArc(rect, -27f, -324f, false, paint)
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(74), dp(74)).apply {
                        marginStart = dp(10)
                    }
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

                heroRow.addView(heroLeft)
                heroRow.addView(markView)
                container.addView(heroRow)

                // Body message
                val safeCount = todayLoopsBroken.coerceAtLeast(1)
                val loopsWord = if (safeCount == 1) "1 loop" else "$safeCount loops"
                val bodyView = TextView(context).apply {
                    text = "$feedLabel is guarded, and that is $loopsWord broken today. $usefulPartsSummary"
                    textSize = 14.5f
                    lineHeight = dp(21)
                    typeface = bodyTypeface
                    setTextColor(inkSecondary)
                    setPadding(dp(2), dp(16), dp(2), dp(14))
                }
                container.addView(bodyView)

                // Twin Mint & Peach Bento Tiles
                val tilesRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                fun createStatTile(bgColor: Int, valueText: String, labelText: String, marginEndDp: Int, marginStartDp: Int): LinearLayout {
                    return LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(13), dp(14), dp(13))
                        background = GradientDrawable().apply {
                            setColor(bgColor)
                            cornerRadius = dpF(18f)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginEnd = dp(marginEndDp)
                            marginStart = dp(marginStartDp)
                        }
                        addView(TextView(context).apply {
                            text = valueText
                            textSize = 20f
                            typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                            setTextColor(inkPrimary)
                        })
                        addView(TextView(context).apply {
                            text = labelText
                            textSize = 12f
                            typeface = bodyTypeface
                            setTextColor(inkSecondary)
                            setPadding(0, dp(2), 0, 0)
                        })
                    }
                }

                val streakLabel = if (streakDays == 1) "1 day" else "$streakDays days"
                tilesRow.addView(createStatTile(mintTileBg, timeKeptText, "time kept", 5, 0))
                tilesRow.addView(createStatTile(peachTileBg, streakLabel, "streak", 0, 5))
                container.addView(tilesRow)

                // Primary dark pill button ("Open DMs")
                val primaryBtn = Button(context).apply {
                    text = primaryUsefulActionLabel
                    contentDescription = "$primaryUsefulActionLabel and leave $appName $feedLabel"
                    isAllCaps = false
                    textSize = 15f
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(ivoryInk)
                    background = GradientDrawable().apply {
                        setColor(darkHeroBg)
                        cornerRadius = dpF(100f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    ).apply {
                        topMargin = dp(16)
                    }
                    minimumHeight = dp(52)
                    setOnClickListener { view ->
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        dismiss()
                        onExitFeed()
                    }
                }
                container.addView(primaryBtn)

                // Secondary mint pill button ("Go to Search")
                val secondaryBtn = Button(context).apply {
                    text = secondaryUsefulActionLabel
                    contentDescription = "$secondaryUsefulActionLabel in $appName"
                    isAllCaps = false
                    textSize = 15f
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(inkPrimary)
                    background = GradientDrawable().apply {
                        setColor(mintTileBg)
                        cornerRadius = dpF(100f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    ).apply {
                        topMargin = dp(10)
                    }
                    minimumHeight = dp(52)
                    setOnClickListener { view ->
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        dismiss()
                        onAllowOnce()
                    }
                }
                container.addView(secondaryBtn)

                // Bottom text button ("Pause for 15 min")
                val pauseBtn = Button(context).apply {
                    text = "Pause for $preferredPauseMinutes min"
                    contentDescription = "Pause Guard for $preferredPauseMinutes minutes"
                    isAllCaps = false
                    textSize = 14f
                    typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
                    setTextColor(inkSecondary)
                    background = null
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                    ).apply {
                        topMargin = dp(6)
                    }
                    minimumHeight = dp(48)
                    setOnClickListener { view ->
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (strictModeEnabled) {
                            modalCard.removeAllViews()
                            modalCard.addView(buildPauseGuardFn())
                        } else {
                            dismiss()
                            onSnoozeMinutes(preferredPauseMinutes)
                        }
                    }
                }
                container.addView(pauseBtn)

                return container
            }

            fun buildPauseGuardContent(): View {
                var selectedMinutes = preferredPauseMinutes.let { if (it == 5 || it == 15 || it == 30) it else 15 }
                var secondsLeft = 10

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // Dark forest header bento card with top-left "Strict mode" pill and center countdown ring
                val heroBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(16), dp(18), dp(22))
                    background = GradientDrawable().apply {
                        setColor(darkHeroBg)
                        cornerRadius = dpF(22f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val strictChip = TextView(context).apply {
                    text = "Strict mode"
                    textSize = 12f
                    typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
                    setTextColor(ivoryInk)
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                    background = GradientDrawable().apply {
                        setColor(darkPillBg)
                        cornerRadius = dpF(100f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                heroBox.addView(strictChip)

                val ringFrame = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(136), dp(136)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = dp(10)
                    }
                }

                var sweepProgress = 1.0f
                val ringCanvas = object : View(context) {
                    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        color = heroTrack
                    }
                    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        color = sageAccent
                    }

                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val strokeW = dpF(11f)
                        trackPaint.strokeWidth = strokeW
                        progressPaint.strokeWidth = strokeW
                        val inset = strokeW / 2f + dp(4)
                        val rect = RectF(inset, inset, width - inset, height - inset)
                        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
                        if (sweepProgress > 0f) {
                            canvas.drawArc(rect, -90f, 360f * sweepProgress, false, progressPaint)
                        }
                    }
                }.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val ringCenter = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                }

                val countdownNumber = TextView(context).apply {
                    text = "10"
                    textSize = 40f
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(ivoryInk)
                    gravity = Gravity.CENTER
                }
                val countdownUnit = TextView(context).apply {
                    text = "seconds"
                    textSize = 12f
                    typeface = bodyTypeface
                    setTextColor(ivoryInk.let { Color.argb(210, Color.red(it), Color.green(it), Color.blue(it)) })
                    gravity = Gravity.CENTER
                }
                ringCenter.addView(countdownNumber)
                ringCenter.addView(countdownUnit)

                ringFrame.addView(ringCanvas)
                ringFrame.addView(ringCenter)
                heroBox.addView(ringFrame)
                container.addView(heroBox)

                // Headline + subtitle
                container.addView(TextView(context).apply {
                    text = "Pause Guard?"
                    textSize = 23f
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(inkPrimary)
                    setPadding(dp(2), dp(18), dp(2), dp(6))
                })
                container.addView(TextView(context).apply {
                    text = "Strict mode asks you to wait 10 seconds first. Still want a break?"
                    textSize = 14.5f
                    lineHeight = dp(21)
                    typeface = bodyTypeface
                    setTextColor(inkSecondary)
                    setPadding(dp(2), 0, dp(2), dp(16))
                })

                // 3-segment duration selector (5 min | 15 min | 30 min)
                val segRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = GradientDrawable().apply {
                        setColor(modalBg)
                        cornerRadius = dpF(100f)
                        setStroke(dp(1), outlineStroke)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                    )
                }

                val durations = listOf(5, 15, 30)
                val segButtons = mutableMapOf<Int, TextView>()
                lateinit var confirmPauseBtn: Button

                fun refreshDurationStyles() {
                    durations.forEach { mins ->
                        val btn = segButtons[mins] ?: return@forEach
                        val isSel = mins == selectedMinutes
                        btn.setTextColor(inkPrimary)
                        btn.typeface = Typeface.create(bodyTypeface, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                        btn.background = if (isSel) {
                            GradientDrawable().apply {
                                setColor(mintTileBg)
                                cornerRadius = dpF(100f)
                            }
                        } else {
                            null
                        }
                    }
                    confirmPauseBtn.text = if (secondsLeft > 0) {
                        "Pause in $secondsLeft s"
                    } else {
                        "Pause for $selectedMinutes min"
                    }
                }

                durations.forEach { mins ->
                    val seg = TextView(context).apply {
                        text = "$mins min"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1f
                        )
                        setOnClickListener { view ->
                            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedMinutes = mins
                            refreshDurationStyles()
                        }
                    }
                    segButtons[mins] = seg
                    segRow.addView(seg)
                }
                container.addView(segRow)

                // Dark primary pill ("Keep guarding")
                val keepGuardingBtn = Button(context).apply {
                    text = "Keep guarding"
                    isAllCaps = false
                    textSize = 15f
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(ivoryInk)
                    background = GradientDrawable().apply {
                        setColor(darkHeroBg)
                        cornerRadius = dpF(100f)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    ).apply {
                        topMargin = dp(16)
                    }
                    minimumHeight = dp(52)
                    setOnClickListener { view ->
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        dismiss()
                        onExitFeed()
                    }
                }
                container.addView(keepGuardingBtn)

                // Outlined secondary pill ("Pause in X s" -> unlocked "Pause for X min")
                confirmPauseBtn = Button(context).apply {
                    text = "Pause in 10 s"
                    isAllCaps = false
                    isEnabled = false
                    textSize = 15f
                    typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                    setTextColor(inkSecondary)
                    background = GradientDrawable().apply {
                        setColor(modalBg)
                        cornerRadius = dpF(100f)
                        setStroke(dp(1), outlineStroke)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    ).apply {
                        topMargin = dp(10)
                    }
                    minimumHeight = dp(52)
                    setOnClickListener { view ->
                        if (secondsLeft > 0) return@setOnClickListener
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        dismiss()
                        onSnoozeMinutes(selectedMinutes)
                    }
                }
                container.addView(confirmPauseBtn)

                refreshDurationStyles()

                val tickRunnable = object : Runnable {
                    override fun run() {
                        if (overlayView == null) return
                        if (secondsLeft > 0) {
                            secondsLeft -= 1
                            countdownNumber.text = secondsLeft.toString()
                            countdownUnit.text = if (secondsLeft == 1) "second" else "seconds"
                            sweepProgress = secondsLeft / 10f
                            ringCanvas.invalidate()
                            if (secondsLeft == 0) {
                                confirmPauseBtn.isEnabled = true
                                confirmPauseBtn.setTextColor(inkPrimary)
                                confirmPauseBtn.background = GradientDrawable().apply {
                                    setColor(mintTileBg)
                                    cornerRadius = dpF(100f)
                                    setStroke(dp(1), Color.parseColor("#3328352C"))
                                }
                            }
                            refreshDurationStyles()
                            if (secondsLeft > 0) {
                                mainHandler.postDelayed(this, 1000L)
                            }
                        }
                    }
                }
                activeCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
                activeCountdownRunnable = tickRunnable
                mainHandler.postDelayed(tickRunnable, 1000L)

                return container
            }

            buildPauseGuardFn = ::buildPauseGuardContent
            modalCard.addView(buildFeedBlockedContent())

            scrollContainer.addView(
                modalCard,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val scrollParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            root.addView(scrollContainer, scrollParams)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                modalCard.alpha = 0f
                modalCard.scaleX = 0.94f
                modalCard.scaleY = 0.94f
                windowManager.addView(root, layoutParams)
                overlayView = root
                modalCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                root.requestFocus()
                modalCard.post {
                    modalCard.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                }
            } catch (_: Exception) {
                overlayView = null
                onAttachmentFailed()
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            activeCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
            activeCountdownRunnable = null
            val view = overlayView ?: return@post
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            } finally {
                overlayView = null
            }
        }
    }
}
