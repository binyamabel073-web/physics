package com.example.data

import kotlinx.coroutines.flow.Flow

class AnimationRepository(private val animationDao: AnimationDao) {

    val allSavedAnimations: Flow<List<SavedAnimation>> = animationDao.getAllAnimations()

    suspend fun insert(animation: SavedAnimation): Long {
        return animationDao.insertAnimation(animation)
    }

    suspend fun deleteById(id: Int) {
        animationDao.deleteAnimationById(id)
    }

    suspend fun ensureDefaultPresetsPopulated() {
        if (animationDao.getCount() == 0) {
            getBuiltInPresets().forEach {
                animationDao.insertAnimation(it)
            }
        }
    }

    fun getBuiltInPresets(): List<SavedAnimation> {
        return listOf(
            SavedAnimation(
                id = -1,
                name = "Density Cube on Scale",
                description = "Observe density and volume dynamics. Tweak material properties and box dimensions to see their direct effect on total mass and weight on an active digital scale.",
                formulaType = "density",
                formulaExpression = "m = ρ * V",
                variablesJson = SimulationVariable.serializeList(
                    listOf(
                        SimulationVariable("density", 7.87, "Density (ρ)", "g/cm³", 0.5, 22.0),
                        SimulationVariable("width", 10.0, "Width (W)", "cm", 1.0, 30.0),
                        SimulationVariable("height", 10.0, "Height (H)", "cm", 1.0, 30.0),
                        SimulationVariable("depth", 10.0, "Depth (D)", "cm", 1.0, 30.0),
                        SimulationVariable("gravity", 9.81, "Gravity (g)", "m/s²", 1.62, 24.79) // Moon gravity to Jupiter gravity
                    )
                )
            ),
            SavedAnimation(
                id = -2,
                name = "Harmonic Spring Oscillator",
                description = "Animate simple harmonic motion. Tweak the mass and spring stiffness to analyze changes in oscillation frequency, period, and visual damping in real time.",
                formulaType = "spring",
                formulaExpression = "f = (1 / 2π) * √(k / m)",
                variablesJson = SimulationVariable.serializeList(
                    listOf(
                        SimulationVariable("mass", 5.0, "Load Mass (m)", "kg", 0.5, 20.0),
                        SimulationVariable("stiffness", 80.0, "Spring Stiffness (k)", "N/m", 10.0, 300.0),
                        SimulationVariable("amplitude", 2.5, "Release Offset (A)", "m", 0.5, 5.0),
                        SimulationVariable("damping", 0.15, "Friction Damping (γ)", "N·s/m", 0.0, 1.0)
                    )
                )
            ),
            SavedAnimation(
                id = -3,
                name = "Newton's Cargo Sled",
                description = "Push a cargo box across a friction runway. Watch the velocity vectors expand and analyze how increasing mass slows down acceleration under a constant force.",
                formulaType = "newton",
                formulaExpression = "a = F_net / m",
                variablesJson = SimulationVariable.serializeList(
                    listOf(
                        SimulationVariable("mass", 15.0, "Sled Mass (m)", "kg", 1.0, 100.0),
                        SimulationVariable("force", 120.0, "Pushing Force (F)", "N", 0.0, 300.0),
                        SimulationVariable("friction_coeff", 0.25, "Friction Coefficient (μ)", "coeff", 0.0, 0.8),
                        SimulationVariable("gravity", 9.81, "Gravity (g)", "m/s²", 9.81, 9.81, false)
                    )
                )
            ),
            SavedAnimation(
                id = -4,
                name = "Variable-Mass Rocket Launch",
                description = "Animate fuel depletion in a vertical launch. Watch fuel weight reduce over time, causing mass to decline and thrust acceleration to surge upwards.",
                formulaType = "rocket",
                formulaExpression = "m(t) = m_dry + m_fuel - r * t",
                variablesJson = SimulationVariable.serializeList(
                    listOf(
                        SimulationVariable("mass_dry", 1200.0, "Dry Shell Mass", "kg", 500.0, 5000.0),
                        SimulationVariable("mass_fuel", 2500.0, "Fuel Capacity", "kg", 100.0, 10000.0),
                        SimulationVariable("burn_rate", 75.0, "Fuel Burn Rate (r)", "kg/s", 10.0, 250.0),
                        SimulationVariable("exhaust_velocity", 2200.0, "Exhaust Velocity (u)", "m/s", 1000.0, 4500.0)
                    )
                )
            ),
            SavedAnimation(
                id = -5,
                name = "Relativistic Particle",
                description = "Accelerate a subatomic mass towards the speed of light. Visualise how physical mass increases as velocity approaches 1.0c, illustrating mass dilation.",
                formulaType = "relativity",
                formulaExpression = "m = m_0 / √(1 - v²/c²)",
                variablesJson = SimulationVariable.serializeList(
                    listOf(
                        SimulationVariable("rest_mass", 1.5, "Rest Mass (m_0)", "MeV/c²", 0.1, 10.0),
                        SimulationVariable("speed_ratio", 0.75, "Velocity Ratio (v/c)", "c", 0.0, 0.99)
                    )
                )
            ),
            SavedAnimation(
                id = -6,
                name = "Quantum Wave Function",
                description = "Simulate a quantum particle inside a 1D potential well. Tweak the quantum energy levels, particle mass, potential barrier, and superposition ratio to visualize the real, imaginary, and probability density wave states in real-time.",
                formulaType = "quantum",
                formulaExpression = "iħ * ∂Ψ/∂t = ĤΨ",
                variablesJson = SimulationVariable.serializeList(
                    listOf(
                        SimulationVariable("energy_level", 1.0, "Energy Level (n)", "quantum_n", 1.0, 5.0),
                        SimulationVariable("superposition", 0.0, "Superposition Ratio (c_2)", "ratio", 0.0, 1.0),
                        SimulationVariable("well_width", 20.0, "Potential Well Width (L)", "nm", 10.0, 30.0),
                        SimulationVariable("particle_mass", 1.0, "Particle Mass (m)", "m_e", 0.5, 5.0)
                    )
                )
            )
        )
    }
}
