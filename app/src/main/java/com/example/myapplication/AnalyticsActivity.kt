package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.utils.PredictionEngine
import com.github.mikephil.charting.charts.*
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.database.*
import com.example.myapplication.models.ComplaintsResponse
import com.example.myapplication.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.app.DatePickerDialog
import android.net.Uri
import java.text.SimpleDateFormat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.util.*

class AnalyticsActivity : AppCompatActivity() {

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    
    private var selectedPurok: String? = null
    private var selectedDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val purokList = arrayOf(
        "Purok 2", "Purok 3", "Purok 4", "Dos Riles", "Sentro", 
        "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
        "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
    )

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchDataForCharts()
            fetchComplaintsForChart()
            calculateRouteEfficiency()
            refreshHandler.postDelayed(this, 10000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.analytics_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_export).setOnClickListener {
            showExportModal()
        }

        findViewById<MaterialButton>(R.id.btn_select_purok).setOnClickListener {
            showPurokSelectionModal()
        }

        findViewById<MaterialButton>(R.id.btn_select_purok_efficiency).setOnClickListener {
            showPurokSelectionModal()
        }

        findViewById<MaterialButton>(R.id.btn_select_date).setOnClickListener {
            showDatePicker()
        }

        setupCharts()
        generateSmartInsights()
        setupBottomNavigation()
        
        // Initial fetch
        fetchDataForCharts()
        fetchComplaintsForChart()
        calculateRouteEfficiency()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, year, month, day ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            calendar.set(year, month, day)
            selectedDate = sdf.format(calendar.time)
            
            val displaySdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            val today = sdf.format(Date())
            val btnLabel = if (selectedDate == today) "Today" else displaySdf.format(calendar.time)
            findViewById<MaterialButton>(R.id.btn_select_date).text = btnLabel
            
