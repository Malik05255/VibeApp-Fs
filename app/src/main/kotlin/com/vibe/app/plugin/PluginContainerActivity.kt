package com.vibe.app.plugin

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.tencent.shadow.core.runtime.HostActivityDelegator
import com.tencent.shadow.core.runtime.ShadowActivity
import java.io.File
import kotlin.math.max

/**
 * Base proxy Activity that hosts a plugin.
 *
 * Preview edit mode is intentionally implemented as a tiny overlay above the already-rendered
 * plugin UI. It is dormant unless EXTRA_PREVIEW_EDIT_MODE is true, so normal chat/app runtime
 * pays virtually no cost. Long-press selects a view; drag moves it; the bottom-right handle
 * resizes it. Changes are exported as lightweight JSON deltas for the host to patch into source.
 */
open class PluginContainerActivity :
    AppCompatActivity(),
    HostActivityDelegator {

    private var pluginActivity: ShadowActivity? = null
    private var pluginResources: Resources? = null
    private var pluginClassLoader: ClassLoader? = null
    private var pluginLayoutInflater: LayoutInflater? = null
    private var pluginTheme: Resources.Theme? = null

    private var projectId: String? = null
    private var slotIndex: Int = -1
    private var previewEditMode: Boolean = false
    private var editOverlay: PreviewEditOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val mainClass = intent.getStringExtra(EXTRA_MAIN_CLASS)
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        previewEditMode = intent.getBooleanExtra(EXTRA_PREVIEW_EDIT_MODE, false)
        slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)

        if (slotIndex >= 0) {
            ActivityHolder.set(slotIndex, this)
        }

        if (apkPath == null || mainClass == null) {
            writeErrorLog("Missing apkPath or mainClass")
            finish()
            return
        }

        try {
            pluginClassLoader = PluginResourceLoader.createPluginClassLoader(
                context = this,
                apkPath = apkPath,
                parentClassLoader = ShadowActivity::class.java.classLoader!!
            )

            pluginResources = PluginResourceLoader.loadPluginResources(this, apkPath)
            pluginTheme = pluginResources!!.newTheme()

            val pluginContext = object : android.content.ContextWrapper(this) {
                override fun getResources(): Resources = pluginResources!!
                override fun getClassLoader(): ClassLoader = pluginClassLoader!!
                override fun getTheme(): Resources.Theme = pluginTheme!!
            }

            pluginLayoutInflater = layoutInflater.cloneInContext(pluginContext)
            val clazz = pluginClassLoader!!.loadClass(mainClass)
            val instance = clazz.getDeclaredConstructor().newInstance()

            if (instance is ShadowActivity) {
                pluginActivity = instance
                instance.setHostDelegator(this)
                instance.performCreate(savedInstanceState)
                if (previewEditMode) installPreviewEditOverlay()
            } else {
                writeErrorLog("$mainClass is not ShadowActivity")
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Plugin loading failed", e)
            writeCrashLog(e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        pluginActivity?.performResume()
    }

    override fun onPause() {
        pluginActivity?.performPause()
        super.onPause()
    }

    override fun onStop() {
        pluginActivity?.performStop()
        super.onStop()
    }

    override fun onDestroy() {
        try {
            pluginActivity?.performDestroy()
        } catch (e: Exception) {
            writeCrashLog(e)
        }
        editOverlay?.detach()
        editOverlay = null
        if (slotIndex >= 0) ActivityHolder.clear(slotIndex)
        pluginActivity = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        pluginActivity?.performActivityResult(requestCode, resultCode, data)
    }

    override fun getHostContext(): Context = this
    override fun getHostResources(): Resources = pluginResources ?: resources
    override fun getHostTheme(): Resources.Theme = pluginTheme ?: theme
    override fun getHostLayoutInflater(): LayoutInflater = pluginLayoutInflater ?: layoutInflater
    override fun getHostWindow(): Window = window
    override fun getHostWindowManager(): WindowManager = windowManager
    override fun getPluginClassLoader(): ClassLoader = pluginClassLoader ?: classLoader

    override fun superSetContentView(layoutResID: Int) {
        val view = layoutInflater.inflate(layoutResID, null)
        super.setContentView(view)
        if (previewEditMode) installPreviewEditOverlay()
    }

    override fun superSetContentView(view: View) {
        super.setContentView(view)
        if (previewEditMode) installPreviewEditOverlay()
    }

    override fun <T : View> superFindViewById(id: Int): T = findViewById(id)
    override fun superStartActivity(intent: Intent) = super.startActivity(intent)
    override fun superStartActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) =
        super.startActivityForResult(intent, requestCode, options)
    override fun superFinish() = super.finish()
    override fun setPluginResult(resultCode: Int, data: Intent?) = setResult(resultCode, data)
    override fun getHostIntent(): Intent = intent

    private fun installPreviewEditOverlay() {
        val decor = window.decorView as? ViewGroup ?: return
        if (editOverlay != null) return
        editOverlay = PreviewEditOverlay(this, decor, projectId).also { it.attach() }
    }

    private fun writeCrashLog(throwable: Throwable) {
        writeErrorLog(Log.getStackTraceString(throwable))
    }

    private fun writeErrorLog(message: String) {
        val id = projectId ?: return
        try {
            val dir = File(filesDir, "projects/$id/logs")
            dir.mkdirs()
            File(dir, "crash.log").appendText(message + "\n")
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "PluginContainer"
        const val EXTRA_APK_PATH = "plugin_apk_path"
        const val EXTRA_MAIN_CLASS = "plugin_main_class"
        const val EXTRA_PLUGIN_LABEL = "plugin_label"
        const val EXTRA_SLOT_INDEX = "plugin_slot_index"
        const val EXTRA_PROJECT_ID = "plugin_project_id"
        const val EXTRA_PREVIEW_EDIT_MODE = "plugin_preview_edit_mode"
    }
}

/**
 * Zero-idle-cost visual editor: no polling, no layout traversal loop, no background work.
 * It wakes only on touch while preview mode is active.
 */
private class PreviewEditOverlay(
    private val activity: PluginContainerActivity,
    private val root: ViewGroup,
    private val projectId: String?,
) {
    private val overlay = FrameLayout(activity)
    private val selection = View(activity)
    private val resizeHandle = View(activity)
    private var selected: View? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var startWidth = 0
    private var startHeight = 0
    private var resizing = false

    fun attach() {
        overlay.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        overlay.isClickable = true
        overlay.isFocusable = false
        overlay.setBackgroundColor(Color.TRANSPARENT)
        selection.background = borderDrawable()
        selection.visibility = View.GONE
        resizeHandle.setBackgroundColor(Color.WHITE)
        resizeHandle.visibility = View.GONE
        overlay.addView(selection)
        overlay.addView(resizeHandle, FrameLayout.LayoutParams(dp(18), dp(18)))
        overlay.setOnTouchListener { _, event -> handleTouch(event) }
        root.addView(overlay)
    }

    fun detach() {
        try { root.removeView(overlay) } catch (_: Exception) { }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                resizing = hitResizeHandle(event.x, event.y)
                if (!resizing) {
                    selected = findEditableViewAt(root, event.rawX.toInt(), event.rawY.toInt())
                    selected?.let { showSelection(it) }
                }
                selected?.let {
                    startX = it.translationX
                    startY = it.translationY
                    startWidth = max(1, it.width)
                    startHeight = max(1, it.height)
                }
                return selected != null
            }
            MotionEvent.ACTION_MOVE -> {
                val target = selected ?: return false
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (resizing) {
                    val lp = target.layoutParams
                    lp.width = max(dp(24), (startWidth + dx).toInt())
                    lp.height = max(dp(24), (startHeight + dy).toInt())
                    target.layoutParams = lp
                } else {
                    target.translationX = startX + dx
                    target.translationY = startY + dy
                }
                showSelection(target)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selected?.let { persistDelta(it) }
                resizing = false
                return selected != null
            }
        }
        return false
    }

    private fun findEditableViewAt(group: ViewGroup, rawX: Int, rawY: Int): View? {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child === overlay || child === selection || child === resizeHandle || child.visibility != View.VISIBLE) continue
            val loc = IntArray(2)
            child.getLocationOnScreen(loc)
            val inside = rawX in loc[0]..(loc[0] + child.width) && rawY in loc[1]..(loc[1] + child.height)
            if (!inside) continue
            if (child is ViewGroup) {
                val nested = findEditableViewAt(child, rawX, rawY)
                if (nested != null) return nested
            }
            return child
        }
        return null
    }

    private fun showSelection(view: View) {
        val loc = IntArray(2)
        val rootLoc = IntArray(2)
        view.getLocationOnScreen(loc)
        root.getLocationOnScreen(rootLoc)
        val left = loc[0] - rootLoc[0]
        val top = loc[1] - rootLoc[1]
        selection.layoutParams = FrameLayout.LayoutParams(max(1, view.width), max(1, view.height)).apply {
            leftMargin = left
            topMargin = top
        }
        selection.visibility = View.VISIBLE
        resizeHandle.layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.START).apply {
            leftMargin = left + max(1, view.width) - dp(9)
            topMargin = top + max(1, view.height) - dp(9)
        }
        resizeHandle.visibility = View.VISIBLE
    }

    private fun hitResizeHandle(x: Float, y: Float): Boolean {
        if (resizeHandle.visibility != View.VISIBLE) return false
        val lp = resizeHandle.layoutParams as FrameLayout.LayoutParams
        return x >= lp.leftMargin - dp(12) && x <= lp.leftMargin + dp(30) &&
            y >= lp.topMargin - dp(12) && y <= lp.topMargin + dp(30)
    }

    private fun persistDelta(view: View) {
        val id = projectId ?: return
        try {
            val dir = File(activity.filesDir, "projects/$id/preview-edits").apply { mkdirs() }
            val stableId = runCatching {
                if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else null
            }.getOrNull() ?: "${view.javaClass.simpleName}_${System.identityHashCode(view)}"
            val json = """{"id":"${escape(stableId)}","class":"${escape(view.javaClass.name)}","translationX":${view.translationX},"translationY":${view.translationY},"width":${view.width},"height":${view.height},"timestamp":${System.currentTimeMillis()}}"""
            File(dir, "latest.jsonl").appendText(json + "\n")
        } catch (e: Exception) {
            Log.w("PreviewEdit", "Unable to persist preview delta", e)
        }
    }

    private fun borderDrawable() = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(dp(2), Color.WHITE)
        cornerRadius = dp(8).toFloat()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

class PluginSlot0 : PluginContainerActivity()
class PluginSlot1 : PluginContainerActivity()
class PluginSlot2 : PluginContainerActivity()
class PluginSlot3 : PluginContainerActivity()
class PluginSlot4 : PluginContainerActivity()
