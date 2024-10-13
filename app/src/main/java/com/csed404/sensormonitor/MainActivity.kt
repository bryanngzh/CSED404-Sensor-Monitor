package com.csed404.sensormonitor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null
    private var gravity: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var activitySpinner: Spinner
    private lateinit var startStopButton: Button
    private lateinit var timerTextView: TextView

    private var selectedActivity = 0
    private var isRecording = false
    private var startTime: Long = 0
    private var timer: Timer? = null

    private lateinit var linearAccelerometerFile: File
    private lateinit var gravityFile: File
    private lateinit var gyroscopeFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Intializing sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (linearAccelerometer == null) {
            Log.e("SensorActivity", "Linear Acceleration Sensor not found!")
        }
        if (gravity == null) {
            Log.e("SensorActivity", "Gravity Sensor not found!")
        }
        if (gyroscope == null) {
            Log.e("SensorActivity", "Gyroscope Sensor not found!")
        }

        // UI
        activitySpinner = findViewById(R.id.activitySpinner)
        startStopButton = findViewById(R.id.startStopButton)
        timerTextView = findViewById(R.id.timerTextView)

        setupSpinner()
        setupButton()
    }

    private fun setupSpinner() {
        // Binding list of activities to spinner
        val activities = arrayOf("Walking", "Running", "Standing", "Sitting", "Upstairs", "Downstairs", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, activities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activitySpinner.adapter = adapter

        // onItemSelectedListener to handle user selection
        activitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedActivity = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButton() {
        startStopButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private fun startTimer() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() - startTime
                val seconds = elapsedTime / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                runOnUiThread {
                    timerTextView.text = String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
                }
            }
        }, 0, 1000)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun startRecording() {
        isRecording = true
        startStopButton.text = getString(R.string.stop_text)
        startTime = System.currentTimeMillis()

        // Create files
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        linearAccelerometerFile = File(directory, "linear_accelerometer.csv")
        gravityFile = File(directory, "gravity.csv")
        gyroscopeFile = File(directory, "gyroscope.csv")

        // Register Listeners
        sensorManager.registerListener(this, linearAccelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)

        // Start Timer
        startTimer()
    }

    private fun stopRecording() {
        isRecording = false
        startStopButton.text = getString(R.string.start_text)

        // Unregister Listeners
        sensorManager.unregisterListener(this)

        // Stop Timer
        stopTimer()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording) return

        val timestamp = event.timestamp
        val values = event.values

        val file: File = when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> linearAccelerometerFile
            Sensor.TYPE_GRAVITY -> gravityFile
            Sensor.TYPE_GYROSCOPE -> gyroscopeFile
            else -> return
        }

        val data = String.format(
            "%d,%d,%.9e,%.9e,%.9e\n",
            selectedActivity,
            timestamp,
            values[0],
            values[1],
            values[2]
        )

        try {
            FileOutputStream(file, true).use { it.write(data.toByteArray()) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}