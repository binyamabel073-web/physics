package com.example.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AnimationRepository
import com.example.data.SavedAnimation
import com.example.data.SimulationVariable
import com.example.network.GeminiSolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.*

data class SolutionStep(
    val title: String,
    val formula: String,
    val calculation: String,
    val result: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AnimationRepository(database.animationDao())

    val savedAnimations: StateFlow<List<SavedAnimation>> = repository.allSavedAnimations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedAnimation = MutableStateFlow<SavedAnimation?>(null)
    val selectedAnimation: StateFlow<SavedAnimation?> = _selectedAnimation.asStateFlow()

    private val _variables = MutableStateFlow<List<SimulationVariable>>(emptyList())
    val variables: StateFlow<List<SimulationVariable>> = _variables.asStateFlow()

    private val _solutionSteps = MutableStateFlow<List<SolutionStep>>(emptyList())
    val solutionSteps: StateFlow<List<SolutionStep>> = _solutionSteps.asStateFlow()

    // Simulation playback controls
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _simTime = MutableStateFlow(0.0)
    val simTime: StateFlow<Double> = _simTime.asStateFlow()

    // Active visualization option
    private val _activeVisualization = MutableStateFlow("standard")
    val activeVisualization: StateFlow<String> = _activeVisualization.asStateFlow()

    // Formula parser validation result
    private val _formulaValidationResult = MutableStateFlow<FormulaValidationResult?>(null)
    val formulaValidationResult: StateFlow<FormulaValidationResult?> = _formulaValidationResult.asStateFlow()

    // History data for plotting graphs (Time, Value)
    private val _massHistory = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val massHistory: StateFlow<List<Pair<Double, Double>>> = _massHistory.asStateFlow()

    private val _paramHistory = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val paramHistory: StateFlow<List<Pair<Double, Double>>> = _paramHistory.asStateFlow()

    // AI custom query state
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // AI Writer states
    private val _writerLoading = MutableStateFlow(false)
    val writerLoading: StateFlow<Boolean> = _writerLoading.asStateFlow()

    private val _writerError = MutableStateFlow<String?>(null)
    val writerError: StateFlow<String?> = _writerError.asStateFlow()

    private val _generatedReport = MutableStateFlow<String?>(null)
    val generatedReport: StateFlow<String?> = _generatedReport.asStateFlow()

    // Equation Composer states
    private val _equationExplanationLoading = MutableStateFlow(false)
    val equationExplanationLoading: StateFlow<Boolean> = _equationExplanationLoading.asStateFlow()

    private val _equationExplanationError = MutableStateFlow<String?>(null)
    val equationExplanationError: StateFlow<String?> = _equationExplanationError.asStateFlow()

    private val _equationExplanation = MutableStateFlow<String?>(null)
    val equationExplanation: StateFlow<String?> = _equationExplanation.asStateFlow()

    private val geminiSolver = GeminiSolver()
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDefaultPresetsPopulated()
            // Load first built-in preset as default
            val defaults = repository.getBuiltInPresets()
            if (defaults.isNotEmpty()) {
                selectAnimation(defaults[0])
            }
        }
        startSimulationLoop()
    }

    fun getBuiltInPresets(): List<SavedAnimation> {
        return repository.getBuiltInPresets()
    }

    fun selectAnimation(animation: SavedAnimation) {
        _selectedAnimation.value = animation
        val vars = SimulationVariable.deserializeList(animation.variablesJson)
        _variables.value = vars
        _simTime.value = 0.0
        _massHistory.value = emptyList()
        _paramHistory.value = emptyList()
        calculateSolutionSequence()
    }

    fun updateVariableValue(key: String, newValue: Double) {
        val updated = _variables.value.map {
            if (it.key == key) it.copy(value = newValue) else it
        }
        _variables.value = updated
        _simTime.value = 0.0
        _massHistory.value = emptyList()
        _paramHistory.value = emptyList()
        calculateSolutionSequence()
    }

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
    }

    fun resetSimulation() {
        _isPlaying.value = false
        _simTime.value = 0.0
        _massHistory.value = emptyList()
        _paramHistory.value = emptyList()
        calculateSolutionSequence()
    }

    fun deleteSavedAnimation(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun saveCurrentConfiguration(customName: String) {
        val current = _selectedAnimation.value ?: return
        viewModelScope.launch {
            val toSave = SavedAnimation(
                name = customName,
                description = "Custom adjusted version of ${current.name}.",
                formulaType = current.formulaType,
                formulaExpression = current.formulaExpression,
                variablesJson = SimulationVariable.serializeList(_variables.value)
            )
            repository.insert(toSave)
        }
    }

    // AI solver and dynamic simulator generator
    fun solveWithAI(query: String, onSuccess: () -> Unit) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _aiLoading.value = true
            _aiError.value = null
            try {
                val animation = geminiSolver.solveAndCreateSimulation(query)
                if (animation != null) {
                    // Temporarily insert in database or just select directly!
                    val id = repository.insert(animation)
                    val inserted = animation.copy(id = id.toInt())
                    selectAnimation(inserted)
                    onSuccess()
                } else {
                    _aiError.value = "Failed to construct the simulation model. Please check your prompt or API Key."
                }
            } catch (e: Exception) {
                _aiError.value = "Error: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    private fun startSimulationLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val stepMs = 30L
            while (true) {
                if (_isPlaying.value) {
                    val currentT = _simTime.value
                    // Advance time
                    val nextT = currentT + (stepMs / 1000.0)
                    
                    // Cap and repeat rocket fuel depletion, or let other oscillations cycle
                    val maxT = when (_selectedAnimation.value?.formulaType) {
                        "rocket" -> {
                            val vars = _variables.value
                            val mFuel = vars.firstOrNull { it.key == "mass_fuel" }?.value ?: 0.0
                            val burnRate = vars.firstOrNull { it.key == "burn_rate" }?.value ?: 1.0
                            val tBurn = mFuel / burnRate
                            tBurn + 2.0 // let rocket float 2s after burn
                        }
                        "newton" -> 8.0 // 8s run
                        else -> 15.0 // loop limit
                    }

                    if (nextT > maxT) {
                        _simTime.value = 0.0
                        _massHistory.value = emptyList()
                        _paramHistory.value = emptyList()
                    } else {
                        _simTime.value = nextT
                        recordHistoryPoints(nextT)
                    }
                }
                delay(stepMs)
            }
        }
    }

    private fun recordHistoryPoints(t: Double) {
        val type = _selectedAnimation.value?.formulaType ?: return
        val vars = _variables.value
        
        when (type) {
            "density" -> {
                val density = vars.firstOrNull { it.key == "density" }?.value ?: 1.0
                val w = vars.firstOrNull { it.key == "width" }?.value ?: 10.0
                val h = vars.firstOrNull { it.key == "height" }?.value ?: 10.0
                val d = vars.firstOrNull { it.key == "depth" }?.value ?: 10.0
                val mass = (density * (w * h * d)) / 1000.0 // kg
                addHistoryPoint(t, mass, w * h * d)
            }
            "spring" -> {
                val mass = vars.firstOrNull { it.key == "mass" }?.value ?: 1.0
                val k = vars.firstOrNull { it.key == "stiffness" }?.value ?: 10.0
                val amp = vars.firstOrNull { it.key == "amplitude" }?.value ?: 1.0
                val damping = vars.firstOrNull { it.key == "damping" }?.value ?: 0.0
                
                val omega0 = sqrt(k / mass)
                val alpha = damping / (2 * mass)
                val omegaD = if (omega0 > alpha) sqrt(omega0.pow(2) - alpha.pow(2)) else 0.0
                val pos = amp * exp(-alpha * t) * cos(omegaD * t)
                addHistoryPoint(t, mass, pos)
            }
            "newton" -> {
                val mass = vars.firstOrNull { it.key == "mass" }?.value ?: 1.0
                val force = vars.firstOrNull { it.key == "force" }?.value ?: 0.0
                val mu = vars.firstOrNull { it.key == "friction_coeff" }?.value ?: 0.0
                
                val fN = mass * 9.81
                val fFric = mu * fN
                val fNet = max(0.0, force - fFric)
                val acc = fNet / mass
                val vel = acc * t
                addHistoryPoint(t, mass, vel)
            }
            "rocket" -> {
                val mDry = vars.firstOrNull { it.key == "mass_dry" }?.value ?: 1000.0
                val mFuel = vars.firstOrNull { it.key == "mass_fuel" }?.value ?: 2000.0
                val burnRate = vars.firstOrNull { it.key == "burn_rate" }?.value ?: 50.0
                val u = vars.firstOrNull { it.key == "exhaust_velocity" }?.value ?: 2000.0
                
                val tBurn = mFuel / burnRate
                val currentMass = if (t <= tBurn) {
                    (mDry + mFuel) - (burnRate * t)
                } else {
                    mDry
                }
                
                // Acceleration calculation
                val thrust = if (t <= tBurn) u * burnRate else 0.0
                val weight = currentMass * 9.81
                val fNet = max(0.0, thrust - weight)
                val acc = fNet / currentMass
                addHistoryPoint(t, currentMass, acc)
            }
            "relativity" -> {
                val restMass = vars.firstOrNull { it.key == "rest_mass" }?.value ?: 1.0
                val beta = vars.firstOrNull { it.key == "speed_ratio" }?.value ?: 0.0
                val vRatio = beta * (t / 15.0) // let it speed up dynamically as time passes
                val gamma = 1.0 / sqrt(1.0 - vRatio.pow(2))
                val relMass = gamma * restMass
                addHistoryPoint(t, relMass, vRatio)
            }
            else -> {
                // For custom AI models, draw standard mass vs time
                val massVar = vars.firstOrNull { it.key.contains("mass") }?.value ?: 1.0
                addHistoryPoint(t, massVar, sin(t) * 2.0)
            }
        }
    }

    private fun addHistoryPoint(t: Double, mass: Double, param: Double) {
        val currentMassHistory = _massHistory.value.toMutableList()
        val currentParamHistory = _paramHistory.value.toMutableList()
        
        currentMassHistory.add(Pair(t, mass))
        currentParamHistory.add(Pair(t, param))
        
        // Cap history length for performance
        if (currentMassHistory.size > 150) {
            currentMassHistory.removeAt(0)
            currentParamHistory.removeAt(0)
        }
        
        _massHistory.value = currentMassHistory
        _paramHistory.value = currentParamHistory
    }

    fun calculateSolutionSequence() {
        val type = _selectedAnimation.value?.formulaType ?: return
        val vars = _variables.value
        val steps = mutableListOf<SolutionStep>()

        when (type) {
            "density" -> {
                val density = vars.firstOrNull { it.key == "density" }?.value ?: 1.0
                val w = vars.firstOrNull { it.key == "width" }?.value ?: 10.0
                val h = vars.firstOrNull { it.key == "height" }?.value ?: 10.0
                val d = vars.firstOrNull { it.key == "depth" }?.value ?: 10.0
                val g = vars.firstOrNull { it.key == "gravity" }?.value ?: 9.81

                val vol = w * h * d
                val massG = density * vol
                val massKg = massG / 1000.0
                val weightN = massKg * g

                steps.add(SolutionStep(
                    "1. Calculate Cube Volume (V)",
                    "V = W × H × D",
                    "V = %.2f cm × %.2f cm × %.2f cm".format(w, h, d),
                    "V = %.2f cm³".format(vol)
                ))
                steps.add(SolutionStep(
                    "2. Calculate Mass in Grams (m_g)",
                    "m_g = ρ × V",
                    "m_g = %.3f g/cm³ × %.2f cm³".format(density, vol),
                    "m_g = %.2f g".format(massG)
                ))
                steps.add(SolutionStep(
                    "3. Convert Mass to Kilograms (m_kg)",
                    "m_kg = m_g / 1000",
                    "m_kg = %.2f g / 1000".format(massG),
                    "m_kg = %.4f kg".format(massKg)
                ))
                steps.add(SolutionStep(
                    "4. Calculate Weight Force on scale (F_w)",
                    "F_w = m_kg × g",
                    "F_w = %.4f kg × %.2f m/s²".format(massKg, g),
                    "F_w = %.2f N (Newtons)".format(weightN)
                ))
            }
            "spring" -> {
                val mass = vars.firstOrNull { it.key == "mass" }?.value ?: 1.0
                val k = vars.firstOrNull { it.key == "stiffness" }?.value ?: 10.0
                val amp = vars.firstOrNull { it.key == "amplitude" }?.value ?: 1.0
                val damping = vars.firstOrNull { it.key == "damping" }?.value ?: 0.0

                val omega0 = sqrt(k / mass)
                val alpha = damping / (2 * mass)
                val omegaD = if (omega0 > alpha) sqrt(omega0.pow(2) - alpha.pow(2)) else 0.0
                val period = if (omegaD > 0) (2 * PI) / omegaD else Double.POSITIVE_INFINITY
                val freq = if (period != Double.POSITIVE_INFINITY) 1.0 / period else 0.0

                steps.add(SolutionStep(
                    "1. Calculate Undamped Natural Frequency (ω₀)",
                    "ω₀ = √(k / m)",
                    "ω₀ = √(%.2f N/m / %.2f kg)".format(k, mass),
                    "ω₀ = %.3f rad/s".format(omega0)
                ))
                steps.add(SolutionStep(
                    "2. Calculate Damping Coefficient (α)",
                    "α = γ / (2m)",
                    "α = %.2f / (2 × %.2f kg)".format(damping, mass),
                    "α = %.4f s⁻¹".format(alpha)
                ))
                steps.add(SolutionStep(
                    "3. Calculate Damped Angular Frequency (ω_d)",
                    "ω_d = √(ω₀² - α²)",
                    "ω_d = √(%.3f² - %.4f²)".format(omega0, alpha),
                    if (omega0 > alpha) "ω_d = %.3f rad/s (Underdamped)".format(omegaD) else "ω_d = 0.00 rad/s (Overdamped/Critical)"
                ))
                steps.add(SolutionStep(
                    "4. Calculate Period of Oscillation (T)",
                    "T = 2π / ω_d",
                    "T = 2 × 3.1415 / %.3f".format(omegaD),
                    if (omegaD > 0) "T = %.3f seconds".format(period) else "T = ∞ (No oscillation)"
                ))
                steps.add(SolutionStep(
                    "5. Calculate Frequency (f)",
                    "f = 1 / T",
                    "f = 1 / %.3f".format(period),
                    if (omegaD > 0) "f = %.3f Hz (oscillations/sec)".format(freq) else "f = 0.00 Hz"
                ))
            }
            "newton" -> {
                val mass = vars.firstOrNull { it.key == "mass" }?.value ?: 1.0
                val force = vars.firstOrNull { it.key == "force" }?.value ?: 0.0
                val mu = vars.firstOrNull { it.key == "friction_coeff" }?.value ?: 0.0
                val g = 9.81

                val fN = mass * g
                val fFric = mu * fN
                val fNet = max(0.0, force - fFric)
                val acc = fNet / mass

                steps.add(SolutionStep(
                    "1. Calculate Normal Force (F_N)",
                    "F_N = m × g",
                    "F_N = %.2f kg × 9.81 m/s²".format(mass),
                    "F_N = %.2f N".format(fN)
                ))
                steps.add(SolutionStep(
                    "2. Calculate Max Friction Resistance Force (F_fric)",
                    "F_fric = μ × F_N",
                    "F_fric = %.2f × %.2f N".format(mu, fN),
                    "F_fric = %.2f N".format(fFric)
                ))
                steps.add(SolutionStep(
                    "3. Calculate Net Applied Driving Force (F_net)",
                    "F_net = F_applied - F_fric",
                    "F_net = %.2f N - %.2f N".format(force, fFric),
                    if (force > fFric) "F_net = %.2f N (Sled Moves!)".format(fNet) else "F_net = 0.00 N (Static Friction Prevents Motion)"
                ))
                steps.add(SolutionStep(
                    "4. Calculate Acceleration (a)",
                    "a = F_net / m",
                    "a = %.2f N / %.2f kg".format(fNet, mass),
                    "a = %.3f m/s²".format(acc)
                ))
            }
            "rocket" -> {
                val mDry = vars.firstOrNull { it.key == "mass_dry" }?.value ?: 1000.0
                val mFuel = vars.firstOrNull { it.key == "mass_fuel" }?.value ?: 2000.0
                val burnRate = vars.firstOrNull { it.key == "burn_rate" }?.value ?: 50.0
                val u = vars.firstOrNull { it.key == "exhaust_velocity" }?.value ?: 2000.0
                val g = 9.81

                val totalM = mDry + mFuel
                val thrust = u * burnRate
                val tBurn = mFuel / burnRate
                val initialWeight = totalM * g
                val launchSucceeds = thrust > initialWeight

                steps.add(SolutionStep(
                    "1. Calculate Lift-off Wet Mass (m₀)",
                    "m₀ = m_dry + m_fuel",
                    "m₀ = %.1f kg + %.1f kg".format(mDry, mFuel),
                    "m₀ = %.1f kg".format(totalM)
                ))
                steps.add(SolutionStep(
                    "2. Calculate Engine Thrust Force (F_thrust)",
                    "F_thrust = u × r",
                    "F_thrust = %.1f m/s × %.1f kg/s".format(u, burnRate),
                    "F_thrust = %.1f N".format(thrust)
                ))
                steps.add(SolutionStep(
                    "3. Calculate Burn Fuel Duration (t_burn)",
                    "t_burn = m_fuel / r",
                    "t_burn = %.1f kg / %.1f kg/s".format(mFuel, burnRate),
                    "t_burn = %.2f seconds".format(tBurn)
                ))
                steps.add(SolutionStep(
                    "4. Evaluate Initial Thrust-to-Weight Ratio",
                    "Ratio = F_thrust / (m₀ × g)",
                    "Ratio = %.1f N / (%.1f kg × 9.81)".format(thrust, totalM),
                    "Ratio = %.2f (%s)".format(thrust / initialWeight, if (launchSucceeds) "LIFTOFF SUCCESSFUL!" else "LAUNCH FAILED: WEAK THRUST")
                ))
            }
            "relativity" -> {
                val restMass = vars.firstOrNull { it.key == "rest_mass" }?.value ?: 1.0
                val speedRatio = vars.firstOrNull { it.key == "speed_ratio" }?.value ?: 0.5

                val gamma = 1.0 / sqrt(1.0 - speedRatio.pow(2))
                val dilatedMass = gamma * restMass
                val kineticEnergy = (gamma - 1) * restMass

                steps.add(SolutionStep(
                    "1. Calculate Lorentz Factor (γ)",
                    "γ = 1 / √(1 - β²)",
                    "γ = 1 / √(1 - %.3f²)".format(speedRatio),
                    "γ = %.4f".format(gamma)
                ))
                steps.add(SolutionStep(
                    "2. Calculate Relativistic Mass (m)",
                    "m = γ × m₀",
                    "m = %.4f × %.2f MeV/c²".format(gamma, restMass),
                    "m = %.4f MeV/c²".format(dilatedMass)
                ))
                steps.add(SolutionStep(
                    "3. Calculate Kinetic Energy (E_k)",
                    "E_k = (γ - 1) × m₀ c²",
                    "E_k = (%.4f - 1) × %.2f MeV".format(gamma, restMass),
                    "E_k = %.4f MeV".format(kineticEnergy)
                ))
            }
            else -> {
                // Generalized/Custom step
                val massVar = vars.firstOrNull { it.key.contains("mass") }?.value ?: 10.0
                steps.add(SolutionStep(
                    "1. Extract Primary System Mass",
                    "M = input_value",
                    "Assigned from AI-generated parameters",
                    "M = %.2f kg".format(massVar)
                ))
                steps.add(SolutionStep(
                    "2. Solution Complete",
                    "F(t) = m * a(t)",
                    "Mathematical model solved successfully",
                    "Ready to animate dynamic motion."
                ))
            }
        }
        _solutionSteps.value = steps
    }

    fun setVisualizationStyle(style: String) {
        _activeVisualization.value = style
    }

    fun setSimulationTime(newTime: Double) {
        _simTime.value = newTime.coerceAtLeast(0.0)
        // Pause playback so scrubbing feels direct and doesn't fight the loop
        _isPlaying.value = false
        recordHistoryPoints(newTime)
    }

    fun getMaxTime(): Double {
        return when (_selectedAnimation.value?.formulaType) {
            "rocket" -> {
                val vars = _variables.value
                val mFuel = vars.firstOrNull { it.key == "mass_fuel" }?.value ?: 0.0
                val burnRate = vars.firstOrNull { it.key == "burn_rate" }?.value ?: 1.0
                val tBurn = mFuel / burnRate
                tBurn + 2.0
            }
            "newton" -> 8.0
            else -> 15.0
        }
    }

    fun validateAndLoadFormula(formulaStr: String) {
        val result = validateFormula(formulaStr)
        _formulaValidationResult.value = result
        if (result is FormulaValidationResult.Success) {
            val customAnim = SavedAnimation(
                id = 9999,
                name = "Math Sandbox",
                description = "Custom user-defined formula.",
                formulaType = "custom",
                formulaExpression = formulaStr,
                variablesJson = "t:0.0:Time:s:0.0:15.0:true"
            )
            selectAnimation(customAnim)
        }
    }

    fun clearValidationState() {
        _formulaValidationResult.value = null
    }

    fun generateReport(formatType: String, additionalPrompt: String) {
        val currentAnim = _selectedAnimation.value ?: return
        val currentVars = _variables.value
        val varsStr = currentVars.joinToString(", ") { "${it.displayName}: ${it.value} ${it.unit}" }
        
        viewModelScope.launch {
            _writerLoading.value = true
            _writerError.value = null
            _generatedReport.value = null
            try {
                val report = geminiSolver.generateLabReport(
                    simName = currentAnim.name,
                    formula = currentAnim.formulaExpression,
                    variablesStr = varsStr,
                    formatType = formatType,
                    additionalPrompt = additionalPrompt
                )
                if (report != null) {
                    _generatedReport.value = report
                } else {
                    _writerError.value = "Failed to generate report from Gemini."
                }
            } catch (e: Exception) {
                _writerError.value = "Error: ${e.localizedMessage}"
            } finally {
                _writerLoading.value = false
            }
        }
    }

    fun clearWriterState() {
        _generatedReport.value = null
        _writerError.value = null
    }

    fun explainComposedEquation(equation: String) {
        viewModelScope.launch {
            _equationExplanationLoading.value = true
            _equationExplanationError.value = null
            _equationExplanation.value = null
            try {
                val explanation = geminiSolver.explainEquation(equation)
                if (explanation != null) {
                    _equationExplanation.value = explanation
                } else {
                    _equationExplanationError.value = "Failed to explain equation from Gemini."
                }
            } catch (e: Exception) {
                _equationExplanationError.value = "Error: ${e.localizedMessage}"
            } finally {
                _equationExplanationLoading.value = false
            }
        }
    }

    fun clearExplanationState() {
        _equationExplanation.value = null
        _equationExplanationError.value = null
    }
}

