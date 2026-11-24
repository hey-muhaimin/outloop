package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
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
 * the Monochrome Editorial Tonal Bento Pause Guard card matching [OutloopMetrics] and [FloatingPillNavBar]:
 *  - Row 1: Nav-dock-style inline Home | Exit pill row with icons (64dp pill dock)
 *  - Row 2: Full-width Pause pill button (52dp height)
 */
class TransparentPauseDimOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var activeCountdownRunnable: Runnable? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        config: OverlayPresentationConfig,
        onGoHome: () -> Unit,
        onExitApp: () -> Unit,
        onSnoozeMinutes: (Int) -> Unit = {},
        onAttachmentFailed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (overlayView != null) return@post

            val built = buildOverlayViews(
                config = config,
                onDismissRequest = { dismiss() },
                onGoHome = onGoHome,
                onExitApp = onExitApp,
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
        onGoHome: () -> Unit,
        onExitApp: () -> Unit,
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
        val canvasColor = if (isDark) Color.parseColor("#111214") else Color.parseColor("#F2F2EF")
        val modalBg = if (isDark) Color.parseColor("#1A1B1E") else Color.parseColor("#FAFAF8")
        val elevatedBg = if (isDark) Color.parseColor("#232529") else Color.parseColor("#FFFFFF")
        val modalStroke = if (isDark) Color.parseColor("#1AFAFAF8") else Color.parseColor("#14111214")
        val darkHeroBg = Color.parseColor("#1C1D1F")
        val darkPillBg = Color.parseColor("#2B2D30")
        val sageAccent = Color.parseColor("#D5DCD7")
        val heroTrack = Color.parseColor("#34363A")
        val subtleTrackBg = if (isDark) Color.parseColor("#26282C") else Color.parseColor("#E7E7E3")
        val warnBg = if (isDark) Color.parseColor("#2F2821") else Color.parseColor("#EFE9E1")
        val warnTextColor = if (isDark) Color.parseColor("#E8D8C6") else Color.parseColor("#7A4E27")
        val inkPrimary = if (isDark) Color.parseColor("#FAFAF8") else Color.parseColor("#111214")
        val inkSecondary = if (isDark) Color.parseColor("#A7AAB0") else Color.parseColor("#5B5E63")
        val ivoryInk = Color.parseColor("#FAFAF8")
        val outlineStroke = if (isDark) Color.parseColor("#33FAFAF8") else Color.parseColor("#14111214")

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
                    onGoHome()
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
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(modalBg)
                cornerRadius = dpF(28f)
                setStroke(dp(1), modalStroke)
            }
        }

        lateinit var buildPauseGuardFn: (isDailyLimitPause: Boolean) -> View

        fun createDockIconView(isHomeIcon: Boolean, tintColor: Int): View {
            return object : View(context) {
                private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = tintColor
                }
                private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = tintColor
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val w = width.toFloat()
                    val h = height.toFloat()
                    if (w <= 0f || h <= 0f) return
                    val sw = dpF(1.9f)
                    strokePaint.strokeWidth = sw

                    if (isHomeIcon) {
                        // Crisp rounded house glyph matching Icons.Rounded.Home
                        val roofPath = Path().apply {
                            moveTo(w * 0.16f, h * 0.46f)
                            lineTo(w * 0.50f, h * 0.17f)
                            lineTo(w * 0.84f, h * 0.46f)
                        }
                        canvas.drawPath(roofPath, strokePaint)

                        val houseBody = Path().apply {
                            moveTo(w * 0.24f, h * 0.44f)
                            lineTo(w * 0.24f, h * 0.82f)
                            lineTo(w * 0.42f, h * 0.82f)
                            lineTo(w * 0.42f, h * 0.60f)
                            lineTo(w * 0.58f, h * 0.60f)
                            lineTo(w * 0.58f, h * 0.82f)
                            lineTo(w * 0.76f, h * 0.82f)
                            lineTo(w * 0.76f, h * 0.44f)
                        }
                        canvas.drawPath(houseBody, strokePaint)
                    } else {
                        // Crisp exit-app door + right arrow glyph matching ExitToApp
                        val framePath = Path().apply {
                            moveTo(w * 0.56f, h * 0.24f)
                            lineTo(w * 0.56f, h * 0.18f)
                            lineTo(w * 0.22f, h * 0.18f)
                            lineTo(w * 0.22f, h * 0.82f)
                            lineTo(w * 0.56f, h * 0.82f)
                            lineTo(w * 0.56f, h * 0.76f)
                        }
                        canvas.drawPath(framePath, strokePaint)

                        // Arrow shaft
                        canvas.drawLine(w * 0.44f, h * 0.50f, w * 0.82f, h * 0.50f, strokePaint)

                        // Arrow head
                        val headPath = Path().apply {
                            moveTo(w * 0.68f, h * 0.35f)
                            lineTo(w * 0.83f, h * 0.50f)
                            lineTo(w * 0.68f, h * 0.65f)
                        }
                        canvas.drawPath(headPath, strokePaint)
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(19), dp(19))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }

        fun buildDockActionItem(
            tagName: String,
            label: String,
            accessibilityDescription: String,
            isHomeIcon: Boolean,
            isElevatedPill: Boolean,
            onClick: () -> Unit
        ): LinearLayout {
            return LinearLayout(context).apply {
                tag = tagName
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = accessibilityDescription
                minimumHeight = dp(48)
                background = if (isElevatedPill) {
                    GradientDrawable().apply {
                        setColor(elevatedBg)
                        cornerRadius = dpF(100f)
                        setStroke(dp(1), modalStroke)
                    }
                } else {
                    null
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    if (!isHomeIcon) marginStart = dp(4)
                }

                addView(createDockIconView(isHomeIcon = isHomeIcon, tintColor = inkPrimary))

                addView(TextView(context).apply {
                    text = label
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    typeface = Typeface.create(
                        bodyTypeface,
                        if (isElevatedPill) Typeface.BOLD else Typeface.BOLD
                    )
                    setTextColor(inkPrimary)
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(1), dp(4), 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })

                setOnClickListener { view ->
                    if (config.hapticsEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    onClick()
                }
            }
        }

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
                setPadding(dp(20), dp(18), dp(20), dp(18))
                background = GradientDrawable().apply {
                    setColor(darkHeroBg)
                    cornerRadius = dpF(26f)
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
                    setColor(if (isLimitReached) warnBg else darkPillBg)
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
                setPadding(0, dp(10), 0, 0)
                isFocusable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }

            heroLeft.addView(pillChip)
            heroLeft.addView(heroHeadline)

            val markView = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    color = if (isLimitReached) warnBg else sageAccent
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val strokeW = dpF(9.5f)
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
                "You reached your ${config.dailyLimitMinutes}m daily budget for ${config.appName} ($usedCompact used today). Return to Home or exit ${config.appName}."
            } else {
                "${config.feedLabel} is dimmed and guarded ($loopsWord broken today). ${config.usefulPartsSummary}"
            }

            val bodyView = TextView(context).apply {
                text = bodyCopy
                textSize = 14.5f
                lineHeight = dp(21)
                typeface = bodyTypeface
                setTextColor(inkSecondary)
                setPadding(dp(2), dp(14), dp(2), dp(14))
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
                valueColor: Int,
                labelColor: Int,
                marginEndDp: Int,
                marginStartDp: Int
            ): LinearLayout {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(16), dp(18), dp(16))
                    background = GradientDrawable().apply {
                        setColor(bgColor)
                        cornerRadius = dpF(22f)
                        setStroke(dp(1), modalStroke)
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
                        textSize = 23f
                        typeface = Typeface.create(headingTypeface, Typeface.BOLD)
                        setTextColor(valueColor)
                    })
                    addView(TextView(context).apply {
                        text = labelText
                        textSize = 12f
                        typeface = bodyTypeface
                        setTextColor(labelColor)
                        setPadding(0, dp(2), 0, 0)
                    })
                }
            }

            if (config.dailyLimitMinutes > 0) {
                val limitText = "$usedCompact / ${config.dailyLimitMinutes}m"
                tilesRow.addView(
                    createStatTile(
                        bgColor = if (isLimitReached) warnBg else canvasColor,
                        valueText = limitText,
                        labelText = "used today",
                        valueColor = if (isLimitReached) warnTextColor else inkPrimary,
                        labelColor = if (isLimitReached) warnTextColor else inkSecondary,
                        marginEndDp = 5,
                        marginStartDp = 0
                    )
                )
                tilesRow.addView(
                    createStatTile(
                        bgColor = subtleTrackBg,
                        valueText = config.timeKeptText,
                        labelText = "time kept",
                        valueColor = inkPrimary,
                        labelColor = inkSecondary,
                        marginEndDp = 0,
                        marginStartDp = 5
                    )
                )
            } else {
                val streakLabel = if (config.streakDays == 1) "1 day" else "${config.streakDays} days"
                tilesRow.addView(
                    createStatTile(
                        bgColor = canvasColor,
                        valueText = config.timeKeptText,
                        labelText = "time kept",
                        valueColor = inkPrimary,
                        labelColor = inkSecondary,
                        marginEndDp = 5,
                        marginStartDp = 0
                    )
                )
                tilesRow.addView(
                    createStatTile(
                        bgColor = subtleTrackBg,
                        valueText = streakLabel,
                        labelText = "streak",
                        valueColor = inkPrimary,
                        labelColor = inkSecondary,
                        marginEndDp = 0,
                        marginStartDp = 5
                    )
                )
            }
            container.addView(tilesRow)

            // Row 1: Nav-dock-style inline Home | Exit bar (64dp pill dock matching FloatingPillNavBar)
            val dockOuterFrame = FrameLayout(context).apply {
                tag = TAG_ACTION_DOCK
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = GradientDrawable().apply {
                    setColor(modalBg)
                    cornerRadius = dpF(100f)
                    setStroke(dp(1), modalStroke)
                }
                elevation = dpF(4f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(64)
                ).apply {
                    topMargin = dp(16)
                }
            }

            val dockInnerTrack = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = GradientDrawable().apply {
                    setColor(subtleTrackBg)
                    cornerRadius = dpF(100f)
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val homeDockBtn = buildDockActionItem(
                tagName = TAG_HOME_ACTION_BTN,
                label = "Home",
                accessibilityDescription = "Return to Home in ${config.appName}",
                isHomeIcon = true,
                isElevatedPill = true,
                onClick = {
                    onDismissRequest()
                    onGoHome()
                }
            )

            val exitDockBtn = buildDockActionItem(
                tagName = TAG_EXIT_ACTION_BTN,
                label = "Exit",
                accessibilityDescription = "Exit and minimize ${config.appName}",
                isHomeIcon = false,
                isElevatedPill = false,
                onClick = {
                    onDismissRequest()
                    onExitApp()
                }
            )

            dockInnerTrack.addView(homeDockBtn)
            dockInnerTrack.addView(exitDockBtn)
            dockOuterFrame.addView(dockInnerTrack)
            container.addView(dockOuterFrame)

            // Row 2: Full-width Pause button (52dp pill matching OutloopPrimaryButton)
            val pauseBtnLabel = if (isLimitReached) {
                "Extend limit \u00B7 ${config.preferredPauseMinutes} min"
            } else {
                "Pause for ${config.preferredPauseMinutes} min"
            }
            val pauseBtn = Button(context).apply {
                tag = TAG_PAUSE_GUARD_BTN
                text = pauseBtnLabel
                contentDescription = "Open Pause Guard options for ${config.preferredPauseMinutes} minutes"
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
                    topMargin = dp(10)
                }
                minimumHeight = dp(52)
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
                setPadding(dp(20), dp(18), dp(20), dp(22))
                background = GradientDrawable().apply {
                    setColor(darkHeroBg)
                    cornerRadius = dpF(26f)
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
                    color = if (isDailyLimitPause) warnBg else sageAccent
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
                setPadding(dp(2), dp(16), dp(2), dp(6))
            })
            container.addView(TextView(context).apply {
                text = "Strict mode asks you to wait 10 seconds first. Still want a break?"
                textSize = 14.5f
                lineHeight = dp(21)
                typeface = bodyTypeface
                setTextColor(inkSecondary)
                setPadding(dp(2), 0, dp(2), dp(14))
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
                            setColor(subtleTrackBg)
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
                    onGoHome()
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
                                setColor(subtleTrackBg)
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
        val removeNow = {
            activeCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
            activeCountdownRunnable = null
            val view = overlayView
            if (view != null) {
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Exception) {
                    runCatching { windowManager.removeView(view) }
                } finally {
                    overlayView = null
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeNow()
        } else {
            mainHandler.post(removeNow)
        }
    }

    companion object {
        const val TAG_OVERLAY_ROOT = "outloop_dim_overlay_root"
        const val TAG_DIM_SCRIM = "outloop_dim_scrim"
        const val TAG_MODAL_CARD = "outloop_pause_guard_card"
        const val TAG_ACTION_DOCK = "outloop_overlay_action_dock"
        const val TAG_HOME_ACTION_BTN = "outloop_overlay_home_action"
        const val TAG_EXIT_ACTION_BTN = "outloop_overlay_exit_action"
        const val TAG_PAUSE_GUARD_BTN = "outloop_overlay_pause_guard"
    }
}
