package com.vibe.app.plugin

import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
open class PluginContainerActivity :
    AppCompatActivity(),
    HostActivityDelegator {

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )


        val apkPath =
            intent.getStringExtra(EXTRA_APK_PATH)

        val mainClass =
            intent.getStringExtra(EXTRA_MAIN_CLASS)


        projectId =
            intent.getStringExtra(EXTRA_PROJECT_ID)

        slotIndex =
            intent.getIntExtra(
                EXTRA_SLOT_INDEX,
                -1
            )


        if (slotIndex >= 0) {
            ActivityHolder.set(
                slotIndex,
                this
            )
        }


        if (apkPath == null || mainClass == null) {
            writeErrorLog(
                "Missing apkPath or mainClass"
            )
            finish()
            return
        }


        try {

            pluginClassLoader =
                PluginResourceLoader.createPluginClassLoader(
                    context = this,
                    apkPath = apkPath,
                    parentClassLoader =
                    ShadowActivity::class.java.classLoader!!
                )


            pluginResources =
                PluginResourceLoader.loadPluginResources(
                    this,
                    apkPath
                )


            pluginTheme =
                pluginResources!!.newTheme()


            val pCtx =
                object : android.content.ContextWrapper(this) {

                    override fun getResources():
                        Resources =
                        pluginResources!!

                    override fun getClassLoader():
                        ClassLoader =
                        pluginClassLoader!!

                    override fun getTheme():
                        Resources.Theme =
                        pluginTheme!!
                }


            pluginContext = pCtx


            pluginLayoutInflater =
                layoutInflater.cloneInContext(
                    pCtx
                )


            val clazz =
                pluginClassLoader!!.loadClass(
                    mainClass
                )


            val instance =
                clazz.getDeclaredConstructor()
                    .newInstance()


            if (instance is ShadowActivity) {

                pluginActivity = instance

                instance.setHostDelegator(
                    this
                )

                instance.performCreate(
                    savedInstanceState
                )

            } else {

                writeErrorLog(
                    "$mainClass is not ShadowActivity"
                )

                finish()
            }


        } catch (e: Exception) {

            Log.e(
                TAG,
                "Plugin loading failed",
                e
            )

            writeCrashLog(e)

            finish()
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

        pluginActivity?.performActivityResult(
            requestCode,
            resultCode,
            data
        )
    }


    override fun getHostContext(): Context =
        this


    override fun getHostResources(): Resources =
        pluginResources ?: resources


    override fun getHostTheme(): Resources.Theme =
        pluginTheme ?: theme


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
            layoutInflater.inflate(
                layoutResID,
                null
            )

        super.setContentView(view)
    }


    override fun superSetContentView(view: View) {

        super.setContentView(view)
    }


    // ===== Missing HostActivityDelegator implementations =====

    override fun <T : View> superFindViewById(id: Int): T {
        return findViewById(id)
    }


    override fun superStartActivity(
        intent: Intent
    ) {
        super.startActivity(intent)
    }


    override fun superStartActivityForResult(
        intent: Intent,
        requestCode: Int,
        options: Bundle?
    ) {
        super.startActivityForResult(
            intent,
            requestCode,
            options
        )
    }


    override fun superFinish() {
        super.finish()
    }


    override fun setPluginResult(
        resultCode: Int,
        data: Intent?
    ) {
        setResult(
            resultCode,
            data
        )
    }


    override fun getHostIntent(): Intent {
        return intent
    }


    private fun writeCrashLog(
        throwable: Throwable
    ) {

        writeErrorLog(
            Log.getStackTraceString(
                throwable
            )
        )
    }


    private fun writeErrorLog(
        message: String
    ) {

        val id = projectId ?: return

        try {

            val dir =
                File(
                    filesDir,
                    "projects/$id/logs"
                )

            dir.mkdirs()

            File(
                dir,
                "crash.log"
            ).appendText(
                message + "\n"
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
    }