            // Re-fetch data
            fetchDataForCharts()
            fetchComplaintsForChart()
            calculateRouteEfficiency()
            generateSmartInsights()
        }, currentYear, currentMonth, currentDay)
        
        datePicker.show()
    }

    private fun calculateRouteEfficiency() {
        val today = selectedDate
        
        // 1. Avg Collection Time & Distance Covered & Stops
        database.getReference("truck_locations").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalDistance = 0.0
                var totalStops = 0
                val collectionTimes = mutableListOf<Long>()

                for (truckSnapshot in snapshot.children) {
                    val truckId = truckSnapshot.child("truckId").getValue(String::class.java) ?: ""
                    val historySnapshot = truckSnapshot.child("route_history")
                    val points = mutableListOf<Pair<Double, Double>>()
                    val times = mutableListOf<Long>()

                    for (pointSnap in historySnapshot.children) {
                        val lat = pointSnap.child("lat").getValue(Double::class.java) ?: continue
                        val lng = pointSnap.child("lng").getValue(Double::class.java) ?: continue
                        
                        // Defensively parse time
                        val timeRaw = pointSnap.child("time").value
                        val time = when (timeRaw) {
                            is Number -> timeRaw.toLong()
                            is String -> timeRaw.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        
                        if (time == 0L) continue
                        
                        val pointDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
                        if (pointDate == today) {
                            // Filter by Purok if selected
                            if (selectedPurok != null) {
                                val zone = com.example.myapplication.utils.PurokManager.getZoneAt(lat, lng)
                                if (zone?.name == selectedPurok) {
                                    points.add(lat to lng)
                                    times.add(time)
                                }
                            } else {
                                points.add(lat to lng)
                                times.add(time)
                            }
                        }
                    }

                    // Calculate Distance for this truck (filtered)
                    for (i in 0 until points.size - 1) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(points[i].first, points[i].second, points[i+1].first, points[i+1].second, results)
                        totalDistance += results[0]
                    }

                    // Calculate Stops (3-10 minutes)
                    for (i in 0 until times.size - 1) {
                        val diffMillis = times[i+1] - times[i]
                        val diffMins = diffMillis / (1000.0 * 60.0)
                        if (diffMins >= 3.0 && diffMins <= 10.0) {
                            totalStops++
                        }
                    }
                }

                findViewById<TextView>(R.id.tv_distance_covered).text = String.format("%.1f km", totalDistance / 1000.0)
                findViewById<TextView>(R.id.tv_stops_per_route).text = "$totalStops stops"

                // 2. Collection Time from logs (Respects selectedPurok)
                database.getReference("collection_logs").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(logSnapshot: DataSnapshot) {
                        val entryTimes = mutableMapOf<String, Long>() // Key: truckId_zoneName
                        val durations = mutableListOf<Long>()

                        for (log in logSnapshot.children) {
                            val logDate = log.child("date").getValue(String::class.java)
                            if (logDate != today) continue

                            val truckId = log.child("truckId").getValue(String::class.java) ?: continue
                            val zoneName = log.child("zoneName").getValue(String::class.java) ?: ""
                            val type = log.child("type").getValue(String::class.java) ?: ""
                            
                            val tsRaw = log.child("timestamp").value
                            val timestamp = when (tsRaw) {
                                is Number -> tsRaw.toLong()
                                is String -> tsRaw.toLongOrNull() ?: 0L
                                else -> 0L
                            }

                            if (timestamp == 0L) continue
                            if (selectedPurok != null && zoneName != selectedPurok) continue

                            val key = "${truckId}_$zoneName"
                            if (type == "ENTRY") {
                                entryTimes[key] = timestamp
                            } else if (type == "FULL" || type == "EXIT") {
                                if (entryTimes.containsKey(key)) {
                                    val duration = timestamp - entryTimes[key]!!
                                    durations.add(duration)
                                }
                            }
                        }

                        if (durations.isNotEmpty()) {
                            val avgMillis = durations.average()
                            val avgHours = avgMillis / (1000.0 * 60.0 * 60.0)
                            findViewById<TextView>(R.id.tv_avg_collection_time).text = String.format("%.1f hours", avgHours)
                        } else {
                            findViewById<TextView>(R.id.tv_avg_collection_time).text = "0.0 hours"
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchComplaintsForChart() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val complaints = response.body()?.data ?: emptyList()
                    val counts = mutableMapOf("Pending" to 0, "In Progress" to 0, "Resolved" to 0)
                    
                    for (c in complaints) {
                        // Filter by selected date (assuming createdAt starts with yyyy-MM-dd)
                        if (c.createdAt.startsWith(selectedDate)) {
                            val status = c.status.uppercase().replace("_", " ")
                            when (status) {
                                "PENDING" -> counts["Pending"] = counts["Pending"]!! + 1
                                "IN PROGRESS" -> counts["In Progress"] = counts["In Progress"]!! + 1
                                "RESOLVED" -> counts["Resolved"] = counts["Resolved"]!! + 1
                            }
                        }
                    }
                    updatePieChart(findViewById(R.id.chart_complaints_status), counts, "Complaints Status")
                }
            }

            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {
                // Log error or show toast if needed
            }
        })
    }

    private fun generateSmartInsights() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        
        // Tomorrow's Prediction
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowDate = sdf.format(cal.time)
        val tomorrowVolume = PredictionEngine.predictWasteVolume(tomorrowDate)
        findViewById<TextView>(R.id.tv_waste_tomorrow).text = String.format("%.0f kg", tomorrowVolume)

        // Weekly Prediction
        var weeklyTotal = 0.0
        for (i in 0..6) {
            weeklyTotal += PredictionEngine.predictWasteVolume(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        findViewById<TextView>(R.id.tv_waste_week).text = String.format("%.0f kg", weeklyTotal)
        
        val capacity = PredictionEngine.getTruckCapacityKilos()
        findViewById<TextView>(R.id.tv_truck_capacity).text = String.format("%.0f kg", capacity)

        // Dynamic Recommendations
        val rec1 = findViewById<TextView>(R.id.tv_rec_1)
        val rec2 = findViewById<TextView>(R.id.tv_rec_2)
        val rec3 = findViewById<TextView>(R.id.tv_rec_3)

        if (tomorrowVolume > capacity) {
            rec1.text = "• WARNING: High volume tomorrow ($tomorrowDate). Truck may exceed capacity."
            rec1.setTextColor(Color.RED)
        } else {
            rec1.text = "• Normal volume expected for tomorrow. Single truck is sufficient."
        }

        if (PredictionEngine.getHolidayMultiplier(tomorrowDate) > 1.0) {
            rec2.text = "• Holiday/Event detected: Extra collection time might be needed."
        } else {
            rec2.text = "• Optimal route efficiency expected for the next 24 hours."
        }
        
        rec3.text = "• Note: Waste volume estimation based on Purok area and event calendar."
    }

    private fun showPurokSelectionModal() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Area / Purok")
        builder.setItems(purokList) { _, which ->
            selectedPurok = purokList[which]
            findViewById<TextView>(R.id.tv_selected_purok_name).text = "Viewing: $selectedPurok"
            findViewById<LinearLayout>(R.id.layout_purok_legend).visibility = android.view.View.VISIBLE
            
            // Update button labels to show selection
            findViewById<MaterialButton>(R.id.btn_select_purok).text = selectedPurok
            findViewById<MaterialButton>(R.id.btn_select_purok_efficiency).text = selectedPurok
            
            fetchDataForCharts() // Re-fetch to filter
            calculateRouteEfficiency()
        }
        builder.setNeutralButton("Select All") { _, _ ->
            selectedPurok = null
            findViewById<TextView>(R.id.tv_selected_purok_name).text = "Viewing: All Areas"
            findViewById<LinearLayout>(R.id.layout_purok_legend).visibility = android.view.View.GONE
            
            // Reset button labels
            findViewById<MaterialButton>(R.id.btn_select_purok).text = "Select Area"
            findViewById<MaterialButton>(R.id.btn_select_purok_efficiency).text = "Select Area"

            fetchDataForCharts()
            calculateRouteEfficiency()
        }
        builder.show()
    }

    private fun setupCharts() {
        // Truck Status Pie Chart
        findViewById<PieChart>(R.id.chart_truck_status).apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.BLACK)
            legend.isEnabled = true
        }

        // Complaints Status Pie Chart
        findViewById<PieChart>(R.id.chart_complaints_status).apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            legend.isEnabled = true
        }

        // Purok Coverage Bar Chart
        findViewById<BarChart>(R.id.chart_purok_coverage).apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            axisRight.isEnabled = false
        }
    }

    private fun fetchDataForCharts() {
        // 0. Update Top Cards
        database.getReference("collection_logs").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val visitedZones = mutableSetOf<String>()
                for (log in snapshot.children) {
                    val logDate = log.child("date").getValue(String::class.java)
                    if (logDate == selectedDate) {
                        val zoneName = log.child("zoneName").getValue(String::class.java)
                        if (zoneName != null) visitedZones.add(zoneName)
                    }
                }
                
                val totalZones = 12
                val visitedCount = visitedZones.size
                val percentage = if (totalZones > 0) (visitedCount.toDouble() / totalZones * 100).toInt() else 0
                
                findViewById<TextView>(R.id.tv_analytics_coverage).text = "$percentage%"
                findViewById<TextView>(R.id.tv_analytics_routes_done).text = "$visitedCount/$totalZones"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 1. Truck Status Data
        database.getReference("truck_locations").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val statusCounts = mutableMapOf(
                    "Active" to 0, 
                    "Idle" to 0, 
                    "Full" to 0, 
                    "Completed" to 0
                )
                for (truck in snapshot.children) {
                    val status = truck.child("status").getValue(String::class.java) ?: "idle"
                    when (status.lowercase()) {
                        "active" -> statusCounts["Active"] = statusCounts["Active"]!! + 1
                        "idle" -> statusCounts["Idle"] = statusCounts["Idle"]!! + 1
                        "full" -> statusCounts["Full"] = statusCounts["Full"]!! + 1
                        "completed" -> statusCounts["Completed"] = statusCounts["Completed"]!! + 1
                        else -> statusCounts["Idle"] = statusCounts["Idle"]!! + 1
                    }
                }
                updatePieChart(findViewById(R.id.chart_truck_status), statusCounts, "Truck Status")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Purok Coverage (Objective 2.1)
        database.getReference("collection_logs").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (selectedPurok == null) {
                    // GLOBAL VIEW: Show frequency for all puroks
                    val coverage = mutableMapOf<String, Int>()
                    for (log in snapshot.children) {
                        val logDate = log.child("date").getValue(String::class.java)
                        if (logDate != selectedDate) continue

                        val zone = log.child("zoneName").getValue(String::class.java) ?: continue
                        coverage[zone] = (coverage[zone] ?: 0) + 1
                    }
                    updateBarChart(findViewById(R.id.chart_purok_coverage), coverage)
                } else {
                    // INDIVIDUAL VIEW: Show Collected vs Uncollected for specific Purok
                    val entries = mutableListOf<BarEntry>()
                    var collectedCount = 0
                    var uncollectedCount = 0
                    
                    for (log in snapshot.children) {
                        val logDate = log.child("date").getValue(String::class.java)
                        if (logDate != selectedDate) continue

                        val zone = log.child("zoneName").getValue(String::class.java)
                        if (zone == selectedPurok) {
                            val type = log.child("type").getValue(String::class.java)
                            if (type == "ENTRY") collectedCount++
                        }
                    }

                    uncollectedCount = if (collectedCount == 0) 1 else 0 

                    val chart = findViewById<BarChart>(R.id.chart_purok_coverage)
                    updateIndividualPurokChart(chart, collectedCount, uncollectedCount)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 4. Prediction Evaluation (Objective 3.2 - MAE calculation)
        database.getReference("prediction_eval").limitToLast(50).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalError = 0.0
                var count = 0
                val errors = mutableListOf<Double>()
                
                for (eval in snapshot.children) {
                    val error = eval.child("errorSeconds").getValue(Double::class.java) ?: continue
                    totalError += Math.abs(error)
                    errors.add(error)
                    count++
                }
                
                if (count > 0) {
                    val mae = totalError / count
                    findViewById<TextView>(R.id.tv_prediction_mae).text = String.format("%.1fs", mae)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updatePieChart(chart: PieChart, data: Map<String, Int>, label: String) {
        val entries = data.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, label).apply {
            if (label.contains("Complaints")) {
                // Specific colors to match the Complaints Tab
                colors = listOf(
                    Color.parseColor("#F9A825"), // Pending (Orange)
                    Color.parseColor("#1E88E5"), // In Progress (Blue)
                    Color.parseColor("#43A047")  // Resolved (Green)
                )
            } else if (label.contains("Truck Status")) {
                // Specific colors to match the Dashboard
                colors = listOf(
                    Color.parseColor("#4CAF50"), // Active (Green)
                    Color.parseColor("#FFC107"), // Idle (Yellow)
                    Color.parseColor("#F44336"), // Full (Red)
                    Color.parseColor("#2196F3")  // Completed (Blue)
                )
            } else {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
            }
            valueTextSize = 12f
            valueTextColor = Color.BLACK
        }
        chart.data = PieData(dataSet)
        chart.invalidate()
    }

    private fun updateIndividualPurokChart(chart: BarChart, collected: Int, uncollected: Int) {
        val entries = listOf(
            BarEntry(0f, collected.toFloat()),
            BarEntry(1f, uncollected.toFloat())
        )
        
        val dataSet = BarDataSet(entries, "Status").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"), // Green for Collected
                Color.parseColor("#F44336")  // Red for Uncollected
            )
            valueTextSize = 14f
            valueTextColor = Color.BLACK
        }

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Collected", "Uncollected"))
        chart.xAxis.labelCount = 2
        chart.data = BarData(dataSet)
        chart.invalidate()
    }

    private fun updateBarChart(chart: BarChart, data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry -> BarEntry(index.toFloat(), entry.value.toFloat()) }
        val dataSet = BarDataSet(entries, "Coverage frequency").apply {
            color = Color.parseColor("#2196F3")
        }
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(data.keys.toList())
        chart.data = BarData(dataSet)
        chart.invalidate()
    }

    private fun showExportModal() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_report, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Setup Report Type Spinner
        val reportTypeSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_report_type)
        val reportTypes = arrayOf("All Reports", "Truck Performance", "Complaints Summary", "Route Efficiency", "Purok Coverage")
        val typeAdapter = ArrayAdapter(this, R.layout.dropdown_item, reportTypes)
        reportTypeSpinner.setAdapter(typeAdapter)
        reportTypeSpinner.setText(reportTypes[0], false)

        // Setup Format Spinner
        val formatSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_format)
        val formats = arrayOf("PDF Document (.pdf)", "Excel Spreadsheet (.xlsx)", "CSV File (.csv)")
        val formatAdapter = ArrayAdapter(this, R.layout.dropdown_item, formats)
        formatSpinner.setAdapter(formatAdapter)
        formatSpinner.setText(formats[0], false)

        val etStartDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_start_date)
        val etEndDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_end_date)

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Default to selectedDate instead of just today
        etStartDate.setText(selectedDate)
        etEndDate.setText(selectedDate)

        val dateSetListener = { view: android.widget.EditText ->
            val datePicker = DatePickerDialog(this, { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                view.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        etStartDate.setOnClickListener { dateSetListener(etStartDate) }
        etEndDate.setOnClickListener { dateSetListener(etEndDate) }

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_export_now)?.setOnClickListener {
            val selectedReport = reportTypeSpinner.text.toString()
            val format = formatSpinner.text.toString()
            val startDate = etStartDate.text.toString()
            val endDate = etEndDate.text.toString()

            if (startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (format.contains(".pdf")) {
                generateNativePDF(selectedReport, startDate, endDate)
                alertDialog.dismiss()
            } else {
                // Determine extension for filename sanitization
                val isExcel = format.contains(".xls")
                val formatParam = if (isExcel) "xls" else "csv"
                
                // REAL PHP URL (Using IP from RetrofitClient to avoid localhost issues)
                val phpUrl = "http://192.168.254.106/Asia-repo1-main/backend/export_report.php" 
                
                // Construct parameters carefully
                val downloadUrl = Uri.parse(phpUrl).buildUpon()
                    .appendQueryParameter("type", selectedReport)
                    .appendQueryParameter("format", formatParam)
                    .appendQueryParameter("start_date", startDate)
                    .appendQueryParameter("end_date", endDate)
                    .build().toString()

                Log.d("EXPORT_DEBUG", "Download URL: $downloadUrl")
                Toast.makeText(this, "Exporting $selectedReport...", Toast.LENGTH_SHORT).show()
                
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("EXPORT_ERROR", "Error starting browser: ${e.message}")
                    Toast.makeText(this, "Could not open browser for download", Toast.LENGTH_SHORT).show()
                }

                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    private fun generateNativePDF(reportType: String, start: String, end: String) {
        Toast.makeText(this, "Generating Professional PDF...", Toast.LENGTH_LONG).show()

        val reportView = LayoutInflater.from(this).inflate(R.layout.report_pdf_template, null)
        
        // Populate Data
        reportView.findViewById<TextView>(R.id.tv_pdf_report_type).text = "Report Type: $reportType"
        reportView.findViewById<TextView>(R.id.tv_pdf_period).text = "Period: $start to $end"
        reportView.findViewById<TextView>(R.id.tv_pdf_generated_at).text = "Generated: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}"

        // Capture Charts
        val truckChart = findViewById<PieChart>(R.id.chart_truck_status)
        val complaintsChart = findViewById<PieChart>(R.id.chart_complaints_status)
        val coverageChart = findViewById<BarChart>(R.id.chart_purok_coverage)

        val truckBitmap = truckChart.chartBitmap
        val complaintsBitmap = complaintsChart.chartBitmap
        
        // Use a small delay or ensure the chart is fully rendered
        val coverageBitmap = coverageChart.chartBitmap

        reportView.findViewById<ImageView>(R.id.img_chart_truck).setImageBitmap(truckBitmap)
        reportView.findViewById<ImageView>(R.id.img_chart_complaints).setImageBitmap(complaintsBitmap)
        reportView.findViewById<ImageView>(R.id.img_chart_coverage).setImageBitmap(coverageBitmap)

        // Measure and Layout
        val width = 595 // A4 width in pts (approx)
        val height = 842 // A4 height in pts (approx)
        
        reportView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        reportView.layout(0, 0, reportView.measuredWidth, reportView.measuredHeight)

        // Create PDF
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(width, reportView.measuredHeight, 1).create()
        val page = document.startPage(pageInfo)
        
        val canvas = page.canvas
        reportView.draw(canvas)
        document.finishPage(page)

        // Save File
        val fileName = "Professional_Report_${System.currentTimeMillis()}.pdf"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        try {
            document.writeTo(FileOutputStream(file))
            Toast.makeText(this, "Report saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            
            // Open PDF
            val path = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(path, "application/pdf")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Note: On modern Android, you'd use FileProvider here, but for simple local check:
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_reports

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> {
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_track -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_reports -> true
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_users -> {
                    startActivity(Intent(this, UserManagementActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, AdminSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}