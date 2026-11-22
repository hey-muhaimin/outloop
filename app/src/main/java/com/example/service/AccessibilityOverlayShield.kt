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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AccessibilityOverlayShield(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var activeCountdownRunnable: Runnable? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        appName: String,
        feedLabel: String,
        loopsToday: Int = 1,
        timeKeptText: String = "0m",
        streakDays: Int = 1,
        strictModeEnabled: Boolean = false,
        useDarkTheme: Boolean = false,
        hapticsEnabled: Boolean = true,
        onExitFeed: () -> Unit,
        onAllowOnce: () -> Unit,
        onPauseForMinutes: (Int) -> Unit = {},
        onAttachmentFailed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (overlayView != null) return@post

            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val scrimColor = Color.parseColor("#EB141D16")
            val cardBg = Color.parseColor(if (useDarkTheme) "#1A231D" else "#F8F7F1")
            val heroBg = Color.parseColor(if (useDarkTheme) "#212E25" else "#253328")
            val heroPillBg = Color.parseColor(if (useDarkTheme) "#334338" else "#3A493D")
            val onHero = Color.parseColor(if (useDarkTheme) "#F2F0E8" else "#F7F6F0")
            val heroAccent = Color.parseColor(if (useDarkTheme) "#A8C49C" else "#B6CEA9")
            val mintBg = Color.parseColor(if (useDarkTheme) "#203428" else "#D3E4CE")
            val mintInk = Color.parseColor(if (useDarkTheme) "#DCEBD6" else "#1E2820")
            val peachBg = Color.parseColor(if (useDarkTheme) "#352620" else "#F3DCD0")
            val peachInk = Color.parseColor(if (useDarkTheme) "#F3DCD0" else "#241D1A")
            val primaryBtnBg = Color.parseColor(if (useDarkTheme) "#A8C49C" else "#253328")
            val primaryBtnText = Color.parseColor(if (useDarkTheme) "#141D17" else "#F7F6F0")
            val headlineColor = Color.parseColor(if (useDarkTheme) "#F1EFE7" else "#1D241E")
            val secondaryText = Color.parseColor(if (useDarkTheme) "#AEB7AD" else "#596158")
            val outlineStroke = Color.parseColor(if (useDarkTheme) "#3DFFFFFF" else "#381E261E")
            val dividerStroke = Color.parseColor(if (useDarkTheme) "#22FFFFFF" else "#1A1E261E")

            val root = FrameLayout(context).apply {
                setBackgroundColor(scrimColor)
                setPadding(dp(20), dp(24), dp(20), dp(24))
                isFocusable = true
                isFocusableInTouchMode = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "Loop broken. $appName $feedLabel is guarded."
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    accessibilityPaneTitle = "Outloop Guard"
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

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(18), dp(18), dp(18), dp(14))
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = dp(30).toFloat()
                }
            }

            fun hapticTap(view: View) {
                if (hapticsEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }

            lateinit var openStrictPause: () -> Unit

            fun showLoopBrokenView() {
                cancelCountdown()
                card.removeAllViews()

                // Dark inner hero banner
                val heroRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(20), dp(20), dp(20))
                    background = GradientDrawable().apply {
                        setColor(heroBg)
                        cornerRadius = dp(24).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val heroLeft = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val contextBadge = TextView(context).apply {
                    text = "$appName \u00B7 $feedLabel"
                    textSize = 13f
                    setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(onHero)
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                    background = GradientDrawable().apply {
                        setColor(heroPillBg)
                        cornerRadius = dp(100).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val loopBrokenTitle = TextView(context).apply {
                    text = "Loop\nbroken"
                    textSize = 29f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(onHero)
                    setPadding(0, dp(10), 0, 0)
                    isFocusable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }

                heroLeft.addView(contextBadge)
                heroLeft.addView(loopBrokenTitle)

                val markView = object : View(context) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        color = heroAccent
                    }

                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val strokeW = dp(9).toFloat()
                        paint.strokeWidth = strokeW
                        val inset = strokeW / 2f + dp(2)
                        val rect = RectF(inset, inset, width - inset, height - inset)
                        canvas.drawArc(rect, -27f, -324f, false, paint)
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

                heroRow.addView(heroLeft)
                heroRow.addView(markView)
                card.addView(heroRow)

                val safeOpenAreaCopy = when {
                    appName.contains("Instagram", ignoreCase = true) -> "DMs and Search are still open."
                    appName.contains("YouTube", ignoreCase = true) -> "Home and Search are still open."
                    appName.contains("Facebook", ignoreCase = true) -> "Messenger and Home are still open."
                    else -> "Messages and Search are still open."
                }
                val loopNoun = if (loopsToday == 1) "loop" else "loops"

                val body = TextView(context).apply {
                    text = "$feedLabel is guarded, and that is $loopsToday $loopNoun broken today. $safeOpenAreaCopy"
                    textSize = 15f
                    setTextColor(secondaryText)
                    setPadding(dp(4), dp(14), dp(4), dp(14))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                card.addView(body)

                // Mint + Peach stat tiles row
                val statsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                fun createStatTile(value: String, label: String, bg: Int, fg: Int): LinearLayout {
                    return LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16), dp(14), dp(16), dp(14))
                        background = GradientDrawable().apply {
                            setColor(bg)
                            cornerRadius = dp(20).toFloat()
                        }
                        addView(TextView(context).apply {
                            text = value
                            textSize = 21f
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextColor(fg)
                        })
                        addView(TextView(context).apply {
                            text = label
                            textSize = 13f
                            setTextColor(fg)
                            alpha = 0.85f
                        })
                    }
                }

                val mintTile = createStatTile(timeKeptText, "time kept", mintBg, mintInk).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tileGap = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(10), 1)
                }
                val streakUnit = if (streakDays == 1) "day" else "days"
                val peachTile = createStatTile("$streakDays $streakUnit", "streak", peachBg, peachInk).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                statsRow.addView(mintTile)
                statsRow.addView(tileGap)
                statsRow.addView(peachTile)
                card.addView(statsRow)

                val actionGapTop = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) }
                card.addView(actionGapTop)

                val primaryLabel = when {
                    appName.contains("Instagram", ignoreCase = true) -> "Open DMs"
                    appName.contains("YouTube", ignoreCase = true) -> "Go to Home"
                    appName.contains("Facebook", ignoreCase = true) -> "Open Messenger"
                    else -> "Return to safe area"
                }
                val secondaryLabel = when {
                    appName.contains("Instagram", ignoreCase = true) -> "Go to Search"
                    appName.contains("YouTube", ignoreCase = true) -> "Go to Search"
                    appName.contains("Facebook", ignoreCase = true) -> "Allow once"
                    else -> "Allow once"
                }

                val exitButton = Button(context).apply {
                    text = primaryLabel
                    contentDescription = "$primaryLabel and leave $appName $feedLabel"
                    isAllCaps = false
                    textSize = 16f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(primaryBtnText)
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    background = GradientDrawable().apply {
                        setColor(primaryBtnBg)
                        cornerRadius = dp(100).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    minimumHeight = dp(54)
                    minHeight = dp(54)
                    setOnClickListener { view ->
                        hapticTap(view)
                        dismiss()
                        onExitFeed()
                    }
                }
                card.addView(exitButton)

                val btnGap1 = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(10)) }
                card.addView(btnGap1)

                val allowButton = Button(context).apply {
                    text = secondaryLabel
                    contentDescription = "Allow $appName $feedLabel once"
                    isAllCaps = false
                    textSize = 16f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(mintInk)
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    background = GradientDrawable().apply {
                        setColor(mintBg)
                        cornerRadius = dp(100).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    minimumHeight = dp(52)
                    minHeight = dp(52)
                    setOnClickListener { view ->
                        hapticTap(view)
                        dismiss()
                        onAllowOnce()
                    }
                }
                card.addView(allowButton)

                val btnGap2 = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(4)) }
                card.addView(btnGap2)

                val pauseButton = Button(context).apply {
                    text = "Pause for 15 min"
                    contentDescription = "Pause Guard for 15 minutes"
                    isAllCaps = false
                    textSize = 15f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(secondaryText)
                    background = null
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    minimumHeight = dp(48)
                    minHeight = dp(48)
                    setOnClickListener { view ->
                        hapticTap(view)
                        if (strictModeEnabled) {
                            openStrictPause()
                        } else {
                            dismiss()
                            onPauseForMinutes(15)
                        }
                    }
                }
                card.addView(pauseButton)

                loopBrokenTitle.post {
                    loopBrokenTitle.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                }
            }

            fun showStrictPauseGuardView() {
                cancelCountdown()
                card.removeAllViews()

                var selectedMinutes = 15
                var secondsLeft = STRICT_WAIT_SECONDS

                val heroBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(18), dp(18), dp(18), dp(22))
                    background = GradientDrawable().apply {
                        setColor(heroBg)
                        cornerRadius = dp(24).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val strictPill = TextView(context).apply {
                    text = "Strict mode"
                    textSize = 13f
                    setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(onHero)
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                    background = GradientDrawable().apply {
                        setColor(heroPillBg)
                        cornerRadius = dp(100).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.START
                    }
                }
                heroBox.addView(strictPill)

                val ringContainer = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(116), dp(116)).apply {
                        topMargin = dp(10)
                    }
                }

                val countdownRing = object : View(context) {
                    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        color = heroPillBg
                    }
                    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        color = heroAccent
                    }

                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val strokeW = dp(10).toFloat()
                        trackPaint.strokeWidth = strokeW
                        sweepPaint.strokeWidth = strokeW
                        val inset = strokeW / 2f + dp(2)
                        val rect = RectF(inset, inset, width - inset, height - inset)
                        canvas.drawArc(rect, -90f, 360f, false, trackPaint)
                        val progress = (secondsLeft.toFloat() / STRICT_WAIT_SECONDS.toFloat()).coerceIn(0f, 1f)
                        if (progress > 0f) {
                            canvas.drawArc(rect, -90f, 360f * progress, false, sweepPaint)
                        }
                    }
                }.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val numberStack = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                }
                val countNumView = TextView(context).apply {
                    text = secondsLeft.toString()
                    textSize = 34f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(onHero)
                    gravity = Gravity.CENTER
                }
                val countUnitView = TextView(context).apply {
                    text = "seconds"
                    textSize = 13f
                    setTextColor(onHero)
                    alpha = 0.78f
                    gravity = Gravity.CENTER
                }
                numberStack.addView(countNumView)
                numberStack.addView(countUnitView)

                ringContainer.addView(countdownRing)
                ringContainer.addView(numberStack)
                heroBox.addView(ringContainer)
                card.addView(heroBox)

                val pauseTitle = TextView(context).apply {
                    text = "Pause Guard?"
                    textSize = 25f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(headlineColor)
                    gravity = Gravity.START
                    setPadding(dp(4), dp(16), dp(4), dp(4))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    isFocusable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                card.addView(pauseTitle)

                val pauseBody = TextView(context).apply {
                    text = "Strict mode asks you to wait $STRICT_WAIT_SECONDS seconds first. Still want a break?"
                    textSize = 15f
                    setTextColor(secondaryText)
                    setPadding(dp(4), 0, dp(4), dp(16))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                card.addView(pauseBody)

                val confirmPauseButton = Button(context).apply {
                    text = "Pause in $secondsLeft s"
                    isAllCaps = false
                    isEnabled = false
                    alpha = 0.65f
                    textSize = 16f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(secondaryText)
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    background = GradientDrawable().apply {
                        setColor(cardBg)
                        cornerRadius = dp(100).toFloat()
                        setStroke(dp(1), outlineStroke)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    minimumHeight = dp(52)
                    minHeight = dp(52)
                    setOnClickListener { view ->
                        if (secondsLeft == 0) {
                            hapticTap(view)
                            dismiss()
                            onPauseForMinutes(selectedMinutes)
                        }
                    }
                }

                // Segmented 5 min | 15 min | 30 min duration selector
                val segmentContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(cardBg)
                        cornerRadius = dp(100).toFloat()
                        setStroke(dp(1), outlineStroke)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(46)
                    )
                }

                val durations = listOf(5, 15, 30)
                val optionViews = mutableListOf<TextView>()

                fun refreshDurationSelection() {
                    durations.forEachIndexed { idx, mins ->
                        val active = mins == selectedMinutes
                        val tv = optionViews[idx]
                        tv.setBackgroundColor(if (active) mintBg else Color.TRANSPARENT)
                        tv.setTextColor(if (active) mintInk else headlineColor)
                        tv.setTypeface(Typeface.SANS_SERIF, if (active) Typeface.BOLD else Typeface.NORMAL)
                    }
                    if (secondsLeft == 0) {
                        confirmPauseButton.text = "Pause for $selectedMinutes min"
                    }
                }

                durations.forEachIndexed { idx, mins ->
                    if (idx > 0) {
                        segmentContainer.addView(View(context).apply {
                            setBackgroundColor(dividerStroke)
                            layoutParams = LinearLayout.LayoutParams(
                                dp(1),
                                LinearLayout.LayoutParams.MATCH_PARENT
                            )
                        })
                    }
                    val tv = TextView(context).apply {
                        text = "$mins min"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1f
                        )
                        setOnClickListener { view ->
                            hapticTap(view)
                            selectedMinutes = mins
                            refreshDurationSelection()
                        }
                    }
                    optionViews += tv
                    segmentContainer.addView(tv)
                }
                refreshDurationSelection()
                card.addView(segmentContainer)

                card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })

                val keepGuardingButton = Button(context).apply {
                    text = "Keep guarding"
                    isAllCaps = false
                    textSize = 16f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(primaryBtnText)
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    background = GradientDrawable().apply {
                        setColor(primaryBtnBg)
                        cornerRadius = dp(100).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    minimumHeight = dp(54)
                    minHeight = dp(54)
                    setOnClickListener { view ->
                        hapticTap(view)
                        dismiss()
                        onExitFeed()
                    }
                }
                card.addView(keepGuardingButton)
                card.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(10)) })
                card.addView(confirmPauseButton)

                val tickRunnable = object : Runnable {
                    override fun run() {
                        if (overlayView == null) return
                        if (secondsLeft > 0) {
                            secondsLeft--
                            countNumView.text = secondsLeft.toString()
                            countUnitView.text = if (secondsLeft == 1) "second" else "seconds"
                            countdownRing.invalidate()
                            if (secondsLeft > 0) {
                                confirmPauseButton.text = "Pause in $secondsLeft s"
                                mainHandler.postDelayed(this, 1_000L)
                            } else {
                                confirmPauseButton.isEnabled = true
                                confirmPauseButton.alpha = 1f
                                confirmPauseButton.setTextColor(headlineColor)
                                confirmPauseButton.text = "Pause for $selectedMinutes min"
                            }
                        }
                    }
                }
                activeCountdownRunnable = tickRunnable
                mainHandler.postDelayed(tickRunnable, 1_000L)

                pauseTitle.post {
                    pauseTitle.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                }
            }

            openStrictPause = ::showStrictPauseGuardView

            showLoopBrokenView()

            scrollContainer.addView(
                card,
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
                windowManager.addView(root, layoutParams)
                overlayView = root
                root.requestFocus()
            } catch (_: Exception) {
                cancelCountdown()
                overlayView = null
                onAttachmentFailed()
            }
        }
    }

    private fun cancelCountdown() {
        activeCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        activeCountdownRunnable = null
    }

    fun dismiss() {
        mainHandler.post {
            cancelCountdown()
            val view = overlayView ?: return@post
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            } finally {
                overlayView = null
            }
        }
    }

    companion object {
        private const val STRICT_WAIT_SECONDS = 10
    }
}
