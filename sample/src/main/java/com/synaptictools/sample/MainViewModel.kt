package com.synaptictools.sample

import android.widget.TextView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.anastr.speedviewlib.SpeedView
import com.synaptictools.iperf.IPerf
import com.synaptictools.iperf.IPerfConfig
import com.synaptictools.iperf.IPerfResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.StringBuilder
import java.util.*

/**
 * Created by KMR on October 10, 2021.
 * Copyright (c) 2021 Augmedix. All rights reserved.
 */
class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel:"
    }
    private val resultBuilder: StringBuilder? = StringBuilder()
    private val _iPerfRequestResult: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    private val _speedbps = MutableLiveData<Float>()
    val speedbps: LiveData<Float> get() = _speedbps


    val iPerfRequestResult: LiveData<String>
        get() = _iPerfRequestResult

    private val _iPerfTestRunning: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }
    val iPerfTestRunning: LiveData<Boolean>
        get() = _iPerfTestRunning

    fun startRequest(config: IPerfConfig, isAsync: Boolean = true) {
        println("$TAG isAsync request $isAsync")
        println("$TAG start request with $config")
        _iPerfTestRunning.postValue(true)
        resultBuilder?.clear()
        viewModelScope.launch {
            //repeat(10){
                doStartRequest(config, isAsync)
            //}

        }
    }

    private suspend fun doStartRequest(config: IPerfConfig, isAsync: Boolean){
        withContext(Dispatchers.IO) {
            try {
                if(isAsync) {
                    IPerf.seCallBack {
                        success {
                            println("$TAG iPerf request done")
                            _iPerfTestRunning.postValue(false)
                            _speedbps.postValue(0.0f)
                        }
                        update { text ->
                            resultBuilder?.append(text)
                            println("$TAG Get result !!!!!!!!!!!!!!!")
                            println(text)
                            _iPerfRequestResult.postValue(resultBuilder.toString())

                            try {
                                val jsonString = text.toString().trimIndent()
                                val jsonObject = JSONObject(jsonString)
                                println("Parsed JSON: $jsonObject")
                                val speed: Float = jsonObject.optDouble("bits_per_second", 0.0).toFloat()
                                _speedbps.postValue(speed)

                            } catch (e: Exception) {
                                println("Error parsing JSON: ${e.message}")
                            }
                            //println(jsonObject)
                        }
                        error { e ->
                            resultBuilder?.append("\niPerf request failed:\n error: $e")
                            println("$TAG $resultBuilder")
                            _iPerfTestRunning.postValue(false)
                            _speedbps.postValue(0.0f)
                        }
                    }
                }
                val result = IPerf.request(config)
                if(!isAsync) {
                    when (result) {
                        is IPerfResult.Success -> {
                            println("$TAG iPerf request done")
                            resultBuilder?.append(result.data)
                            _iPerfRequestResult.postValue(resultBuilder.toString())
                            _iPerfTestRunning.postValue(false)
                            _speedbps.postValue(0.0f)
                        }
                        is IPerfResult.Error -> {
                            println("$TAG iPerf request failed-> ${result.error}")
                            resultBuilder?.append("\niPerf request failed:\n error: $result.error")
                            _iPerfTestRunning.postValue(false)
                            _speedbps.postValue(0.0f)
                        }
                    }
                }
            } catch (e: Exception) {
                println("$TAG error on doStartRequest() -> ${e.message}")
            }
        }
    }
}