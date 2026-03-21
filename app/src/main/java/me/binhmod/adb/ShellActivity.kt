package me.binhmod.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import binhmod.server.IShellCallback
import binhmod.server.IShellService
import me.binhmod.adb.databinding.ShellActivityBinding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ShellActivity : AppCompatActivity() {

    private lateinit var binding: ShellActivityBinding

    private var service: IShellService? = null
    private var shellId = -1

    private val executor = Executors.newSingleThreadExecutor()
    private val readerRunning = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private var deathRecipient: IBinder.DeathRecipient? = null

    companion object {
        private const val TAG = "ShellActivity"
    }

    private val binderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try { unregisterReceiver(this) } catch (ignored: Exception) {}
            if (executor.isShutdown) return
            val svc = BootstrapProvider.getService() ?: return
            setupShell(svc)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ShellActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSend.setOnClickListener { sendCommand() }
        binding.btnStop.setOnClickListener { stopServer() }
        tryConnectOrWait()
    }

    override fun onResume() {
        super.onResume()
        if (shellId == -1 || service?.asBinder()?.pingBinder() != true) {
            service = null
            shellId = -1
            readerRunning.set(false)
            tryConnectOrWait()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        // Hủy receiver trước
        try { unregisterReceiver(binderReceiver) } catch (ignored: Exception) {}

        readerRunning.set(false)
        readerThread?.interrupt()

        deathRecipient?.let {
            try { service?.asBinder()?.unlinkToDeath(it, 0) } catch (ignored: Exception) {}
        }

        try {
            if (shellId != -1) {
                service?.close(shellId)
                Log.d(TAG, "Closed shell session $shellId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing shell", e)
        }

        // Shutdown executor cuối cùng
        executor.shutdownNow()
    }

    private fun tryConnectOrWait() {
        val binder = BootstrapProvider.getBinder()
        if (binder != null && binder.pingBinder()) {
            val svc = BootstrapProvider.getService() ?: return
            setupShell(svc)
        } else {
            Log.d(TAG, "Waiting for server binder...")
            try {
                registerReceiver(binderReceiver,
                    IntentFilter("binhmod.server.BINDER_RECEIVED"))
            } catch (e: Exception) {
                Log.e(TAG, "registerReceiver failed", e)
            }
        }
    }

    private fun setupShell(svc: IShellService) {
        if (executor.isShutdown) return
        service = svc

        // Link death để detect server chết
        deathRecipient = IBinder.DeathRecipient {
            Log.e(TAG, "Service binder died")
            service = null
            shellId = -1
            readerRunning.set(false)
            runOnUiThread {
                Toast.makeText(this, "Server died", Toast.LENGTH_SHORT).show()
            }
        }
        try {
            svc.asBinder().linkToDeath(deathRecipient!!, 0)
            Log.d(TAG, "linkToDeath registered")
        } catch (e: Exception) {
            Log.e(TAG, "linkToDeath failed", e)
        }

        executor.execute {
            try {
                shellId = svc.openShell()
                if (shellId == -1) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to open shell", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }
                startReader()
                registerCallback()
                runOnUiThread {
                    Toast.makeText(this, "Shell connected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during shell setup", e)
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun registerCallback() {
        try {
            Log.d(TAG, "Registering callback for shell $shellId")
            service?.registerCallback(shellId, object : IShellCallback.Stub() {
                override fun onData() {
                    Log.d(TAG, "Callback onData() received")
                }

                override fun onExit(code: Int) {
                    Log.d(TAG, "Callback onExit() with code $code")
                    runOnUiThread {
                        Toast.makeText(this@ShellActivity, "Shell exited: $code", Toast.LENGTH_SHORT).show()
                        shellId = -1
                        readerRunning.set(false)
                    }
                }

                override fun onError(msg: String) {
                    Log.e(TAG, "Callback onError(): $msg")
                    runOnUiThread {
                        Toast.makeText(this@ShellActivity, "Shell error: $msg", Toast.LENGTH_SHORT).show()
                        shellId = -1
                        readerRunning.set(false)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register callback", e)
        }
    }

    private fun startReader() {
        if (!readerRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Reader already running")
            return
        }

        Log.d(TAG, "Starting reader thread")
        readerThread = Thread {
            while (readerRunning.get()) {
                try {
                    val srv = service
                    if (srv == null || shellId == -1) {
                        Thread.sleep(50)
                        continue
                    }
                    val chunk = srv.poll(shellId, 100)
                    if (chunk != null && chunk.isNotEmpty()) {
                        val text = String(chunk)
                        runOnUiThread {
                            binding.tvOutput.append(text)
                            binding.scroll.post { binding.scroll.fullScroll(View.FOCUS_DOWN) }
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Reader exception", e)
                    readerRunning.set(false)
                    runOnUiThread {
                        Toast.makeText(this@ShellActivity, "Reader stopped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Log.d(TAG, "Reader thread finished")
        }
        readerThread?.start()
    }
    
    fun stopServer() {
    executor.execute {
        try {
            if (shellId != -1) {
                service?.close(shellId)
                shellId = -1
            }
            service?.exit()
            service = null
        } catch (e: Exception) {
            Log.e(TAG, "stopServer error", e)
        }
    }
}

    private fun sendCommand() {
        val cmd = binding.editText.text?.toString() ?: ""
        if (cmd.isBlank()) return

        Log.d(TAG, "sendCommand: $cmd")

        val srv = service
        if (srv == null || shellId == -1) {
            Toast.makeText(this, "Server not ready or shell dead", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "sendCommand: service=$srv, shellId=$shellId")
            return
        }

        if (!srv.asBinder().isBinderAlive) {
            Toast.makeText(this, "Server died", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "sendCommand: binder not alive")
            return
        }

        binding.editText.text?.clear()
        executor.execute {
            try {
                srv.write(shellId, (cmd + "\n").toByteArray())
                Log.d(TAG, "write() called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Write failed", e)
                runOnUiThread {
                    Toast.makeText(this@ShellActivity, "Write failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    shellId = -1
                    readerRunning.set(false)
                }
            }
        }
    }
}