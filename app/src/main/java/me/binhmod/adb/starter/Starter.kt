package me.binhmod.adb.starter

import me.binhmod.adb.application
// import java.io.File

object Starter {

    // private val starterFile = File(application.applicationInfo.nativeLibraryDir, "libshizuku.so")

    // val userCommand: String = starterFile.absolutePath

    // val adbCommand = "adb shell $userCommand"
    
    val adbCommand = "adb shell echo adbCommand"

    // val internalCommand = "$userCommand --apk=${application.applicationInfo.sourceDir}"
    
    val internalCommand = "echo internalCommand && id -u"
}
