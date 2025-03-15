package com.synaptictools.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.synaptictools.iperf.IPerfConfig
import com.synaptictools.sample.databinding.ActivityMainBinding
import java.io.File

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.widget.ScrollView
import com.github.anastr.speedviewlib.SpeedView
import java.net.Inet4Address
import java.net.NetworkInterface


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity:"
    }
    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel
    var isServer: Boolean = false
    var isUpload: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        with(activityMainBinding) {
            lifecycleOwner = this@MainActivity
            //val wifiIP = getWiFiIPAddress(this@MainActivity)
            //val mobileIP = getMobileDataIPAddress()
            val wifiIP = getIPAddress(this@MainActivity, "wifi")
            // Disable Wi-Fi

            val mobileIP = getIPAddress(this@MainActivity, "cellular")

            val scrollView: ScrollView = findViewById(R.id.result_placeholder)
            val textView: TextView = findViewById(R.id.tvResult)

            mainViewModel.iPerfRequestResult.observe(this@MainActivity) { newResult ->
                textView.text = newResult // This will update the TextView with the new result
                scrollView.post {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN) // Scroll to the bottom of the ScrollView
                }
            }

            findViewById<TextView>(R.id.ipAddress).text = "Wi-Fi IP: $wifiIP\nMobile Data IP: $mobileIP"

            viewModel = mainViewModel
            btRequest.setText("Start client")
            clientServerSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    btRequest.setText("Start server")
                    isServer = true
                } else {
                    btRequest.setText("Start client")
                    isServer = false
                }
            }

            isUploadSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {

                    isUpload = true
                } else {

                    isUpload = false
                }
            }
            val speedometer : SpeedView = findViewById<SpeedView>(R.id.uplinkspeedometer)
            speedometer.withTremble = false
            mainViewModel.speedbps.observe(this@MainActivity) { newSpeed ->
                //textView.text = newSpeed  // Update UI when data changes

                    println("Speed ================== : $newSpeed")
                    speedometer.speedTo(newSpeed/1000000.0f, 50) // Set gauge to 75%

            }





            btRequest.setOnClickListener {
                val hostname: String = etHostname.text.toString()
                val port = etPort.text.toString()
                if (hostname.isNotEmpty() && port.isNotEmpty()) {
                    val stream = File(applicationContext.filesDir, "iperf3.XXXXXX")
                    if(isServer == true) {
                        mainViewModel.startRequest(
                            IPerfConfig(
                                hostname = hostname,
                                port = port.toInt(),
                                stream = stream.path,
                                download = true,
                                interval = 5,
                                useUDP = false,
                                json = false,
                                debug = false,
                                role = 's',
                            ),
                            isAsync = true
                        )
                    } else {
                        mainViewModel.startRequest(
                            IPerfConfig(
                                hostname = hostname,
                                port = port.toInt(),
                                stream = stream.path,
                                download = isUpload,
                                duration = 10,
                                interval = 1,
                                useUDP = false,
                                json = false,
                                debug = false,
                                role = 'c',
                            ),
                            isAsync = true
                        )
                    }
                }
            }
        }
    }


    // Get Wi-Fi IP Address

    fun getWiFiIPAddress(context: Context): String {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "Wi-Fi IP: Unknown"
        }
    }

    // Get Mobile Data IP Address
    fun getMobileDataIPAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "Mobile Data IP: Unknown"
        } catch (e: Exception) {
            "Mobile Data IP: Unknown"
        }
    }

    fun getIPAddress(context: Context, networkType: String): String {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = connectivityManager.allNetworks

            for (network in networks) {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                val linkProperties = connectivityManager.getLinkProperties(network) ?: continue

                // Check if it's Wi-Fi or Cellular
                if ((networkType == "wifi" && networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) ||
                    (networkType == "cellular" && networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)) {

                    for (linkAddress in linkProperties.linkAddresses) {
                        val ip = linkAddress.address
                        if (ip is Inet4Address && !ip.isLoopbackAddress) {
                            return ip.hostAddress ?: "Unknown IP"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown IP"
    }


}