sealed class FormulaValidationResult {
    data class Success(val parsedExpression: String) : FormulaValidationResult()
    data class Error(val message: String) : FormulaValidationResult()
}

fun validateFormula(formula: String): FormulaValidationResult {
    if (formula.isBlank()) {
        return FormulaValidationResult.Error("Formula cannot be empty")
    }
    
    // Check balanced parentheses
    var openCount = 0
    for (char in formula) {
        if (char == '(') openCount++
        if (char == ')') {
            openCount--
            if (openCount < 0) {
                return FormulaValidationResult.Error("Mismatched parentheses: closing parenthesis ')' has no matching opening parenthesis")
            }
        }
    }
    if (openCount > 0) {
        return FormulaValidationResult.Error("Mismatched parentheses: $openCount unclosed opening parenthesis '('")
    }

    // Check invalid characters
    val allowedPattern = Regex("[0-9a-zA-Z\\+\\-\\*/\\^\\(\\)\\s\\.\\,]+")
    if (!allowedPattern.matches(formula)) {
        val invalidChars = formula.filter { !Regex("[0-9a-zA-Z\\+\\-\\*/\\^\\(\\)\\s\\.\\,]").matches(it.toString()) }.toSet()
        return FormulaValidationResult.Error("Invalid characters found: ${invalidChars.joinToString(", ")}")
    }

    // Check adjacent operators or trailing operator
    val trimmed = formula.trim()
    val operators = setOf('+', '-', '*', '/', '^')
    if (operators.contains(trimmed.first()) && trimmed.first() != '-') {
        return FormulaValidationResult.Error("Formula cannot start with operator: '${trimmed.first()}'")
    }
    if (operators.contains(trimmed.last())) {
        return FormulaValidationResult.Error("Formula cannot end with trailing operator: '${trimmed.last()}'")
    }

    // Regex for basic syntax checking of operator sequences, e.g. "++", "* /" etc.
    val cleaned = trimmed.replace("\\s+".toRegex(), "")
    if (cleaned.contains("++") || cleaned.contains("**") || cleaned.contains("//") || cleaned.contains("^^") ||
        cleaned.contains("+*") || cleaned.contains("+/") || cleaned.contains("+^") ||
        cleaned.contains("-*") || cleaned.contains("-/") || cleaned.contains("-^") ||
        cleaned.contains("*+") || cleaned.contains("*/") || cleaned.contains("*^") ||
        cleaned.contains("/+") || cleaned.contains("/*") || cleaned.contains("/^") ||
        cleaned.contains("^+") || cleaned.contains("^*") || cleaned.contains("^/")
    ) {
        return FormulaValidationResult.Error("Invalid operator sequence detected")
    }

    // Tokenize words to verify allowed variables and functions
    val wordRegex = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    val foundWords = wordRegex.findAll(formula).map { it.value }.toList()
    val allowedWords = setOf(
        "t", "x", "y", "m", "g", "pi", "PI", "e", "E",
        "sin", "cos", "tan", "sqrt", "exp", "log", "abs", "pow"
    )
    val unrecognized = foundWords.filter { !allowedWords.contains(it.lowercase()) }
    if (unrecognized.isNotEmpty()) {
        return FormulaValidationResult.Error(
            "Unrecognized variable(s) or function(s): ${unrecognized.joinToString(", ")}. " +
            "Allowed variables: 't' (time), 'x', 'y'. Supported functions: sin, cos, tan, sqrt, exp, log, abs"
        )
    }

    return FormulaValidationResult.Success(formula)
}

