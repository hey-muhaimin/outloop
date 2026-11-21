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

class AccessibilityOverlayShield(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        appName: String,
        feedLabel: String,
        useDarkTheme: Boolean = false,
        hapticsEnabled: Boolean = true,
        onExitFeed: () -> Unit,
        onAllowOnce: () -> Unit,
        onSnooze15Minutes: () -> Unit = {},
        onAttachmentFailed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (overlayView != null) return@post

            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val cardBg = if (useDarkTheme) Color.parseColor("#19211B") else Color.parseColor("#FAF9F5")
            val cardStroke = if (useDarkTheme) Color.parseColor("#1EF1F0E9") else Color.parseColor("#1A1C231D")
            val markColor = if (useDarkTheme) Color.parseColor("#A8B3A0") else Color.parseColor("#29362E")
            val primaryBtnBg = if (useDarkTheme) Color.parseColor("#A8B3A0") else Color.parseColor("#29362E")
            val primaryBtnText = if (useDarkTheme) Color.parseColor("#16201A") else Color.parseColor("#FAF9F5")
            val secondaryBtnBg = if (useDarkTheme) Color.parseColor("#202A23") else Color.parseColor("#ECE9DF")
            val secondaryBtnStroke = if (useDarkTheme) Color.parseColor("#1EF1F0E9") else Color.parseColor("#1A1C231D")
            val headlineColor = if (useDarkTheme) Color.parseColor("#F1F0E9") else Color.parseColor("#1C231D")
            val secondaryText = if (useDarkTheme) Color.parseColor("#B7C0B5") else Color.parseColor("#4A544C")
            val subtleTint = if (useDarkTheme) Color.parseColor("#A8B3A0") else Color.parseColor("#435A4A")

            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#880A0E0B"))
                setPadding(dp(20), dp(20), dp(20), dp(20))
                isFocusable = true
                isFocusableInTouchMode = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "Take a moment. Outloop paused $appName $feedLabel."
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    accessibilityPaneTitle = "Outloop Mindful Pause"
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
                setPadding(dp(20), dp(20), dp(20), dp(16))
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = dp(24).toFloat()
                    setStroke(dp(1), cardStroke)
                }
            }

            val markView = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    color = markColor
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val strokeW = dp(4).toFloat()
                    paint.strokeWidth = strokeW
                    val inset = strokeW / 2f + dp(3)
                    val rect = RectF(inset, inset, width - inset, height - inset)
                    // Matching Outloop open-loop arc (-27 deg start sweeping -324 deg)
                    canvas.drawArc(rect, -27f, -324f, false, paint)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            val contextLabel = TextView(context).apply {
                text = "$appName \u00B7 $feedLabel"
                textSize = 12f
                setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(subtleTint)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }

            val title = TextView(context).apply {
                text = "Take a moment."
                textSize = 22f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(headlineColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(3))
                isFocusable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }

            val body = TextView(context).apply {
                text = "Is this what you came here for?"
                textSize = 14.5f
                setTextColor(secondaryText)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            }

            val exitButton = Button(context).apply {
                text = "Return to my intention"
                contentDescription = "Return to my intention and leave $appName $feedLabel"
                isAllCaps = false
                textSize = 14.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(primaryBtnText)
                setPadding(dp(16), dp(11), dp(16), dp(11))
                background = GradientDrawable().apply {
                    setColor(primaryBtnBg)
                    cornerRadius = dp(100).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = dp(48)
                minHeight = dp(48)
                setOnClickListener { view ->
                    if (hapticsEnabled) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    dismiss()
                    onExitFeed()
                }
            }

            val allowButton = Button(context).apply {
                text = "Allow once"
                contentDescription = "Allow $appName $feedLabel once for 5 minutes"
                isAllCaps = false
                textSize = 14.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(headlineColor)
                setPadding(dp(16), dp(11), dp(16), dp(11))
                background = GradientDrawable().apply {
                    setColor(secondaryBtnBg)
                    cornerRadius = dp(100).toFloat()
                    setStroke(dp(1), secondaryBtnStroke)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = dp(48)
                minHeight = dp(48)
                setOnClickListener { view ->
                    if (hapticsEnabled) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    dismiss()
                    onAllowOnce()
                }
            }

            val snoozeButton = Button(context).apply {
                text = "Snooze for 15 minutes"
                contentDescription = "Snooze protection for $appName for 15 minutes"
                isAllCaps = false
                textSize = 14f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(subtleTint)
                background = null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = dp(48)
                minHeight = dp(48)
                setOnClickListener { view ->
                    if (hapticsEnabled) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    dismiss()
                    onSnooze15Minutes()
                }
            }

            val spacer1 = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1, dp(8))
            }
            val spacer2 = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1, dp(2))
            }

            card.addView(markView)
            card.addView(contextLabel)
            card.addView(title)
            card.addView(body)
            card.addView(exitButton)
            card.addView(spacer1)
            card.addView(allowButton)
            card.addView(spacer2)
            card.addView(snoozeButton)

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
                card.alpha = 0f
                card.scaleX = 0.94f
                card.scaleY = 0.94f
                windowManager.addView(root, layoutParams)
                overlayView = root
                card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                root.requestFocus()
                title.post {
                    title.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                }
            } catch (_: Exception) {
                overlayView = null
                onAttachmentFailed()
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
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
