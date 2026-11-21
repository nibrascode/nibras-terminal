package com.nibras.terminal

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Compact Android layout preview (the ACS/AndroidIDE layout designer, in small): parses a layout
 * XML and builds real views for the common widgets, resolving @string / @color / @dimen / @drawable
 * from the project's res/ folder. ConstraintLayout and RelativeLayout are shown as stacks.
 */
object LayoutPreview {
    private class Res(val strings: Map<String, String>, val colors: Map<String, String>, val dimens: Map<String, String>, val res: File?)

    private fun values(res: File?, tag: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        res?.listFiles { f -> f.name == "values" || f.name.startsWith("values-") && f.name == "values" }?.forEach { dir ->
            dir.listFiles { f -> f.extension == "xml" }?.forEach { f ->
                runCatching {
                    val p = Xml.newPullParser(); f.reader().use { r -> p.setInput(r)
                        var name: String? = null
                        while (p.next() != XmlPullParser.END_DOCUMENT) {
                            if (p.eventType == XmlPullParser.START_TAG && p.name == tag) name = p.getAttributeValue(null, "name")
                            else if (p.eventType == XmlPullParser.TEXT && name != null) { out[name] = p.text.trim(); name = null }
                        }
                    }
                }
            }
        }
        return out
    }

    fun render(context: Context, xml: String, layoutDir: File?): View {
        val resDir = layoutDir?.parentFile?.takeIf { it.name == "res" }
        val res = Res(values(resDir, "string"), values(resDir, "color"), values(resDir, "dimen"), resDir)
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val stack = ArrayDeque<ViewGroup>()
        var root: View? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    val a = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
                    val v = make(context, p.name, a, res)
                    v.layoutParams = params(context, stack.lastOrNull(), a, res)
                    stack.lastOrNull()?.addView(v) ?: run { root = v }
                    if (v is ViewGroup && v !is Spinner) stack.addLast(v) else skipChildren(p)
                }
                XmlPullParser.END_TAG -> if (stack.isNotEmpty() && p.depth - 1 == stack.size - 1) stack.removeLast()
            }
        }
        return root ?: throw IllegalArgumentException("empty layout")
    }

    private fun skipChildren(p: XmlPullParser) {
        var depth = 1
        while (depth > 0 && p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) depth++ else if (p.eventType == XmlPullParser.END_TAG) depth--
        }
    }

    private fun dp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    private fun size(c: Context, v: String?, res: Res): Int? {
        val s = v?.let { if (it.startsWith("@dimen/")) res.dimens[it.removePrefix("@dimen/")] else it } ?: return null
        return when {
            s == "match_parent" || s == "fill_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
            s == "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            s == "0dp" -> 0
            s.endsWith("dp") || s.endsWith("dip") -> dp(c, s.removeSuffix("dip").removeSuffix("dp").toFloat())
            s.endsWith("sp") -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, s.removeSuffix("sp").toFloat(), c.resources.displayMetrics).toInt()
            s.endsWith("px") -> s.removeSuffix("px").toFloat().toInt()
            else -> s.toFloatOrNull()?.toInt()
        }
    }

    private fun color(v: String?, res: Res): Int? {
        val s = v?.let { if (it.startsWith("@color/")) res.colors[it.removePrefix("@color/")] else if (it.startsWith("@android:color/")) it.removePrefix("@android:color/").replace("_", "") else it } ?: return null
        return runCatching { Color.parseColor(if (s.startsWith("#") && s.length == 4) "#" + s.drop(1).map { "$it$it" }.joinToString("") else s) }.getOrNull()
    }

    private fun text(v: String?, res: Res) = v?.let { if (it.startsWith("@string/")) res.strings[it.removePrefix("@string/")] ?: it else it.replace("\\n", "\n") }

    private fun gravity(v: String?): Int? = v?.split('|')?.map {
        when (it.trim()) { "center" -> Gravity.CENTER; "center_horizontal" -> Gravity.CENTER_HORIZONTAL; "center_vertical" -> Gravity.CENTER_VERTICAL
            "start", "left" -> Gravity.START; "end", "right" -> Gravity.END; "top" -> Gravity.TOP; "bottom" -> Gravity.BOTTOM; else -> 0 }
    }?.fold(0) { x, y -> x or y }

    private fun make(c: Context, tag: String, a: Map<String, String>, res: Res): View {
        val name = tag.substringAfterLast('.')
        val v: View = when (name) {
            "LinearLayout", "LinearLayoutCompat", "RadioGroup", "TableLayout", "TableRow" -> LinearLayout(c).apply {
                orientation = if (a["android:orientation"] == "horizontal" || name == "TableRow") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                a["android:weightSum"]?.toFloatOrNull()?.let { weightSum = it }
                gravity(a["android:gravity"])?.let { gravity = it }
            }
            "ConstraintLayout", "RelativeLayout", "CoordinatorLayout", "AppBarLayout", "MotionLayout" ->
                LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity(a["android:gravity"])?.let { gravity = it } }
            "FrameLayout", "CardView", "MaterialCardView", "FragmentContainerView", "DrawerLayout", "SwipeRefreshLayout", "NestedScrollView", "ScrollView" -> FrameLayout(c).apply {
                if (name.contains("Card")) background = GradientDrawable().apply { cornerRadius = dp(c, 12f).toFloat(); setColor(color(a["app:cardBackgroundColor"], res) ?: 0xFFF5F5F5.toInt()); setStroke(1, 0x22000000) }
            }
            "HorizontalScrollView" -> HorizontalScrollView(c)
            "Button", "MaterialButton", "AppCompatButton", "ToggleButton" -> Button(c)
            "EditText", "TextInputEditText", "AppCompatEditText", "AutoCompleteTextView" -> EditText(c).apply { a["android:hint"]?.let { hint = text(it, res) } }
            "TextInputLayout" -> LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            "CheckBox", "MaterialCheckBox" -> CheckBox(c).apply { isChecked = a["android:checked"] == "true" }
            "RadioButton", "MaterialRadioButton" -> RadioButton(c).apply { isChecked = a["android:checked"] == "true" }
            "Switch", "SwitchCompat", "SwitchMaterial", "MaterialSwitch" -> Switch(c).apply { isChecked = a["android:checked"] == "true" }
            "ProgressBar", "CircularProgressIndicator", "LinearProgressIndicator" -> ProgressBar(c, null, if (a["style"]?.contains("Horizontal") == true || name.startsWith("Linear")) android.R.attr.progressBarStyleHorizontal else android.R.attr.progressBarStyle).apply { progress = 40 }
            "SeekBar", "Slider" -> SeekBar(c).apply { progress = 40 }
            "RatingBar" -> RatingBar(c).apply { rating = 3.5f }
            "ImageView", "ImageButton", "AppCompatImageView", "ShapeableImageView", "FloatingActionButton" -> ImageView(c).apply {
                val src = a["android:src"] ?: a["app:srcCompat"]
                val bmp = src?.takeIf { it.startsWith("@drawable/") || it.startsWith("@mipmap/") }?.let { s ->
                    val n = s.substringAfter('/')
                    res.res?.listFiles { f -> f.name.startsWith("drawable") || f.name.startsWith("mipmap") }?.flatMap { it.listFiles()?.toList().orEmpty() }
                        ?.firstOrNull { it.nameWithoutExtension == n && it.extension in setOf("png", "jpg", "jpeg", "webp") }?.let { BitmapFactory.decodeFile(it.absolutePath) }
                }
                if (bmp != null) setImageBitmap(bmp) else { setImageResource(android.R.drawable.ic_menu_gallery); setBackgroundColor(0x11000000) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                minimumHeight = dp(c, 48f); minimumWidth = dp(c, 48f)
            }
            "RecyclerView", "ListView", "GridView", "ViewPager", "ViewPager2", "WebView", "MapView", "include", "fragment", "TabLayout", "BottomNavigationView", "Toolbar", "MaterialToolbar" ->
                TextView(c).apply {
                    text = "⟨$name${a["layout"]?.let { " $it" } ?: ""}${a["android:id"]?.let { " " + it.substringAfter('/') } ?: ""}⟩"
                    gravity = Gravity.CENTER; setTextColor(0xFF555555.toInt()); minHeight = dp(c, if (name.contains("Toolbar") || name.contains("Tab") || name.contains("Navigation")) 56f else 120f)
                    background = GradientDrawable().apply { setColor(if (name.contains("Toolbar")) 0xFF6750A4.toInt() else 0xFFEDEDED.toInt()); setStroke(2, 0x33000000) }
                    if (name.contains("Toolbar")) { setTextColor(Color.WHITE); text = text(a["app:title"], res) ?: text }
                }
            "View", "Space", "Divider", "MaterialDivider" -> View(c)
            "Spinner" -> TextView(c).apply { text = "▾ Spinner"; setPadding(dp(c, 8f), dp(c, 8f), dp(c, 8f), dp(c, 8f)) }
            "merge" -> FrameLayout(c)
            else -> if (a.keys.any { it == "android:text" }) TextView(c) else FrameLayout(c)
        }
        if (v is TextView && v !is EditText || v is Button || v is CheckBox) (v as TextView).apply {
            text(a["android:text"], res)?.let { text = it }
            a["android:textSize"]?.let { size(c, it, res)?.let { px -> setTextSize(TypedValue.COMPLEX_UNIT_PX, px.toFloat()) } }
            color(a["android:textColor"], res)?.let { setTextColor(it) }
            if (a["android:textStyle"]?.contains("bold") == true) setTypeface(typeface, Typeface.BOLD)
            if (v !is Button && a["android:gravity"] != null) gravity(a["android:gravity"])?.let { gravity = it }
            a["android:maxLines"]?.toIntOrNull()?.let { maxLines = it }
        }
        if (v is EditText) { text(a["android:text"], res)?.let { v.setText(it) } }
        color(a["android:background"], res)?.let { v.setBackgroundColor(it) }
        if (name in setOf("View", "Divider", "MaterialDivider") && a["android:background"] == null) v.setBackgroundColor(0x22000000)
        val pad = size(c, a["android:padding"], res)
        v.setPadding(size(c, a["android:paddingStart"] ?: a["android:paddingLeft"] ?: a["android:paddingHorizontal"], res) ?: pad ?: v.paddingLeft,
            size(c, a["android:paddingTop"] ?: a["android:paddingVertical"], res) ?: pad ?: v.paddingTop,
            size(c, a["android:paddingEnd"] ?: a["android:paddingRight"] ?: a["android:paddingHorizontal"], res) ?: pad ?: v.paddingRight,
            size(c, a["android:paddingBottom"] ?: a["android:paddingVertical"], res) ?: pad ?: v.paddingBottom)
        when (a["android:visibility"]) { "gone" -> v.visibility = View.GONE; "invisible" -> v.visibility = View.INVISIBLE }
        a["android:alpha"]?.toFloatOrNull()?.let { v.alpha = it }
        return v
    }

    private fun params(c: Context, parent: ViewGroup?, a: Map<String, String>, res: Res): ViewGroup.LayoutParams {
        val w = size(c, a["android:layout_width"], res) ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val h = size(c, a["android:layout_height"], res) ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val lp: ViewGroup.MarginLayoutParams = when (parent) {
            is LinearLayout -> LinearLayout.LayoutParams(w, h).apply {
                a["android:layout_weight"]?.toFloatOrNull()?.let { weight = it }
                gravity(a["android:layout_gravity"])?.let { gravity = it }
                // ConstraintLayout shown as a stack: 0dp means "fill the constraint".
                if (w == 0 && weight == 0f) width = ViewGroup.LayoutParams.MATCH_PARENT
                if (h == 0 && weight == 0f) height = ViewGroup.LayoutParams.WRAP_CONTENT
                if (a.keys.any { it.startsWith("app:layout_constraint") } && a["app:layout_constraintStart_toStartOf"] != null && a["app:layout_constraintEnd_toEndOf"] != null) gravity = Gravity.CENTER_HORIZONTAL
            }
            is FrameLayout -> FrameLayout.LayoutParams(w, h).apply { gravity(a["android:layout_gravity"])?.let { gravity = it } }
            else -> ViewGroup.MarginLayoutParams(w, h)
        }
        val m = size(c, a["android:layout_margin"], res)
        lp.setMargins(size(c, a["android:layout_marginStart"] ?: a["android:layout_marginLeft"] ?: a["android:layout_marginHorizontal"], res) ?: m ?: 0,
            size(c, a["android:layout_marginTop"] ?: a["android:layout_marginVertical"], res) ?: m ?: 0,
            size(c, a["android:layout_marginEnd"] ?: a["android:layout_marginRight"] ?: a["android:layout_marginHorizontal"], res) ?: m ?: 0,
            size(c, a["android:layout_marginBottom"] ?: a["android:layout_marginVertical"], res) ?: m ?: 0)
        return lp
    }
}
