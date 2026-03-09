package me.binhmod.adb.starter

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.binhmod.adb.R
import me.binhmod.adb.AdbSettings
import me.binhmod.adb.AdbClient
import me.binhmod.adb.AdbKey
import me.binhmod.adb.AdbKeyException
import me.binhmod.adb.PreferenceAdbKeyStore
import me.binhmod.adb.EnvironmentUtils
import me.binhmod.adb.starter.Starter
import me.binhmod.adb.AdbMdns
import me.binhmod.adb.databinding.StarterActivityBinding
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

class StarterActivity : AppCompatActivity() {

    private val viewModel by lazy {
        AdbViewModel(
            intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1",
            intent.getIntExtra(EXTRA_PORT, -1)
        )
    }
    
    private lateinit var binding: StarterActivityBinding
    private var adbMdns: AdbMdns? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24)

        binding = StarterActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.output.observe(this) { resource ->
            binding.text1.text = resource.data?.toString() ?: ""
            
            if (resource.status == Status.ERROR) {
                var message = 0
                when (resource.error) {
                    is AdbKeyException -> message = R.string.adb_error_key_store
                    is ConnectException -> message = R.string.cannot_connect_port
                    is SSLProtocolException -> message = R.string.adb_pair_required
                }
                if (message != 0) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
        
        viewModel.startAdbConnection(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        adbMdns?.stop()
    }
    
    fun onPortFound(port: Int) {
        viewModel.setPortAndConnect(port)
    }
    
    fun showPortInputDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Enter ADB Port")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val port = input.text.toString().toIntOrNull()
                if (port != null && port > 0) {
                    onPortFound(port)
                } else {
                    Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show()
                    showPortInputDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_HOST = "EXTRA_HOST"
        const val EXTRA_PORT = "EXTRA_PORT"
    }
}

class AdbViewModel(private val host: String, private var port: Int) : ViewModel() {

    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()
    val output: LiveData<Resource<StringBuilder>> = _output
    private var activity: StarterActivity? = null
    private var adbMdns: AdbMdns? = null

    fun appendOutput(line: String) {
        sb.appendLine(line)
        postResult()
    }
    
    fun setPortAndConnect(newPort: Int) {
        port = newPort
        startAdbConnection()
    }

    private fun postResult(throwable: Throwable? = null) {
        if (throwable == null)
            _output.postValue(Resource.success(sb))
        else
            _output.postValue(Resource.error(throwable, sb))
    }

    fun startAdbConnection(activity: StarterActivity) {
        this.activity = activity
        
        if (port > 0) {
            doConnect()
            return
        }
        
        port = EnvironmentUtils.getAdbTcpPort()
        if (port > 0) {
            doConnect()
            return
        }
        
        adbMdns = AdbMdns(activity, AdbMdns.TLS_CONNECT) { foundPort ->
            activity.onPortFound(foundPort)
        }.apply { start() }
    }
    
    private fun doConnect() {
        appendOutput("Connecting to port $port...")
        
        viewModelScope.launch(Dispatchers.IO) {
            val key = try {
                AdbKey(PreferenceAdbKeyStore(AdbSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                postResult(AdbKeyException(e))
                return@launch
            }

            try {
                AdbClient(host, port, key).use { client ->
                    client.connect()
                    client.shellCommand(Starter.internalCommand) { data ->
                        appendOutput(String(data))
                    }
                }
            } catch (e: ConnectException) {
                activity?.runOnUiThread {
                    activity?.showPortInputDialog()
                }
            } catch (e: Exception) {
                postResult(e)
            }
        }
    }
    
    fun startAdbConnection() {
        doConnect()
    }
    
    override fun onCleared() {
        super.onCleared()
        adbMdns?.stop()
    }
}