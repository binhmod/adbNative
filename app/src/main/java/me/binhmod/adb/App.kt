package me.binhmod.adb

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
// import com.topjohnwu.superuser.Shell
import me.binhmod.adb.logd
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
// import rikka.material.app.LocaleDelegate

lateinit var application: App

class App : Application() {

    companion object {

        init {
            logd("App", "init")

            // Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }
    }

    private fun init(context: Context?) {
        AdbSettings.initialize(context)
        // AppCompatDelegate.setDefaultNightMode(AdbSettings.getNightMode())
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        init(this)
    }

}
