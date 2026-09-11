package com.mudasir.smartledger.util

import com.mudasir.smartledger.BuildConfig
import com.mudasir.smartledger.data.Electricity
import com.mudasir.smartledger.data.Expense
import com.mudasir.smartledger.data.MilkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiHelper {
    private const val BASE_URL = "https://api.groq.com/openai/v1/"
    private const val MODEL_ID = "openai/gpt-oss-120b"
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(GroqApiService::class.java)

    interface GroqApiService {
        @POST("chat/completions")
        suspend fun getChatCompletion(
            @Header("Authorization") auth: String,
            @Body body: GroqRequest
        ): Response<GroqResponse>
    }

    fun summarizeElectricity(records: List<Electricity>): String {
        if (records.isEmpty()) return "No records."
        val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return records.joinToString("; ") {
            "Date: ${sdf.format(Date(it.endDate))}, Units: ${it.totalUnits ?: 0}, Rs: ${it.amount ?: 0}"
        }
    }
    fun summarizeMilk(records: List<MilkRecord>): String {
        if (records.isEmpty()) return "No records."
        val sortedRecords = records.sortedWith(compareBy({ it.year }, { it.monthIndex }))
        return sortedRecords.joinToString("; ") {
            "Period: ${it.monthName} ${it.year}, Qty: ${it.totalLiters}L, Rate: Rs ${it.pricePerLiter}/L, Total: Rs ${it.totalAmount}"
        }
    }
    fun summarizeExpenses(records: List<Expense>): String {
        if (records.isEmpty()) return "No records."
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return records.joinToString("; ") {
            "[Date: ${sdf.format(Date(it.date))}, Item: ${it.title}, Info: ${it.description}, Rs: ${it.amount}]"
        }
    }
    fun isError(result: String): Boolean {
        val r = result.lowercase()
        return r.contains("internet") || r.contains("network") ||
                r.contains("limit") || r.contains("overloaded") ||
                r.contains("busy") || r.contains("unavailable") ||
                r.contains("failed")
    }

    private suspend fun callLedgerAi(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val cleanPrompt = prompt
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\\", "/")

            val request = GroqRequest(
                model = MODEL_ID,
                messages = listOf(AiMessage("user", cleanPrompt))
            )
            val response = apiService.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)

            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content ?: "No response from Ledger AI."
            } else {
                val errorDetail = response.errorBody()?.string()
                when (response.code()) {
                    400 -> "Bad Request: Check your JSON format. Details: $errorDetail"
                    401 -> "Authentication Failed: Please check your API Key."
                    429 -> "Limit reached: Ledger AI is cooling down. Please wait a minute."
                    500, 503 -> "Groq Servers are currently overloaded. Try again in 10 seconds."
                    else -> "Ledger AI is currently unavailable (Error ${response.code()})."

                }
            }
        } catch (e: Exception) {
            if (e is UnknownHostException) "No internet connection. Please check your network and try again."
            else "Ledger AI is unavailable. Please try again later."
        }
    }


    suspend fun getInsight(dataType: String, dataSummary: String): String {
        val currentDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())
        val cleanSummary = dataSummary
            .replace(Regex("\\[Last completed month:.*?\\]"), "")
            .trim()

        val domainContext = when (dataType.lowercase()) {
            "electricity" -> "Domain: Electricity consumption and utility billing. Focus on units consumed (kWh), seasonal trends, cost per unit, and energy efficiency."
            "milk" -> "Domain: Household milk consumption and expenditure (completed historical months only). Focus on monthly volume (Liters), rate per liter, consistency, and consumption patterns."
            else -> "Domain: Personal and household expense tracking. Focus on spending distribution across categories, recurring vs discretionary expenses, and budget discipline."
        }

        val prompt = """
        Today's Date: $currentDate.
        $domainContext
        Role: Senior Financial Analyst & Household Budget Specialist.
        Historical Records: $cleanSummary
        
        Task:
        1. Trend Analysis: Concisely analyze historical spending and consumption patterns for $dataType based on the recorded data.
        2. Spikes & Anomalies: Identify any notable shifts, spikes, or drops in cost, rates, or quantities across the periods.
        3. Practical Optimization Tip: Provide one practical, actionable tip to optimize consumption or reduce expenses for this specific category.
        
        CRITICAL RULES:
        - Analyze ONLY the past historical data provided above.
        - DO NOT provide any future predictions, forecasts, or estimated next-month costs or units in this analysis. Predictions will be explicitly requested later by the user if needed.
        - Keep the analysis strictly retrospective, professional, and clear.
        - Use 'PKR' or 'Rs' and clear units ('Liters' or 'Units/kWh').
        - Format: 3 to 4 concise bullet points. Plain text only. No bolding (**), asterisks (*), or markdown headers (#).
    """.trimIndent()

        return callLedgerAi(prompt)
    }

    suspend fun getPrediction(dataType: String, dataSummary: String): String {
        val currentDate = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val predictMonthMatch = Regex("Predict for: (.+?) only\\.").find(dataSummary)
        val predictMonth = predictMonthMatch?.groupValues?.get(1) ?: "next month"
        val cleanSummary = dataSummary
            .replace(Regex("\\[Last completed month:.*?\\]"), "")
            .trim()

        val domainContext = when (dataType.lowercase()) {
            "electricity" -> "Domain: Electricity consumption forecasting. Account for seasonal weather patterns (e.g. heating or cooling demands) and historical billing trends."
            "milk" -> "Domain: Household milk consumption and cost forecasting. Base projections on established daily consumption habits and recent price per liter."
            else -> "Domain: Household expense forecasting. Consider historical monthly expenditure averages, recurring obligations, and recent spending trends."
        }

        val prompt = """
        Current Date: $currentDate.
        $domainContext
        Role: Expert Financial Forecaster.
        Historical Records: $cleanSummary
        
        Target Forecast Period: $predictMonth.
        
        Task:
        1. Header: Start with the header "Prediction for $predictMonth"
        2. Expected Trend: Explain the expected direction for $predictMonth based on historical trends and seasonal or usage patterns.
        3. Baseline Comparison: Compare briefly with the last recorded period to highlight expected changes.
        4. Estimated Cost: Provide a realistic predicted cost in PKR for $predictMonth (e.g., "Estimated Cost: Rs 5,200").
        5. Estimated Quantity: Provide a realistic predicted quantity for $predictMonth if applicable (e.g., "Estimated Quantity: 60 Liters" or "150 Units").
        
        Formatting:
        - Output concise bullet points. Plain text only.
        - Do not use bolding (**), asterisks (*), or markdown headers (#).
        - Use 'Rs' or 'PKR' and standard units clearly.
    """.trimIndent()

        return callLedgerAi(prompt)
    }

    fun formatAiResponse(text: String): String = text.replace(Regex("[#*]"), "").trim()
}


data class GroqRequest(
    val model: String,
    val messages: List<AiMessage>
)

data class AiMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: AiMessage
)