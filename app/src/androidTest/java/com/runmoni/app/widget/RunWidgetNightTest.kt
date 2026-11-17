package com.runmoni.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real RemoteViews inflation with a scoped configuration; never sends a control
 * or changes OS settings, accounts, the widget cache or the recorder. */
@RunWith(AndroidJUnit4::class)
class RunWidgetNightTest {
    @Test fun dayAndNightResourcesKeepReadableTextAndActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (night in listOf(false, true)) {
            val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            })
            val surface = display.getColor(R.color.run_widget_surface)
            assertEquals(if (night) 0xFF1D2E36.toInt() else 0xFFFFFFFF.toInt(), surface)
            for (role in listOf(R.color.run_widget_text, R.color.run_widget_muted, R.color.run_widget_primary))
                assertTrue("Widget text contrast night=$night role=$role",
                    ColorUtils.calculateContrast(display.getColor(role), surface) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(display.getColor(R.color.run_widget_on_primary),
                display.getColor(R.color.run_widget_primary)) >= 4.5)
        }
    }

    @Test fun nightIdleRemoteViewsUsesNightInkAndKeepsItsOpenActionAtFontTwo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            fontScale = 2f
        })
        var bitmap: Bitmap? = null
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val density = display.resources.displayMetrics.density
                val width = (256 * density).toInt()
                val height = (320 * density).toInt()
                val root = RunWidgetViews.forSize(display, null, false, 256, 320)
                    .apply(display, FrameLayout(display)) as ViewGroup
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                val brand = root.findViewById<TextView>(R.id.run_widget_brand)
                assertEquals(display.getColor(R.color.run_widget_text), brand.currentTextColor)
                assertEquals("runmoni", brand.text.toString())
                val open = root.findViewById<View>(R.id.run_widget_open)
                assertTrue(open.isClickable)
                assertTrue(open.height >= 48 * density - 1)
                assertTrue(open.width >= 48 * density - 1)
                assertEquals("런모니 앱 열기", open.contentDescription)
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
            }
            val target = File(context.cacheDir, "widget-review/widget-night-idle-font2-native-fixture.png")
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            target.outputStream().use { assertTrue(requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap?.recycle() }
    }
}
