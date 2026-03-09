@file:Suppress("NOTHING_TO_INLINE")

package me.binhmod.adb

import android.util.Log

inline val <reified T> T.TAG: String
    get() = T::class.java.simpleName
        .let { name ->
            when {
                name.isBlank() -> T::class.java.name  // fallback for anonymous/local classes
                    .substringAfterLast('.')
                    .take(23)
                    .ifBlank { "Unknown" }
                name.length > 23 -> name.substring(0, 23)
                else -> name
            }
        }

inline fun <reified T> T.logv(message: String, throwable: Throwable? = null) = logv(TAG, message, throwable)
inline fun <reified T> T.logi(message: String, throwable: Throwable? = null) = logi(TAG, message, throwable)
inline fun <reified T> T.logw(message: String, throwable: Throwable? = null) = logw(TAG, message, throwable)
inline fun <reified T> T.logd(message: String, throwable: Throwable? = null) = logd(TAG, message, throwable)
inline fun <reified T> T.loge(message: String, throwable: Throwable? = null) = loge(TAG, message, throwable)

inline fun <reified T> T.logv(tag: String, message: String, throwable: Throwable? = null) = Log.v(tag, message, throwable)
inline fun <reified T> T.logi(tag: String, message: String, throwable: Throwable? = null) = Log.i(tag, message, throwable)
inline fun <reified T> T.logw(tag: String, message: String, throwable: Throwable? = null) = Log.w(tag, message, throwable)
inline fun <reified T> T.logd(tag: String, message: String, throwable: Throwable? = null) = Log.d(tag, message, throwable)
inline fun <reified T> T.loge(tag: String, message: String, throwable: Throwable? = null) = Log.e(tag, message, throwable)