package com.example.appdev

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlin.random.Random
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Thread.sleep
import kotlin.math.sin

const val TIME_DELAY = 0.5f
const val MAX_ENTRIES = 100000
const val STARTED : Int = 1
const val STOPPED : Int = 0
const val CREATE_FILE : Int = 1

class GraphView : AppCompatActivity() {

    val xVal = ArrayList<Float>()
    val yVal = ArrayList<Float>()
    private var state = STOPPED
    private var isClosed : Boolean = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_graph_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }



        //start service to access bluetooth thread later

        lateinit var oStream: FileOutputStream

        lateinit var chart: LineChart

        chart = findViewById(R.id.chart)
        var x = 0f
        var index = 0
        var y = 0f

        val sineWave = Array(1000) { i ->
            val r = i * (2*Math.PI/100)
            25 * sin(r).toFloat() + 50
        }


        val fileName = "temp.csv"
        val file = File(cacheDir,fileName)

        if(file.exists()) {
            file.delete()
        }

        oStream = FileOutputStream(file,true)
        //oStream.write("1,2\n3,4\n5,6".toByteArray())


        val startButton = findViewById<Button>(R.id.starter)
        val stopButton = findViewById<Button>(R.id.stopper)
        val saveButton = findViewById<Button>(R.id.saver)


        saveButton.setOnClickListener{
            oStream.close()
            createFile(file.toUri())
        }


        startButton.setOnClickListener {
            if(state == STOPPED) {
                state = STARTED

//                if(isClosed) {
//                    val fileName = "temp.csv"
//                    val file = File(cacheDir,fileName)
//
//                    if(file.exists()) {
//                        file.delete()
//                    }
//
//                    outputStream = FileOutputStream(file,true)
//
//                    isClosed = false
//                }


                lifecycleScope.launch {

                    val data = chart.data.getDataSetByIndex(0) as LineDataSet

                    //scrolling
                    launch {
                        while(state == STARTED){

                            delay(66)
                            updateView(chart, x)
                        }
                    }

                    //clearing if too big
                    launch {
                        while(state == STARTED){

                            Log.d("Entry Count: ", (xVal.size/1000).toString() + "k")

                            if(data.entryCount > MAX_ENTRIES) {

                                data.clear()
                                chart.moveViewToX(x)
                                chart.notifyDataSetChanged()

                            }

                            delay(1000)
                        }
                    }


                    //plotting
                    launch{
                        while (state == STARTED) {
                            //Log.d("Activity", "Running")

                            y = Random.nextFloat() * (80 - 20) + 20
                            for(i in 0 until 2){
                                //y = sineWave[index]
                                addData(chart, x, y)
                                xVal.add(x)
                                yVal.add(y)
                                x += TIME_DELAY
                                index++
                                if (index == 100) index = 0
                            }

                            delay(33)
                        }
                    }

                    //csv writing
                    launch {
                            while(state == STARTED) {
                                if (xVal.size > 1000) {
                                    val subX = xVal.subList(0, 1000)
                                    val subY = yVal.subList(0, 1000)

                                    val rows = map(subX, subY)

                                    for (row in rows) {
                                        oStream.write((row + "\n").toByteArray())
                                    }

                                    Log.d("CSV", "wrote csv and xVal size:")
                                    subX.clear()
                                    subY.clear()
                                    Log.d("xVal size", xVal.size.toString())
                                }
                                delay(500)
                            }
                    }
                }
            }
        }

        stopButton.setOnClickListener{
            if(state == STARTED) {
                state = STOPPED
                sleep(300)
                val rows = map(xVal,yVal)
                for (row in rows) {
                    oStream.write((row + "\n").toByteArray())
                }
                xVal.clear()
                yVal.clear()
            }
        }




        val lineDataSet = LineDataSet(null, "Dynamic Graph")
        lineDataSet.color = Color.BLUE
        lineDataSet.lineWidth = 2f
        lineDataSet.setDrawCircles(false)
        lineDataSet.setDrawValues(false)


        val lineData = LineData(lineDataSet)


        chart.data = lineData

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setGranularity(2f) // One unit per X label



        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false // Disable the right Y-axis
        chart.axisLeft.axisMinimum = 0f   // Set minimum Y value
        chart.axisLeft.axisMaximum = 100f // Set maximum Y value

        chart.invalidate() // Refreshes the chart with the new data


        chart.setVisibleXRange(20f,20f)

        chart.invalidate()



    }

    override fun onDestroy() {
        super.onDestroy()
        state = STOPPED
    }

    //check for null later because we won't have any data or maybe i force data
    fun addData(chart: LineChart, x: Float, y: Float) {
        val lineDataSet = chart.data.getDataSetByIndex(0) as LineDataSet
        lineDataSet.addEntry(Entry(x, y))
        chart.data.notifyDataChanged()

    }

    fun updateView(chart: LineChart, x: Float) {
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()

        chart.moveViewToX(x)
        chart.setVisibleXRange(20f,20f)
        chart.invalidate()
    }

    fun createDataSet() : LineDataSet {
        var dataSet = LineDataSet(null, "Dynamic Graph")
        dataSet.color = Color.BLUE
        dataSet.lineWidth = 2f
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        return dataSet
    }

    fun map(x : List<Float>, y: List<Float>) : List<String> {
        val rows = x.indices.map { i -> "${x[i]},${y[i]}"}
        return rows
    }


    private fun createFile(pickerInitialUri: Uri) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv" // Set MIME type to CSV
            putExtra(Intent.EXTRA_TITLE, "untitled.csv") // Give the file a name

            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker before your app creates the document.
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        startActivityForResult(intent, CREATE_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CREATE_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Once the file is created, you can write to it
                copyCsvFileToUri(uri)
            }
        }
    }

    private fun copyCsvFileToUri(destinationUri: Uri) {
        // Path to your existing CSV file (from cache or internal storage)
        val sourceFile = File(cacheDir, "temp.csv")  // Replace this with your actual file location

        try {
            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                // Open input stream for the existing file
                FileInputStream(sourceFile).use { inputStream ->
                    // Copy the contents of the source CSV file to the selected destination
                    inputStream.copyTo(outputStream)

                }
            }
            Toast.makeText(this, "File saved successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}