package com.example.batterypercent

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/** Common base: immersive black full-screen window that shows a [ChargeView] and controls window brightness. */
abstract class FullScreenActivity : Activity() {

    protected lateinit var chargeView: ChargeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Let the picture extend under the camera hole so the blue cap can sit right below it.
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        chargeView = ChargeView(this)
        setContentView(chargeView)
    }

    protected fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    companion object {
        /** Window brightness while the screen is "lit" (widget tap, every 10%, 100%). */
        const val FULL_BRIGHTNESS = 1.0f

        /**
         * Window brightness the rest of the time: 0.0f = BRIGHTNESS_OVERRIDE_OFF, the lowest value the
         * system allows. If it is too dark to read on your phone, raise it (e.g. 0.02f).
         */
        const val DIM_BRIGHTNESS = 0.0f

        /** How long the full-brightness phase lasts while charging. */
        const val FULL_HOLD_MS = 10_000L

        /** How long the widget-tap preview stays on screen. */
        const val PEEK_MS = 3_000L
    }
}
