package com.prirai.android.nira.videosearch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * UI-only surface for the queue preview. Search, validation, profile binding,
 * cancellation and persistence remain owned by MyVideoSitesActivity.
 */
internal object VideoSearchQueueDialogUi {
    internal data class Controls(
        val view: View,
        val phrase: EditText,
        val showPlan: Button,
        val cancel: Button,
        val start: Button,
    )

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun gradient(context: Context, bright: Boolean, radius: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (bright) intArrayOf(0xff00c9ee.toInt(), 0xff244ef2.toInt(), 0xffce12ef.toInt())
            else intArrayOf(0xff1c1736.toInt(), 0xff171329.toInt()),
        ).apply {
            cornerRadius = context.dp(radius).toFloat()
            setStroke(context.dp(1).coerceAtLeast(1),
                if (bright) 0xffd47bff.toInt() else 0xff68458f.toInt())
        }

    fun create(context: Context, initialPhrase: String): Controls {
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            tag = "video-queue-neon-scroll"
        }
        val backdrop = FrameLayout(context).apply {
            isFocusableInTouchMode = true
            tag = "video-queue-neon-dialog"
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xff0c2035.toInt(), 0xff1c1333.toInt(),
                    0xff28123b.toInt(), 0xff150e30.toInt()),
            ).apply {
                cornerRadius = context.dp(32).toFloat()
                setStroke(context.dp(2).coerceAtLeast(1), 0xffa76afa.toInt())
            }
        }
        backdrop.addView(QueueLightArcs(context), FrameLayout.LayoutParams(-1, -1))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(22))
        }
        val symbol = QueueStackMark(context).apply {
            contentDescription = "Wyszukiwanie na wybranych witrynach"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        body.addView(symbol, LinearLayout.LayoutParams(context.dp(106), context.dp(92)))
        val title = TextView(context).apply {
            text = "Podgląd kolejki witryn"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        body.addView(title, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(4)
            bottomMargin = context.dp(20)
        })
        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xff241d42.toInt(), 0xff2b1b42.toInt()),
            ).apply {
                cornerRadius = context.dp(16).toFloat()
                setStroke(context.dp(2).coerceAtLeast(1), 0xffa36af3.toInt())
            }
        }
        val glass = TextView(context).apply {
            text = "⌕"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(0xffc9b4ff.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        searchRow.addView(glass, LinearLayout.LayoutParams(context.dp(44), context.dp(54)))
        val field = EditText(context).apply {
            id = View.generateViewId()
            tag = "video-queue-phrase"
            hint = "Fraza do wyszukania na wybranych witrynach"
            setText(initialPhrase)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xffbcb2d3.toInt())
            setPadding(0, 0, context.dp(12), 0)
            background = null
            isSaveEnabled = false
            contentDescription = "Fraza do wyszukiwania na wybranych witrynach"
        }
        searchRow.addView(field, LinearLayout.LayoutParams(0, context.dp(54), 1f))
        body.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = context.dp(24)
        })

        fun action(text: String, main: Boolean): Button =
            Button(context).apply {
                this.text = text
                isAllCaps = false
                textSize = if (main) 15f else 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minHeight = context.dp(if (main) 56 else 46)
                minimumHeight = minHeight
                setPadding(context.dp(10), 0, context.dp(10), 0)
                background = gradient(context, main, if (main) 28 else 24)
            }
        val preview = action("▦  Pokaż plan", false).apply {
            tag = "video-queue-show-plan"
            contentDescription = "Pokaż plan wyszukiwania bez uruchamiania"
        }
        val cancel = action("×  Anuluj", false).apply {
            tag = "video-queue-cancel"
        }
        val execute = action("▷  Uruchom wyszukiwanie", true).apply {
            tag = "video-queue-start"
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, context.dp(46)).apply {
            marginStart = context.dp(38)
            marginEnd = context.dp(38)
            bottomMargin = context.dp(9)
        })
        body.addView(cancel, LinearLayout.LayoutParams(-1, context.dp(46)).apply {
            marginStart = context.dp(38)
            marginEnd = context.dp(38)
            bottomMargin = context.dp(18)
        })
        body.addView(execute, LinearLayout.LayoutParams(-1, context.dp(56)).apply {
            marginStart = context.dp(8)
            marginEnd = context.dp(8)
        })
        backdrop.addView(body, FrameLayout.LayoutParams(-1, -2))
        scroll.addView(backdrop, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Controls(scroll, field, preview, cancel, execute)
    }

    /** Simple clipped cyan/violet arcs behind the content, no external image. */
    private class QueueLightArcs(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val clip = Path().apply {
                addRoundRect(0f, 0f, w, h, context.dp(30).toFloat(),
                    context.dp(30).toFloat(), Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clip)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, w * .55f, h * .3f,
                0xff06cff0.toInt(), 0x0006cff0, Shader.TileMode.CLAMP)
            canvas.drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(w * .62f, 0f)
                quadTo(w * .12f, h * .04f, 0f, h * .29f); close()
            }, paint)
            paint.shader = LinearGradient(w, h, w * .58f, h * .7f,
                0xff9439fa.toInt(), 0x009439fa, Shader.TileMode.CLAMP)
            canvas.drawPath(Path().apply {
                moveTo(w, h * .78f); lineTo(w, h)
                lineTo(w * .42f, h)
                quadTo(w * .87f, h * .96f, w, h * .78f); close()
            }, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(1).toFloat()
            paint.color = 0xff64eaff.toInt()
            canvas.drawPath(Path().apply {
                moveTo(0f, h * .29f)
                quadTo(w * .12f, h * .04f, w * .62f, 0f)
            }, paint)
            paint.color = 0xffc071ff.toInt()
            canvas.drawPath(Path().apply {
                moveTo(w * .42f, h)
                quadTo(w * .87f, h * .96f, w, h * .78f)
            }, paint)
            canvas.restore()
        }
    }

    /** Compact custom icon: three neon rhombi and a magnifier. */
    private class QueueStackMark(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val sx = width / 106f
            val sy = height / 92f
            canvas.save()
            canvas.scale(sx, sy)
            for (layer in 2 downTo 0) {
                val cy = 38f + layer * 12f
                val shape = Path().apply {
                    moveTo(53f, cy - 26f)
                    quadTo(57f, cy - 26f, 63f, cy - 22f)
                    lineTo(91f, cy - 7f)
                    quadTo(97f, cy - 3f, 91f, cy + 2f)
                    lineTo(60f, cy + 22f)
                    quadTo(53f, cy + 26f, 46f, cy + 22f)
                    lineTo(15f, cy + 2f)
                    quadTo(9f, cy - 3f, 15f, cy - 7f)
                    close()
                }
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(9f, cy, 97f, cy,
                    0xff00d9f5.toInt(), 0xffaf17f2.toInt(), Shader.TileMode.CLAMP)
                canvas.drawPath(shape, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = 0xffd7b2ff.toInt()
                canvas.drawPath(shape, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.2f
            paint.color = Color.WHITE
            canvas.drawCircle(52f, 34f, 10f, paint)
            canvas.drawLine(59.5f, 41.5f, 69f, 51f, paint)
            canvas.restore()
        }
    }
}
