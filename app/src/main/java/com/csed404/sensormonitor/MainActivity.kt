package com.csed404.sensormonitor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
        val activities = arrayOf("Other", "Walking", "Running", "Standing", "Sitting", "Upstairs", "Downstairs")
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
        timerTextView.text = String.format("%02d:%02d:%02d", 0, 0, 0)
    }

    private fun startRecording() {
        isRecording = true
        startStopButton.text = getString(R.string.stop_text)
        startTime = System.currentTimeMillis()

        // Create activity-specific directory
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), selectedActivity.toString())
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Create files in the respective activity folder
        linearAccelerometerFile = File(directory, "linear.csv")
        gravityFile = File(directory, "gravity.csv")
        gyroscopeFile = File(directory, "gyro.csv")

        val samplingPeriodUs = 10000 // 100 Hz sampling rate

        // Register Listeners
        sensorManager.registerListener(this, linearAccelerometer, samplingPeriodUs)
        sensorManager.registerListener(this, gravity, samplingPeriodUs)
        sensorManager.registerListener(this, gyroscope, samplingPeriodUs)

        // Start Timer
        startTimer()
    }

    private fun removeLastLines(file: File) {
        if (!file.exists()) return
        val linesToRemove = 500
        try {
            val lines = file.readLines()

            if (lines.size <= linesToRemove) {
                file.writeText("")
            } else {
                val remainingLines = lines.dropLast(linesToRemove)
                file.writeText(remainingLines.joinToString("\n"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        isRecording = false
        startStopButton.text = getString(R.string.start_text)

        // Unregister Listeners
        sensorManager.unregisterListener(this)

        // Stop Timer
        stopTimer()

        // Early Stop - remove last 5 seconds of data
        removeLastLines(linearAccelerometerFile)
        removeLastLines(gravityFile)
        removeLastLines(gyroscopeFile)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording) return

        // Late start by 5 seconds
        val elapsedTime = System.currentTimeMillis() - startTime
        if (elapsedTime < 5000) return

        val timestamp = event.timestamp / 1000 // nano -> micro_sec
        val values = event.values

        val file: File = when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> linearAccelerometerFile
            Sensor.TYPE_GRAVITY -> gravityFile
            Sensor.TYPE_GYROSCOPE -> gyroscopeFile
            else -> return
        }

        val data = String.format(
            "%d,%d,%.9e,%.9e,%.9e\n",
            selectedActivity, // class: int
            timestamp, // timestamp: ms
            values[0], // x: m/s^2 || rad/s
            values[1], // y: m/s^2 || rad/s
            values[2]  // z: m/s^2 || rad/s
        )

        try {
            FileOutputStream(file, true).use { it.write(data.toByteArray()) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}