import com.example.smartledger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiHelper {
    private const val BASE_URL = "https://api.groq.com/openai/v1/"
    private const val MODEL_ID = "llama-3.3-70b-versatile"
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
        ): retrofit2.Response<GroqResponse>
    }

    // --- RESTORED SUMMARIZATION FUNCTIONS ---

    fun summarizeElectricity(records: List<com.example.smartledger.data.Electricity>): String {
        if (records.isEmpty()) return "No records."
        val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return records.joinToString("; ") {
            "Date: ${sdf.format(Date(it.endDate))}, Units: ${it.totalUnits ?: 0}, Rs: ${it.amount ?: 0}"
        }
    }

    fun summarizeMilk(records: List<com.example.smartledger.data.MilkRecord>): String {
        if (records.isEmpty()) return "No records."
        val sortedRecords = records.sortedWith(compareBy({ it.year }, { it.monthIndex }))
        return sortedRecords.joinToString("; ") {
            "Period: ${it.monthName} ${it.year}, Qty: ${it.totalLiters}L, Rate: Rs ${it.pricePerLiter}/L, Total: Rs ${it.totalAmount}"
        }
    }

    fun summarizeExpenses(records: List<com.example.smartledger.data.Expense>): String {
        if (records.isEmpty()) return "No records."
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return records.joinToString("; ") {
            "[Date: ${sdf.format(Date(it.date))}, Item: ${it.title}, Info: ${it.description}, Rs: ${it.amount}]"
        }
    }

    // Unified Error Checker
    fun isError(result: String): Boolean {
        val r = result.lowercase()
        return r.contains("internet") || r.contains("network") ||
                r.contains("limit") || r.contains("overloaded") ||
                r.contains("busy") || r.contains("unavailable") ||
                r.contains("failed")
    }

    private suspend fun callLedgerAi(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            // CLEAN THE PROMPT: Remove characters that break JSON
            val cleanPrompt = prompt
                .replace("\"", "'")  // Replace double quotes with single quotes
                .replace("\n", " ")  // Remove newlines
                .replace("\r", " ")
                .replace("\\", "/")  // Replace backslashes

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
            if (e is java.net.UnknownHostException) "No internet connection. Please check your network and try again."
            else "Ledger AI is unavailable. Please try again later."
        }
    }


    suspend fun getInsight(dataType: String, dataSummary: String): String {
        val currentDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())

        // Dynamic context based on what the user is looking at
        val localContext = when (dataType.lowercase()) {
            "electricity" -> "Location: Karachi. Utility: K-Electric. Focus on units (kWh), peak/off-peak logic, and weather impact on cooling."
            "milk" -> "Location: Karachi. Context: Daily milk consumption and pricing. Focus on price per liter and monthly consumption stability."
            else -> "Location: Karachi. Context: General expenses and budgets. Focus on budgeting and inflation in Pakistan."
        }

        val prompt = """
        Today's Date: $currentDate.
        $localContext
        Role: Expert Financial Analyst.
        Data: $dataSummary
        
        Task:
        1. Analyze the trend for $dataType.
        2. Identify any unusual spikes in cost or quantity.
        3. Provide one Karachi-specific tip to optimize this spending.
        
        Format: Use 3-4 bullet points. Use 'PKR' and 'Units/Liters' clearly. No bolding or hashtags.
    """.trimIndent()

        return callLedgerAi(prompt)
    }

    suspend fun getPrediction(dataType: String, dataSummary: String): String {
        val currentDate = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

        val localContext = when (dataType.lowercase()) {
            "electricity" -> "Karachi weather context: Heatwaves in summer, lower usage in winter. K-Electric billing."
            "milk" -> "Context: Standard milk price fluctuations in Karachi dairy markets."
            else -> "Context: General expenses and budgets in Karachi market trends."
        }

        val prompt = """
        Today: $currentDate.
        $localContext
        Role: Expert Financial Forecaster.
        History: $dataSummary
        
        Task:
        1. Explain the prediction based on the upcoming month's season or trend.
        2. Compare it briefly to the previous month's actual data.
        3. Predict next month's total cost in PKR (e.g., "Rs 5,200").
        4. Predict quantity (e.g., "150 Units" or "60 Liters").
        
        Format: Concise bullet points. Plain text only.
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