package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.SavedAnimation
import com.example.data.SimulationVariable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: okhttp3.RequestBody
    ): ResponseBody
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

class GeminiSolver {

    suspend fun solveAndCreateSimulation(userPrompt: String): SavedAnimation? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiSolver", "API Key is empty or placeholder!")
            return@withContext null
        }

        val systemInstruction = """
            You are a physics animation and mathematical solver engine. 
            The user will provide a word problem, physical formula, or mass-related scenario.
            Your task is to analyze it, solve it step-by-step ("sequence of the solution"), and output a strict JSON configuration that can be used to animate it.
            
            You must choose one of these standard drawing templates that best represents the physical situation visually:
            - "scale" (for static weighing, density, mass vs weight, balance scales)
            - "spring" (for simple harmonic oscillation, vibration, periodic tension)
            - "sled" (for blocks sliding, acceleration, force, friction, momentum)
            - "rocket" (for launches, burning fuel, mass depletion, thrust acceleration)
            - "particle" (for particle acceleration, relativistic mass dilation, subatomic collisions)
            
            Identify 2 to 5 relevant adjustable variables that control this system. 
            At least one variable must represent the mass of the primary animating body.
            
            Output MUST be valid JSON and ONLY valid JSON. No markdown backticks, no comments, no formatting except the raw JSON string.
            
            JSON Schema:
            {
              "name": "Title of the simulation (e.g., Inertia Cargo)",
              "description": "Short explanation of the physical phenomenon (e.g., how mass resists force).",
              "formulaType": "one of the templates above: 'scale', 'spring', 'sled', 'rocket', 'particle'",
              "formulaExpression": "The mathematical formula (e.g. m = m0 / sqrt(1 - v2/c2))",
              "variables": [
                {
                  "key": "unique_variable_identifier (lowercase, e.g. mass, density, force, speed_ratio, stiffness, fuel_mass)",
                  "value": 15.0,
                  "displayName": "User Friendly Name (e.g., Payload Mass)",
                  "unit": "kg or m/s or N",
                  "min": 1.0,
                  "max": 100.0
                }
              ]
            }
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "Analyze and generate simulation parameters for: $userPrompt")
                }))
            }))
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemInstruction)
                }))
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            val jsonStr = response.string()
            val responseObj = JSONObject(jsonStr)
            val candidates = responseObj.getJSONArray("candidates")
            val text = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val cleanedJson = text.trim()
            val parsedObj = JSONObject(cleanedJson)

            val name = parsedObj.getString("name")
            val description = parsedObj.getString("description")
            val formulaType = parsedObj.getString("formulaType")
            val formulaExpression = parsedObj.getString("formulaExpression")

            val variablesArray = parsedObj.getJSONArray("variables")
            val variablesList = mutableListOf<SimulationVariable>()
            for (i in 0 until variablesArray.length()) {
                val vObj = variablesArray.getJSONObject(i)
                variablesList.add(
                    SimulationVariable(
                        key = vObj.getString("key"),
                        value = vObj.getDouble("value"),
                        displayName = vObj.getString("displayName"),
                        unit = vObj.getString("unit"),
                        min = vObj.getDouble("min"),
                        max = vObj.getDouble("max"),
                        isEditable = true
                    )
                )
            }

            SavedAnimation(
                id = 0,
                name = name,
                description = description,
                formulaType = formulaType,
                formulaExpression = formulaExpression,
                variablesJson = SimulationVariable.serializeList(variablesList)
            )
        } catch (e: Exception) {
            Log.e("GeminiSolver", "Error parsing Gemini response", e)
            null
        }
    }

    suspend fun generateLabReport(
        simName: String,
        formula: String,
        variablesStr: String,
        formatType: String,
        additionalPrompt: String
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiSolver", "API Key is empty or placeholder!")
            return@withContext "Error: API Key is missing. Please add your GEMINI_API_KEY in the Secrets panel."
        }

        val systemInstruction = """
            You are a formal physics lab report and scientific writing assistant.
            The user wants to generate a high-quality, professional academic text based on a simulation run in "Mass Animator".
            
            Simulation Name: $simName
            Mathematical Model: $formula
            Configured Variables: $variablesStr
            Document Format Type: $formatType
            
            Structure the document with beautiful, clear academic sections:
            - TITLE & METADATA
            - ABSTRACT / CONCEPTUAL OVERVIEW
            - THEORETICAL MODEL & FORMULAS (use clear mathematical steps)
            - SIMULATION PARAMETERS (incorporate the exact configured variables given above)
            - DISCUSSION & DYNAMIC BEHAVIOR ANALYSIS (explain how changing these parameters influences the system's states)
            - CONCLUDING CONCLUSION
            
            Tone: Formal, precise, scientific, educational.
            Output: Markdown text with clean headers, bullet points, and equations. Do not output JSON; output beautiful, raw markdown.
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "Write the academic $formatType. Additional instructions: $additionalPrompt")
                }))
            }))
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemInstruction)
                }))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            val jsonStr = response.string()
            val responseObj = JSONObject(jsonStr)
            val candidates = responseObj.getJSONArray("candidates")
            val text = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            text.trim()
        } catch (e: Exception) {
            Log.e("GeminiSolver", "Error generating lab report", e)
            null
        }
    }

    suspend fun explainEquation(
        equationStr: String
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiSolver", "API Key is empty or placeholder!")
            return@withContext "Error: API Key is missing. Please add your GEMINI_API_KEY in the Secrets panel."
        }

        val systemInstruction = """
            You are an expert theoretical physicist, mathematician, and scientific educator.
            The user wants a clear, detailed, yet highly scannable academic analysis of the following equation: $equationStr
            
            Format your response in beautiful, clear Markdown, strictly using these section headers:
            
            # Equation Analysis: $equationStr
            
            ### 1. Conceptual Essence
            Provide a 2-3 sentence overview of what physical phenomenon or mathematical relationship this equation describes and why it is historically or practically significant.
            
            ### 2. Breakdown of Terms
            Deconstruct each symbol, variable, or operator present in the equation (e.g., Ψ, ħ, m, V(x), t, etc.) and explain its physical units and conceptual meaning.
            
            ### 3. Practical Applications & Setup
            Explain where this equation is applied in the physical world (e.g., quantum computers, semiconductor physics, astrophysics, mechanical systems) and what kind of physical experiment or laboratory setup would observe it.
            
            Tone: Academic, inspiring, clear, and highly educational.
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "Analyze the physical equation: $equationStr")
                }))
            }))
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemInstruction)
                }))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.5)
            })
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            val jsonStr = response.string()
            val responseObj = JSONObject(jsonStr)
            val candidates = responseObj.getJSONArray("candidates")
            val text = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            text.trim()
        } catch (e: Exception) {
            Log.e("GeminiSolver", "Error explaining equation", e)
            null
        }
    }
}
