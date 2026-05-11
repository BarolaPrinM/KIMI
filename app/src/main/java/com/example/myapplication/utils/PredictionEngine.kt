package com.example.myapplication.utils

import kotlin.math.pow

object PredictionEngine {

    /**
     * Simple Linear Regression to predict Arrival Time
     * Y = mX + b
     * Y: Time to arrive (seconds)
     * X: Distance remaining (meters)
     * 
     * @param distanceMeters Current distance to target
     * @param historicalData List of Pair(HistoricalDistance, HistoricalTimeTaken)
     */
    fun predictArrivalTime(distanceMeters: Double, historicalData: List<Pair<Double, Double>>): Double {
        if (historicalData.size < 3) {
            // Fallback: 15 km/h average speed (4.16 m/s)
            return distanceMeters / 4.16
        }

        val n = historicalData.size
        val sumX = historicalData.sumOf { it.first }
        val sumY = historicalData.sumOf { it.second }
        val sumXY = historicalData.sumOf { it.first * it.second }
        val sumX2 = historicalData.sumOf { it.first.pow(2) }

        val denominator = n * sumX2 - sumX.pow(2)
        if (denominator == 0.0) return distanceMeters / 4.16

        val m = (n * sumXY - sumX * sumY) / denominator
        val b = (sumY - m * sumX) / n

        val prediction = (m * distanceMeters) + b
        
        // Ensure we don't return negative or unrealistically small values
        return if (prediction < (distanceMeters / 30.0)) distanceMeters / 4.16 else prediction
    }

    /**
     * Moving Average for time-series delay forecasting
     */
    fun calculateMovingAverageDelay(delays: List<Double>, window: Int = 5): Double {
        if (delays.isEmpty()) return 0.0
        val recentDelays = delays.takeLast(window)
        return recentDelays.average()
    }

    // --- WASTE VOLUME PREDICTION (Objective 2.1 & 2.2) ---

    private val purokBaseWeights = mapOf(
        "Purok 2" to 300.0,
        "Purok 3" to 350.0,
        "Purok 4" to 400.0,
        "Dos Riles" to 250.0,
        "Sentro" to 500.0,
        "San Isidro" to 320.0,
        "Paraiso" to 280.0,
        "Riverside" to 310.0,
        "Kalaw Street" to 200.0,
        "Home Subdivision" to 450.0,
        "Tanco Road / Ayala Highway" to 150.0,
        "Brixton Area" to 330.0
    )

    /**
     * Estimates waste volume based on date, holidays, and purok area.
     */
    fun predictWasteVolume(date: String): Double {
        val multiplier = getHolidayMultiplier(date)
        return purokBaseWeights.values.sum() * multiplier
    }

    fun getHolidayMultiplier(date: String): Double {
        // Simple logic for major Filipino events/holidays
        return when {
            date.contains("-12-25") || date.contains("-01-01") -> 2.5 // Heavy Christmas/New Year load
            date.contains("-12-24") || date.contains("-12-31") -> 2.0 // Eve preparation
            date.contains("-11-01") || date.contains("-11-02") -> 1.5 // Undas
            // Weekends usually have 20% more waste
            isWeekend(date) -> 1.2
            else -> 1.0
        }
    }

    private fun isWeekend(date: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val d = sdf.parse(date)
            val cal = java.util.Calendar.getInstance()
            if (d != null) {
                cal.time = d
                val day = cal.get(java.util.Calendar.DAY_OF_WEEK)
                day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun getTruckCapacityKilos(): Double = 6000.0 // Updated to 6 Tons as per specs

    /**
     * Generates operational insights based on predictions and truck logic.
     */
    fun generateInsights(predictedVolume: Double, truckCapacity: Double, isHoliday: Boolean): List<String> {
        val insights = mutableListOf<String>()
        
        if (predictedVolume > truckCapacity) {
            insights.add("CRITICAL: Single truck may be insufficient for tomorrow. Expected overflow.")
        } else if (predictedVolume > truckCapacity * 0.8) {
            insights.add("WARNING: High waste volume expected. Capacity at 80%+")
        }
        
        if (isHoliday) {
            insights.add("EVENT ALERT: Holiday detected. Expect heavy loads and slower collection.")
        }
        
        insights.add("OPTIMIZATION: Recommended start time 30 mins earlier to avoid traffic peak.")
        
        return insights
    }
}
