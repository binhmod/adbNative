package me.binhmod.adb.starter

import me.binhmod.adb.application
import java.io.File

object Starter {

    private val starterFile =
        File(application.applicationInfo.nativeLibraryDir, "libpaigisk.so")

    val adbCommand: String =
        "adb shell ${starterFile.absolutePath}"

    val internalCommand: String =
        "${starterFile.absolutePath} --apk=${application.applicationInfo.sourceDir}"
}