fun evaluateFormula(formula: String, t: Double): Double {
    return try {
        val expr = formula.replace("pi", PI.toString(), ignoreCase = true)
            .replace("e", E.toString(), ignoreCase = true)
            .replace("\\s+".toRegex(), "")
        
        object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < expr.length) expr[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expr.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else break
                }
                return x
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        x = if (divisor != 0.0) x / divisor else 0.0
                    }
                    else break
                }
                return x
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = expr.substring(startPos, pos).toDouble()
                } else if (ch >= 'a'.code && ch <= 'z'.code) {
                    while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                    val func = expr.substring(startPos, pos)
                    if (func == "t") {
                        x = t
                    } else {
                        x = parseFactor()
                        x = when (func) {
                            "sin" -> sin(x)
                            "cos" -> cos(x)
                            "tan" -> tan(x)
                            "sqrt" -> if (x >= 0.0) sqrt(x) else 0.0
                            "exp" -> exp(x)
                            "log" -> if (x > 0.0) ln(x) else 0.0
                            "abs" -> abs(x)
                            else -> throw RuntimeException("Unknown function: $func")
                        }
                    }
                } else {
                    throw RuntimeException("Unexpected character: " + ch.toChar())
                }

                if (eat('^'.code)) x = x.pow(parseFactor())

                return x
            }
        }.parse()
    } catch (e: Exception) {
        0.0
    }
}
