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
import com.example.data.repository.InterventionRepository

enum class OverlayTriggerMode {
    FEED_GUARD,
    DAILY_LIMIT_REACHED
}

data class OverlayPresentationConfig(
    val appName: String,
    val feedLabel: String,
    val triggerMode: OverlayTriggerMode = OverlayTriggerMode.FEED_GUARD,
    val dimAlpha: Float = DEFAULT_DIM_ALPHA,
    val useDarkTheme: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val strictModeEnabled: Boolean = true,
    val todayLoopsBroken: Int = 1,
    val timeKeptText: String = "0 min",
    val streakDays: Int = 0,
    val dailyUsedSeconds: Long = 0L,
    val dailyLimitMinutes: Int = 0,
    val preferredPauseMinutes: Int = 15,
    val primaryUsefulActionLabel: String = "Open DMs",
    val secondaryUsefulActionLabel: String = "Go to Search",
    val usefulPartsSummary: String = "DMs and Search are still open."
) {
    companion object {
        const val DEFAULT_DIM_ALPHA = 0.80f
    }
}

/**
 * Transparent UI overlay component triggered by [FeedSurgeonService] via [TYPE_ACCESSIBILITY_OVERLAY].
 * Draws an animated translucent dim scrim over the underlying short-form video feed while presenting
 * the Light Tonal Bento Pause Guard card (supporting both feed-scroll and daily-limit triggers).
 */
class TransparentPauseDimOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var activeCountdownRunnable: Runnable? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        config: OverlayPresentationConfig,
        onExitFeed: () -> Unit,
        onAllowOnce: () -> Unit,
        onSnoozeMinutes: (Int) -> Unit = {},
        onAttachmentFailed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (overlayView != null) return@post

            val built = buildOverlayViews(
                config = config,
                onDismissRequest = { dismiss() },
                onExitFeed = onExitFeed,
                onAllowOnce = onAllowOnce,
                onSnoozeMinutes = onSnoozeMinutes
            )

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
                built.dimScrim.alpha = 0f
                built.modalCard.alpha = 0f
                built.modalCard.scaleX = 0.94f
                built.modalCard.scaleY = 0.94f
                windowManager.addView(built.root, layoutParams)
                overlayView = built.root

                built.dimScrim.animate()
                    .alpha(1f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                built.modalCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                built.root.requestFocus()
                built.modalCard.post {
                    built.modalCard.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                }
            } catch (_: Exception) {
                activeCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
                activeCountdownRunnable = null
                overlayView = null
                onAttachmentFailed()
            }
        }
    }

    internal data class OverlayViewHandles(
        val root: FrameLayout,
        val dimScrim: View,
        val modalCard: LinearLayout
    )

    internal fun buildOverlayViews(
        config: OverlayPresentationConfig,
        onDismissRequest: () -> Unit,
        onExitFeed: () -> Unit,
        onAllowOnce: () -> Unit,
        onSnoozeMinutes: (Int) -> Unit
    ): OverlayViewHandles {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        fun dpF(value: Float): Float = value * density

        val headingTypeface = runCatching {
            ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val bodyTypeface = runCatching {
            ResourcesCompat.getFont(context, R.font.inter)
        }.getOrNull() ?: Typeface.SANS_SERIF

        val clampedDimAlpha = config.dimAlpha.coerceIn(0.35f, 0.92f)
        val dimAlphaInt = (clampedDimAlpha * 255f).toInt().coerceIn(80, 235)
        val scrimColor = Color.argb(dimAlphaInt, 0x11, 0x12, 0x14)

        val isDark = config.useDarkTheme
        val modalBg = if (isDark) Color.parseColor("#1A1B1E") else Color.parseColor("#FAFAF8")
        val modalStroke = if (isDark) Color.parseColor("#1AFAFAF8") else Color.parseColor("#14111214")
        val darkHeroBg = Color.parseColor("#1C1D1F")
        val darkPillBg = Color.parseColor("#2B2D30")
        val sageAccent = Color.parseColor("#D5DCD7")
        val heroTrack = Color.parseColor("#34363A")
        val mintTileBg = if (isDark) Color.parseColor("#26282C") else Color.parseColor("#E7E7E3")
        val peachTileBg = if (isDark) Color.parseColor("#2F2821") else Color.parseColor("#EFE9E1")
        val inkPrimary = if (isDark) Color.parseColor("#FAFAF8") else Color.parseColor("#111214")
        val inkSecondary = if (isDark) Color.parseColor("#A7AAB0") else Color.parseColor("#5B5E63")
        val ivoryInk = Color.parseColor("#FAFAF8")
        val warnTextColor = Color.parseColor("#7A4E27")
        val outlineStroke = if (isDark) Color.parseColor("#33FAFAF8") else Color.parseColor("#24111214")

        val root = FrameLayout(context).apply {
            tag = TAG_OVERLAY_ROOT
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = if (config.triggerMode == OverlayTriggerMode.DAILY_LIMIT_REACHED) {
                "Daily limit reached. Outloop dimmed ${config.appName} ${config.feedLabel}."
            } else {
                "Loop broken. Outloop dimmed ${config.appName} ${config.feedLabel}."
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                accessibilityPaneTitle = "Outloop Pause Guard"
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onDismissRequest()
                    onExitFeed()
                    true
                } else {
                    false
                }
            }
        }

        // Translucent dim layer covering the entire short-form video feed
        val dimScrim = View(context).apply {
            tag = TAG_DIM_SCRIM
            setBackgroundColor(scrimColor)
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            contentDescription = "Dimmed feed backdrop"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(dimScrim)

        val scrollContainer = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
            setPadding(dp(18), dp(24), dp(18), dp(24))
        }

        val modalCard = LinearLayout(context).apply {
            tag = TAG_MODAL_CARD
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(modalBg)
                cornerRadius = dpF(28f)
                setStroke(dp(1), modalStroke)
            }
        }

        lateinit var buildPauseGuardFn: (isDailyLimitPause: Boolean) -> View

        fun buildFeedBlockedContent(): View {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val isLimitReached = config.triggerMode == OverlayTriggerMode.DAILY_LIMIT_REACHED

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
                text = if (isLimitReached) {
                    "${config.appName} \u00B7 Daily limit"
                } else {
                    "${config.appName} \u00B7 ${config.feedLabel}"
                }
                textSize = 12f
                typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
                setTextColor(
                    if (isLimitReached) warnTextColor
                    else Color.argb(235, Color.red(ivoryInk), Color.green(ivoryInk), Color.blue(ivoryInk))
                )
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = GradientDrawable().apply {
                    setColor(if (isLimitReached) Color.parseColor("#EFE9E1") else darkPillBg)
                    cornerRadius = dpF(100f)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val heroHeadline = TextView(context).apply {
                text = if (isLimitReached) "Daily limit\nreached" else "Loop\nbroken"
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
                    color = if (isLimitReached) Color.parseColor("#EFE9E1") else sageAccent
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

            val safeCount = config.todayLoopsBroken.coerceAtLeast(1)
            val loopsWord = if (safeCount == 1) "1 loop" else "$safeCount loops"
            val usedCompact = InterventionRepository.formatDurationCompact(config.dailyUsedSeconds)

            val bodyCopy = if (isLimitReached && config.dailyLimitMinutes > 0) {
                "You reached your ${config.dailyLimitMinutes}m daily budget for ${config.appName} ($usedCompact used today). Step out to keep your focus."
            } else {
                "${config.feedLabel} is dimmed and guarded, and that is $loopsWord broken today. ${config.usefulPartsSummary}"
            }

            val bodyView = TextView(context).apply {
                text = bodyCopy
                textSize = 14.5f
                lineHeight = dp(21)
                typeface = bodyTypeface
                setTextColor(inkSecondary)
                setPadding(dp(2), dp(16), dp(2), dp(14))
            }
            container.addView(bodyView)

            val tilesRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            fun createStatTile(
                bgColor: Int,
                valueText: String,
                labelText: String,
                marginEndDp: Int,
                marginStartDp: Int
            ): LinearLayout {
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

            if (config.dailyLimitMinutes > 0) {
                val limitText = "$usedCompact / ${config.dailyLimitMinutes}m"
                tilesRow.addView(createStatTile(peachTileBg, limitText, "used today", 5, 0))
                tilesRow.addView(createStatTile(mintTileBg, config.timeKeptText, "time kept", 0, 5))
            } else {
                val streakLabel = if (config.streakDays == 1) "1 day" else "${config.streakDays} days"
                tilesRow.addView(createStatTile(Color.parseColor(if (isDark) "#232529" else "#F2F2EF"), config.timeKeptText, "time kept", 5, 0))
                tilesRow.addView(createStatTile(mintTileBg, streakLabel, "streak", 0, 5))
            }
            container.addView(tilesRow)

            // Primary dark pill button ("Open DMs" or "Leave Instagram")
            val primaryLabel = if (isLimitReached) {
                "Leave ${config.appName}"
            } else {
                config.primaryUsefulActionLabel
            }
            val primaryBtn = Button(context).apply {
                tag = TAG_PRIMARY_EXIT_BTN
                text = primaryLabel
                contentDescription = "$primaryLabel and leave ${config.appName} ${config.feedLabel}"
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
                    if (config.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismissRequest()
                    onExitFeed()
                }
            }
            container.addView(primaryBtn)

            if (!isLimitReached) {
                val secondaryBtn = Button(context).apply {
                    tag = TAG_SECONDARY_ALLOW_BTN
                    text = config.secondaryUsefulActionLabel
                    contentDescription = "${config.secondaryUsefulActionLabel} in ${config.appName}"
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
                        if (config.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismissRequest()
                        onAllowOnce()
                    }
                }
                container.addView(secondaryBtn)
            }

            val pauseBtnLabel = if (isLimitReached) {
                "Extend limit for ${config.preferredPauseMinutes} min"
            } else {
                "Pause for ${config.preferredPauseMinutes} min"
            }
            val pauseBtn = Button(context).apply {
                tag = TAG_PAUSE_GUARD_BTN
                text = pauseBtnLabel
                contentDescription = "Open Pause Guard options for ${config.preferredPauseMinutes} minutes"
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
                    if (config.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (config.strictModeEnabled) {
                        modalCard.removeAllViews()
                        modalCard.addView(buildPauseGuardFn(isLimitReached))
                    } else {
                        onDismissRequest()
                        onSnoozeMinutes(config.preferredPauseMinutes)
                    }
                }
            }
            container.addView(pauseBtn)

            return container
        }

        fun buildPauseGuardContent(isDailyLimitPause: Boolean): View {
            var selectedMinutes = config.preferredPauseMinutes.let { if (it == 5 || it == 15 || it == 30) it else 15 }
            var secondsLeft = 10

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

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
                text = if (isDailyLimitPause) "Strict mode \u00B7 Daily limit" else "Strict mode"
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
                    color = if (isDailyLimitPause) Color.parseColor("#EFE9E1") else sageAccent
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
                setTextColor(Color.argb(210, Color.red(ivoryInk), Color.green(ivoryInk), Color.blue(ivoryInk)))
                gravity = Gravity.CENTER
            }
            ringCenter.addView(countdownNumber)
            ringCenter.addView(countdownUnit)

            ringFrame.addView(ringCanvas)
            ringFrame.addView(ringCenter)
            heroBox.addView(ringFrame)
            container.addView(heroBox)

            container.addView(TextView(context).apply {
                text = if (isDailyLimitPause) "Pause daily limit?" else "Pause Guard?"
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
                        if (config.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedMinutes = mins
                        refreshDurationStyles()
                    }
                }
                segButtons[mins] = seg
                segRow.addView(seg)
            }
            container.addView(segRow)

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
                    if (config.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismissRequest()
                    onExitFeed()
                }
            }
            container.addView(keepGuardingBtn)

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
                    if (config.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismissRequest()
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
                                setStroke(dp(1), outlineStroke)
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

        return OverlayViewHandles(
            root = root,
            dimScrim = dimScrim,
            modalCard = modalCard
        )
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

    companion object {
        const val TAG_OVERLAY_ROOT = "outloop_dim_overlay_root"
        const val TAG_DIM_SCRIM = "outloop_dim_scrim"
        const val TAG_MODAL_CARD = "outloop_pause_guard_card"
        const val TAG_PRIMARY_EXIT_BTN = "outloop_overlay_primary_exit"
        const val TAG_SECONDARY_ALLOW_BTN = "outloop_overlay_secondary_allow"
        const val TAG_PAUSE_GUARD_BTN = "outloop_overlay_pause_guard"
    }
}
