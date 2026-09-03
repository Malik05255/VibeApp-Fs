package com.vibe.app.plugin

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.tencent.shadow.core.runtime.HostActivityDelegator
import com.tencent.shadow.core.runtime.ShadowActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Base proxy Activity that hosts a plugin.
 */
open class PluginContainerActivity : AppCompatActivity(), HostActivityDelegator {

    private var pluginActivity: ShadowActivity? = null
    private var pluginResources: Resources? = null
    private var pluginClassLoader: ClassLoader? = null
    private var pluginLayoutInflater: LayoutInflater? = null
    private var pluginTheme: Resources.Theme? = null
    private var pluginContext: Context? = null

    private var projectId: String? = null
    private var slotIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ compatible back handling
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
        val pluginLabel = intent.getStringExtra(EXTRA_PLUGIN_LABEL)

        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)

        if (slotIndex >= 0) {
            ActivityHolder.set(slotIndex, this)
        }

        if (apkPath == null || mainClass == null) {
            Log.e(TAG, "Missing apkPath or mainClass in intent")
            writeErrorLog("Missing apkPath or mainClass in intent")
            finish()
            return
        }

        if (pluginLabel != null) {
            setTaskDescription(
                android.app.ActivityManager.TaskDescription(pluginLabel)
            )
        }

        try {
            pluginClassLoader =
                PluginResourceLoader.createPluginClassLoader(
                    context = this,
                    apkPath = apkPath,
                    parentClassLoader = ShadowActivity::class.java.classLoader!!
                )

            pluginResources =
                PluginResourceLoader.loadPluginResources(this, apkPath)

            pluginTheme = pluginResources!!.newTheme()

            val pluginPackage = mainClass.substringBeforeLast('.')

            val themeResId = pluginResources!!.getIdentifier(
                "Theme.MyApplication",
                "style",
                pluginPackage
            )

            if (themeResId != 0) {
                pluginTheme!!.applyStyle(themeResId, true)
                syncWindowWithPluginTheme(pluginTheme!!)
            }

            val pCtx = object : android.content.ContextWrapper(this) {

                override fun getResources(): Resources =
                    pluginResources!!

                override fun getClassLoader(): ClassLoader =
                    pluginClassLoader!!

                override fun getTheme(): Resources.Theme =
                    pluginTheme!!

                override fun getSystemService(name: String): Any? {
                    if (name == LAYOUT_INFLATER_SERVICE) {
                        return pluginLayoutInflater
                    }
                    return super.getSystemService(name)
                }
            }

            pluginContext = pCtx

            val cleanInflater =
                applicationContext.getSystemService(
                    LAYOUT_INFLATER_SERVICE
                ) as LayoutInflater

            pluginLayoutInflater =
                cleanInflater.cloneInContext(pCtx)

            initPluginLogger(mainClass)

            installCrashHandler()

            val clazz =
                pluginClassLoader!!.loadClass(mainClass)

            val instance =
                clazz.getDeclaredConstructor().newInstance()

            if (instance is ShadowActivity) {

                pluginActivity = instance

                instance.setHostDelegator(this)

                try {
                    instance.performCreate(savedInstanceState)
                } catch (e: Exception) {
                    Log.e(TAG, "Plugin crashed during onCreate", e)
                    writeCrashLog(e)
                    finish()
                }

            } else {

                val error =
                    "$mainClass is not a ShadowActivity subclass"

                Log.e(TAG, error)
                writeErrorLog(error)
                finish()
            }

        } catch (e: Exception) {

            Log.e(TAG, "Failed to load plugin", e)
            writeCrashLog(e)
            finish()
        }
    }


    override fun onResume() {
        super.onResume()

        try {
            pluginActivity?.performResume()
        } catch (e: Exception) {
            writeCrashLog(e)
            finish()
        }
    }


    override fun onPause() {

        try {
            pluginActivity?.performPause()
        } catch (e: Exception) {
            writeCrashLog(e)
        }

        super.onPause()
    }


    override fun onStop() {

        try {
            pluginActivity?.performStop()
        } catch (e: Exception) {
            writeCrashLog(e)
        }

        super.onStop()
    }


    override fun onDestroy() {

        try {
            pluginActivity?.performDestroy()
        } catch (e: Exception) {
            writeCrashLog(e)
        }

        if (slotIndex >= 0) {
            ActivityHolder.clear(slotIndex)
        }

        pluginActivity = null

        super.onDestroy()
    }


    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        try {
            pluginActivity?.performActivityResult(
                requestCode,
                resultCode,
                data
            )
        } catch (e: Exception) {
            writeCrashLog(e)
            finish()
        }
    }


    override fun getHostContext(): Context =
        this

    override fun getHostResources(): Resources =
        pluginResources ?: super.getResources()

    override fun getHostTheme(): Resources.Theme =
        pluginTheme ?: super.getTheme()

    override fun getHostLayoutInflater(): LayoutInflater =
        pluginLayoutInflater ?: layoutInflater

    override fun getHostWindow(): Window =
        window

    override fun getHostWindowManager(): WindowManager =
        windowManager

    override fun getPluginClassLoader(): ClassLoader =
        pluginClassLoader ?: classLoader


    override fun superSetContentView(layoutResID: Int) {

        val view =
            (pluginLayoutInflater ?: layoutInflater)
                .inflate(layoutResID, null)

        super.setContentView(
            wrapInPluginCoordinator(view)
        )
    }


    override fun superSetContentView(view: View) {

        super.setContentView(
            wrapInPluginCoordinator(view)
        )
    }


    private fun wrapInPluginCoordinator(view: View): View {

        val ctx = pluginContext ?: return view
        val cl = pluginClassLoader ?: return view

        return try {

            val coordClass =
                cl.loadClass(
                    "androidx.coordinatorlayout.widget.CoordinatorLayout"
                )

            val ctor =
                coordClass.getConstructor(
                    Context::class.java
                )

            val wrapper =
                ctor.newInstance(ctx) as ViewGroup

            wrapper.addView(view)

            wrapper

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Failed to create CoordinatorLayout wrapper",
                e
            )

            view
        }
    }


    private fun syncWindowWithPluginTheme(
        theme: Resources.Theme
    ) {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            true
        )

        resolveThemeColor(
            theme,
            android.R.attr.statusBarColor
        )?.let {
            window.statusBarColor = it
        }

        resolveThemeColor(
            theme,
            android.R.attr.navigationBarColor
        )?.let {
            window.navigationBarColor = it
        }
    }


    private fun resolveThemeColor(
        theme: Resources.Theme,
        attrResId: Int
    ): Int? {

        val value = TypedValue()

        if (!theme.resolveAttribute(attrResId, value, true)) {
            return null
        }

        return when {

            value.resourceId != 0 ->
                pluginResources?.getColor(
                    value.resourceId,
                    theme
                )

            value.type in
                    TypedValue.TYPE_FIRST_COLOR_INT..
                    TypedValue.TYPE_LAST_COLOR_INT ->
                value.data

            else -> null
        }
    }


    private fun installCrashHandler() {

        Thread.setDefaultUncaughtExceptionHandler {
                thread,
                throwable ->

            Log.e(
                TAG,
                "Plugin crash",
                throwable
            )

            writeCrashLog(throwable)

            android.os.Process.killProcess(
                android.os.Process.myPid()
            )
        }
    }


    private fun initPluginLogger(mainClass: String) {
        // existing implementation remains
    }


    private fun writeCrashLog(
        throwable: Throwable
    ) {
        writeErrorLog(
            Log.getStackTraceString(throwable)
        )
    }


    private fun writeErrorLog(
        message: String
    ) {

        val pid = projectId ?: return

        try {

            val logDir =
                File(
                    filesDir,
                    "projects/$pid/logs"
                )

            logDir.mkdirs()

            File(
                logDir,
                "crash.log"
            ).appendText(
                "--- ${SimpleDateFormat("MM-dd HH:mm:ss.SSS").format(Date())} ---\n$message\n"
            )

        } catch (_: Exception) {
        }
    }


    companion object {

        private const val TAG =
            "PluginContainer"

        const val EXTRA_APK_PATH =
            "plugin_apk_path"

        const val EXTRA_MAIN_CLASS =
            "plugin_main_class"

        const val EXTRA_PLUGIN_LABEL =
            "plugin_label"

        const val EXTRA_SLOT_INDEX =
            "plugin_slot_index"

        const val EXTRA_PROJECT_ID =
            "plugin_project_id"
    }
}


class PluginSlot0 : PluginContainerActivity()
class PluginSlot1 : PluginContainerActivity()
class PluginSlot2 : PluginContainerActivity()
class PluginSlot3 : PluginContainerActivity()
class PluginSlot4 : PluginContainerActivity()
