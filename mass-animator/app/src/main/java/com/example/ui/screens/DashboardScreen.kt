package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedAnimation
import com.example.data.SimulationVariable
import com.example.ui.MainViewModel
import com.example.ui.SolutionStep
import com.example.ui.FormulaValidationResult
import com.example.ui.evaluateFormula
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedAnim by viewModel.selectedAnimation.collectAsStateWithLifecycle()
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    val steps by viewModel.solutionSteps.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val simTime by viewModel.simTime.collectAsStateWithLifecycle()
    val massHistory by viewModel.massHistory.collectAsStateWithLifecycle()
    val paramHistory by viewModel.paramHistory.collectAsStateWithLifecycle()
    val activeVisualization by viewModel.activeVisualization.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    if (selectedAnim == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val anim = selectedAnim!!

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = anim.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = anim.formulaExpression,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            saveName = "${anim.name} Custom"
                            showSaveDialog = true
                        },
                        modifier = Modifier.testTag("save_config_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Configuration")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Animation Canvas Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(4.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1C1E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Drawing Canvas
                        PhysicsAnimationCanvas(
                            formulaType = anim.formulaType,
                            formulaExpression = anim.formulaExpression,
                            visualizationStyle = activeVisualization,
                            variables = variables,
                            time = simTime,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Particle or Text Overlay "Simulation Active"
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SIMULATION ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = Color.LightGray
                            )
                        }

                        // Playback Control & Scrubbing Timeline HUD Overlay
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Timeline Slider Row
                            val maxT = viewModel.getMaxTime()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "0.0s",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = simTime.toFloat(),
                                    onValueChange = { viewModel.setSimulationTime(it.toDouble()) },
                                    valueRange = 0f..maxT.toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .testTag("simulation_scrubber"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                                Text(
                                    text = "%.1fs".format(maxT),
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.togglePlayback() },
                                        modifier = Modifier.testTag("play_pause_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.resetSimulation() },
                                        modifier = Modifier.testTag("reset_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Reset",
                                            tint = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = "TIME STEP: %.2f / %.2fs".format(simTime, maxT),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 1.5. Visualization Style Selector Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Visualization Style",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toggle rendering overlays to visualize physical distributions, gradients, or flow fields.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "standard" to "Standard",
                                "heatmap" to "Heatmap",
                                "vector" to "Vector Field",
                                "particles" to "Particles"
                            ).forEach { (style, label) ->
                                val isSelected = activeVisualization == style
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setVisualizationStyle(style) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("viz_chip_$style")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Variable Sliders Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Interactive Parameters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        variables.forEach { variable ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = variable.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${"%.2f".format(variable.value)} ${variable.unit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (variable.isEditable) {
                                    Slider(
                                        value = variable.value.toFloat(),
                                        onValueChange = { viewModel.updateVariableValue(variable.key, it.toDouble()) },
                                        valueRange = variable.min.toFloat()..variable.max.toFloat(),
                                        modifier = Modifier.testTag("slider_${variable.key}")
                                    )
                                } else {
                                    Text(
                                        text = "Constant Value",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2.5. Custom Formula Lab Card
            item {
                var formulaInput by remember { mutableStateOf("") }
                val validationResult by viewModel.formulaValidationResult.collectAsStateWithLifecycle()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Formula Lab & Math Sandbox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter a custom mathematical function of time (t) to dynamically simulate any mass distribution in real-time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = formulaInput,
                            onValueChange = { 
                                formulaInput = it 
                                viewModel.clearValidationState()
                            },
                            label = { Text("Mass Formula f(t)") },
                            placeholder = { Text("e.g., 5 * sin(t) + 10") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("formula_input_field"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (validationResult is FormulaValidationResult.Success) {
                                    Icon(Icons.Default.CheckCircle, "Valid", tint = Color(0xFF10B981))
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Reference cheat-sheet for math tokens
                        Text(
                            text = "Allowed Tokens: t, pi, e, +, -, *, /, ^, sin(), cos(), tan(), sqrt(), exp(), abs()",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Validation Message Banner
                        AnimatedVisibility(visible = validationResult != null) {
                            validationResult?.let { res ->
                                val isSuccess = res is FormulaValidationResult.Success
                                val bgColor = if (isSuccess) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer
                                val textColor = if (isSuccess) Color(0xFF137333) else MaterialTheme.colorScheme.onErrorContainer
                                val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .background(bgColor, RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = if (isSuccess) "Success" else "Error",
                                        tint = textColor
                                    )
                                    Text(
                                        text = when (res) {
                                            is FormulaValidationResult.Success -> "Success! Formula parsed successfully. Simulation loaded in Custom Sandbox mode."
                                            is FormulaValidationResult.Error -> "Syntax Error: ${res.message}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.validateAndLoadFormula(formulaInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("verify_formula_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = "Parse")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify & Generate Animation")
                        }
                    }
                }
            }

            // 3. Real-time Analysis Graph Block
            if (massHistory.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(32.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1C1E)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Real-time Oscilloscope (Mass vs Time)",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height

                                // Draw grids
                                val gridLineCount = 5
                                for (i in 0..gridLineCount) {
                                    val y = height * i / gridLineCount
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.1f),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = 1f
                                    )
                                }

                                val points = massHistory
                                if (points.size > 1) {
                                    val minVal = points.minOf { it.second }
                                    val maxVal = points.maxOf { it.second }
                                    val valueRange = if (maxVal == minVal) 1.0 else (maxVal - minVal)

                                    val graphPath = Path()
                                    points.forEachIndexed { idx, pair ->
                                        val x = width * idx / (points.size - 1)
                                        // normalized y coordinate
                                        val y = height - ((pair.second - minVal) / valueRange * (height - 30f) + 15f).toFloat()

                                        if (idx == 0) {
                                            graphPath.moveTo(x, y)
                                        } else {
                                            graphPath.lineTo(x, y)
                                        }
                                    }

                                    drawPath(
                                        path = graphPath,
                                        color = Color(0xFF22C55E), // Neon green graph plot
                                        style = Stroke(width = 4f)
                                    )
                                    
                                    // Label values
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "Max: %.1f".format(maxVal),
                                        15f,
                                        30f,
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.GRAY
                                            textSize = 24f
                                        }
                                    )
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "Min: %.1f".format(minVal),
                                        15f,
                                        height - 15f,
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.GRAY
                                            textSize = 24f
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Mathematical Sequence of Solution
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("formula_solution_card"),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CURRENT FORMULA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = anim.formulaExpression,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        fontFamily = FontFamily.Serif
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = anim.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Sequence of the Solution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            steps.forEachIndexed { idx, step ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Step Circle
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (idx + 1).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Substitution: ${step.calculation}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Result: ${step.result}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981), // Green highlight for math output
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom Simulation") },
            text = {
                Column {
                    Text("Store this parameter configuration into your library database.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Simulation Title") },
                        modifier = Modifier.fillMaxWidth().testTag("save_dialog_name_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveName.isNotBlank()) {
                            viewModel.saveCurrentConfiguration(saveName)
                            showSaveDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_dialog_confirm")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PhysicsAnimationCanvas(
    formulaType: String,
    formulaExpression: String,
    visualizationStyle: String,
    variables: List<SimulationVariable>,
    time: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        var targetX = centerX
        var targetY = centerY

        when (formulaType) {
            "density" -> {
                // Draw 3D Density Block on Scale
                val density = variables.firstOrNull { it.key == "density" }?.value ?: 1.0
                val wDim = variables.firstOrNull { it.key == "width" }?.value ?: 10.0
                val hDim = variables.firstOrNull { it.key == "height" }?.value ?: 10.0
                val dDim = variables.firstOrNull { it.key == "depth" }?.value ?: 10.0

                // Render dynamic weighing scale base
                val scaleWidth = width * 0.7f
                val scaleHeight = 35f
                val scaleY = height - 70f
                drawRoundRect(
                    color = Color(0xFF2C353F),
                    topLeft = Offset(centerX - scaleWidth / 2f, scaleY),
                    size = Size(scaleWidth, scaleHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )

                // Scale Display
                val massG = density * (wDim * hDim * dDim)
                val displayMass = if (massG >= 1000) "%.2f kg".format(massG / 1000.0) else "%.1f g".format(massG)
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(centerX - 80f, scaleY + 8f),
                    size = Size(160f, scaleHeight - 16f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Draw display text
                drawContext.canvas.nativeCanvas.drawText(
                    displayMass,
                    centerX - 45f,
                    scaleY + 26f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 20f
                        isFakeBoldText = true
                    }
                )

                // Render Cube (Isometric Projection) sitting on the scale
                // Map dimensional variables to scale pixels (max scale 120 pixels)
                val scaleFactor = 150f / 30f // Max 30cm maps to 150px
                val rw = (wDim * scaleFactor).toFloat()
                val rh = (hDim * scaleFactor).toFloat()
                val rd = (dDim * scaleFactor).toFloat()

                val cubeBottomX = centerX
                val cubeBottomY = scaleY

                // Material color based on density (Wood = light, Gold = golden yellow, metals = silver-gray)
                val cubeBaseColor = when {
                    density < 1.0 -> Color(0xFFD4A373) // Wood
                    density < 3.0 -> Color(0xFF94A3B8) // Aluminum / Concrete
                    density < 8.5 -> Color(0xFF64748B) // Iron / Steel
                    else -> Color(0xFFFBBF24) // Gold / Platinum
                }

                val pathFront = Path().apply {
                    moveTo(cubeBottomX - rw / 2f, cubeBottomY)
                    lineTo(cubeBottomX + rw / 2f, cubeBottomY)
                    lineTo(cubeBottomX + rw / 2f, cubeBottomY - rh)
                    lineTo(cubeBottomX - rw / 2f, cubeBottomY - rh)
                    close()
                }
                drawPath(pathFront, cubeBaseColor)

                val pathTop = Path().apply {
                    moveTo(cubeBottomX - rw / 2f, cubeBottomY - rh)
                    lineTo(cubeBottomX - rw / 2f + rd * 0.5f, cubeBottomY - rh - rd * 0.3f)
                    lineTo(cubeBottomX + rw / 2f + rd * 0.5f, cubeBottomY - rh - rd * 0.3f)
                    lineTo(cubeBottomX + rw / 2f, cubeBottomY - rh)
                    close()
                }
                drawPath(pathTop, cubeBaseColor.copy(alpha = 0.85f))

                val pathSide = Path().apply {
                    moveTo(cubeBottomX + rw / 2f, cubeBottomY)
                    lineTo(cubeBottomX + rw / 2f + rd * 0.5f, cubeBottomY - rd * 0.3f)
                    lineTo(cubeBottomX + rw / 2f + rd * 0.5f, cubeBottomY - rh - rd * 0.3f)
                    lineTo(cubeBottomX + rw / 2f, cubeBottomY - rh)
                    close()
                }
                drawPath(pathSide, cubeBaseColor.copy(alpha = 0.7f))

                // Wireframe outline
                drawPath(pathFront, Color.White.copy(alpha = 0.3f), style = Stroke(width = 2f))
                drawPath(pathTop, Color.White.copy(alpha = 0.3f), style = Stroke(width = 2f))
                drawPath(pathSide, Color.White.copy(alpha = 0.3f), style = Stroke(width = 2f))

                // Assign density block center coordinates
                targetX = cubeBottomX
                targetY = cubeBottomY - rh / 2f
            }
            "spring" -> {
                // Spring mass oscillator drawing
                val mass = variables.firstOrNull { it.key == "mass" }?.value ?: 1.0
                val k = variables.firstOrNull { it.key == "stiffness" }?.value ?: 10.0
                val amp = variables.firstOrNull { it.key == "amplitude" }?.value ?: 1.0
                val damping = variables.firstOrNull { it.key == "damping" }?.value ?: 0.0

                val omega0 = sqrt(k / mass)
                val alpha = damping / (2 * mass)
                val omegaD = if (omega0 > alpha) sqrt(omega0.pow(2) - alpha.pow(2)) else 0.0
                val displacement = amp * exp(-alpha * time) * cos(omegaD * time)

                val baseLineY = 50f
                // Top support
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(centerX - 100f, baseLineY),
                    end = Offset(centerX + 100f, baseLineY),
                    strokeWidth = 10f
                )

                // Spring extension formula
                val springBaseLength = centerY - baseLineY
                val scaleMultiplier = 30f // pixels per meter
                val activeSpringLength = (springBaseLength + displacement * scaleMultiplier).toFloat()

                // Draw coiled spring path
                val springPath = Path()
                springPath.moveTo(centerX, baseLineY)
                val coils = 12
                val coilStep = activeSpringLength / coils
                for (i in 1..coils) {
                    val y = baseLineY + (i * coilStep)
                    val x = if (i % 2 == 0) centerX - 30f else centerX + 30f
                    if (i == coils) {
                        springPath.lineTo(centerX, y)
                    } else {
                        springPath.lineTo(x, y)
                    }
                }
                drawPath(springPath, Color(0xFF94A3B8), style = Stroke(width = 5f, cap = StrokeCap.Round))

                // Draw Hanging Mass Load Block
                val blockRadius = (30f + mass * 2.5f).toFloat().coerceIn(20f, 65f)
                val blockTopLeft = Offset(centerX - blockRadius, baseLineY + activeSpringLength)
                drawRoundRect(
                    color = Color(0xFFEF4444),
                    topLeft = blockTopLeft,
                    size = Size(blockRadius * 2, blockRadius * 2),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                // Draw load text inside
                drawContext.canvas.nativeCanvas.drawText(
                    "%.1f kg".format(mass),
                    centerX - 24f,
                    baseLineY + activeSpringLength + blockRadius + 8f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 18f
                        isFakeBoldText = true
                    }
                )

                // Assign spring oscillator mass coordinates
                targetX = centerX
                targetY = baseLineY + activeSpringLength + blockRadius
            }
            "newton" -> {
                // Newton's Sled Force acceleration drawing
                val mass = variables.firstOrNull { it.key == "mass" }?.value ?: 1.0
                val force = variables.firstOrNull { it.key == "force" }?.value ?: 0.0
                val mu = variables.firstOrNull { it.key == "friction_coeff" }?.value ?: 0.0

                val fN = mass * 9.81
                val fFric = mu * fN
                val fNet = max(0.0, force - fFric)
                val acc = fNet / mass

                // Calculate displacement wrap-around
                val distScale = 30f
                val disp = 0.5 * acc * time.pow(2) * distScale
                val sledX = (100f + (disp % (width - 250f))).toFloat()
                val sledY = centerY + 30f

                // Draw Ground Runway
                drawLine(
                    color = Color(0xFF334155),
                    start = Offset(0f, sledY + 40f),
                    end = Offset(width, sledY + 40f),
                    strokeWidth = 6f
                )

                // Draw Cargo Sled
                drawRoundRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(sledX, sledY - 20f),
                    size = Size(100f, 60f),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                // Push Force Arrow
                if (force > 0) {
                    val arrowLength = (30f + force * 0.4f).toFloat().coerceAtMost(120f)
                    drawLine(
                        color = Color(0xFF10B981),
                        start = Offset(sledX - arrowLength, sledY + 10f),
                        end = Offset(sledX, sledY + 10f),
                        strokeWidth = 6f
                    )
                    // Arrow tip
                    val tipPath = Path().apply {
                        moveTo(sledX, sledY + 10f)
                        lineTo(sledX - 15f, sledY)
                        lineTo(sledX - 15f, sledY + 20f)
                        close()
                    }
                    drawPath(tipPath, Color(0xFF10B981))
                }

                // Resistance Friction Arrow (point backwards)
                if (fFric > 0 && force > 0) {
                    val fricArrowLen = (20f + fFric * 0.4f).toFloat().coerceAtMost(80f)
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(sledX + 100f, sledY + 25f),
                        end = Offset(sledX + 100f + fricArrowLen, sledY + 25f),
                        strokeWidth = 4f
                    )
                    val tipPath = Path().apply {
                        moveTo(sledX + 100f, sledY + 25f)
                        lineTo(sledX + 100f + 12f, sledY + 18f)
                        lineTo(sledX + 100f + 12f, sledY + 32f)
                        close()
                    }
                    drawPath(tipPath, Color(0xFFEF4444))
                }

                // Draw Mass Label on Block
                drawContext.canvas.nativeCanvas.drawText(
                    "M: %.0fkg".format(mass),
                    sledX + 15f,
                    sledY + 18f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 16f
                        isFakeBoldText = true
                    }
                )
                // Draw velocity speedometer
                val vel = acc * time
                drawContext.canvas.nativeCanvas.drawText(
                    "v = %.2fm/s".format(vel),
                    sledX + 10f,
                    sledY - 30f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.YELLOW
                        textSize = 18f
                        isFakeBoldText = true
                    }
                )

                // Assign moving sled center coordinates
                targetX = sledX + 50f
                targetY = sledY + 10f
            }
            "rocket" -> {
                // Rocket Launch fuel mass burn simulation
                val mDry = variables.firstOrNull { it.key == "mass_dry" }?.value ?: 1000.0
                val mFuel = variables.firstOrNull { it.key == "mass_fuel" }?.value ?: 2000.0
                val burnRate = variables.firstOrNull { it.key == "burn_rate" }?.value ?: 50.0
                val u = variables.firstOrNull { it.key == "exhaust_velocity" }?.value ?: 2000.0

                val tBurn = mFuel / burnRate
                val currentFuel = if (time <= tBurn) mFuel - burnRate * time else 0.0
                val currentMass = mDry + currentFuel

                val thrust = if (time <= tBurn) u * burnRate else 0.0
                val gravityForce = currentMass * 9.81
                val isFlying = thrust > gravityForce

                // Let's compute height offset
                val thrustRatio = thrust / (mDry + mFuel) / 9.81
                val rocketY = if (isFlying) {
                    val initialAcc = (thrust - (mDry + mFuel) * 9.81) / (mDry + mFuel)
                    val riseY = 0.5 * initialAcc * time.pow(2) * 5f
                    (height - 110f - (riseY % (height - 150f))).toFloat()
                } else {
                    height - 110f
                }

                // Draw deep space background stars
                for (i in 0..15) {
                    val xStar = (sin(i.toDouble() + 5.0) * centerX + centerX).toFloat()
                    val yStar = (cos(i.toDouble() * 3.0) * centerY + centerY).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.6f), 2f, Offset(xStar, yStar))
                }

                // Draw Pad
                drawRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(centerX - 80f, height - 50f),
                    size = Size(160f, 15f)
                )

                // Draw Rocket Body
                val rocketWidth = 40f
                val rocketHeight = 90f
                val rX = centerX - rocketWidth / 2f
                val rY = rocketY

                // Nose cone
                val noseCone = Path().apply {
                    moveTo(centerX, rY - 25f)
                    lineTo(rX, rY)
                    lineTo(rX + rocketWidth, rY)
                    close()
                }
                drawPath(noseCone, Color(0xFFEF4444))

                // Fuselage
                drawRoundRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(rX, rY),
                    size = Size(rocketWidth, rocketHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Fins
                val finLeft = Path().apply {
                    moveTo(rX, rY + rocketHeight - 30f)
                    lineTo(rX - 18f, rY + rocketHeight)
                    lineTo(rX, rY + rocketHeight)
                    close()
                }
                drawPath(finLeft, Color(0xFFDC2626))

                val finRight = Path().apply {
                    moveTo(rX + rocketWidth, rY + rocketHeight - 30f)
                    lineTo(rX + rocketWidth + 18f, rY + rocketHeight)
                    lineTo(rX + rocketWidth, rY + rocketHeight)
                    close()
                }
                drawPath(finRight, Color(0xFFDC2626))

                // Fuel status indicator bar on rocket body
                val fuelRatio = (currentFuel / mFuel).toFloat()
                if (fuelRatio > 0) {
                    drawRect(
                        color = Color(0xFF10B981),
                        topLeft = Offset(centerX - 4f, rY + 15f),
                        size = Size(8f, 50f * fuelRatio)
                    )
                }

                // Fire exhaust particle effects
                if (currentFuel > 0 && isFlying) {
                    val flameLength = (35f + (sin(time * 30.0) * 15f)).toFloat()
                    val flamePath = Path().apply {
                        moveTo(centerX - 12f, rY + rocketHeight)
                        lineTo(centerX + 12f, rY + rocketHeight)
                        lineTo(centerX, rY + rocketHeight + flameLength)
                        close()
                    }
                    drawPath(
                        flamePath,
                        Brush.verticalGradient(listOf(Color(0xFFF97316), Color(0xFFEF4444), Color.Transparent))
                    )
                }

                // Overlay HUD parameters
                drawContext.canvas.nativeCanvas.drawText(
                    "Mass: %.0f kg".format(currentMass),
                    centerX + 40f,
                    rY + 25f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 15f
                        isFakeBoldText = true
                    }
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "Fuel: %.0f%%".format(fuelRatio * 100f),
                    centerX + 40f,
                    rY + 45f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 14f
                    }
                )

                // Assign rocket fuselage center coordinates
                targetX = centerX
                targetY = rY + rocketHeight / 2f
            }
            "relativity" -> {
                // Relativistic Particle Accelerator
                val restMass = variables.firstOrNull { it.key == "rest_mass" }?.value ?: 1.0
                val speedRatio = variables.firstOrNull { it.key == "speed_ratio" }?.value ?: 0.5
                // Let acceleration increase velocity dynamically on loop
                val vRatio = speedRatio * ((time / 15.0) % 1.0)
                val gamma = 1.0 / sqrt(1.0 - vRatio.pow(2))
                val relativeMass = gamma * restMass

                // Draw round particle accelerator tunnel ring
                val ringRadius = 80f
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = ringRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 30f)
                )
                drawCircle(
                    color = Color(0xFF06B6D4), // Glowing neon cyan rail guide
                    radius = ringRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), time.toFloat() * 100f))
                )

                // Particle Position based on angular velocity
                val angle = (time * 5.0 * (1.0 + vRatio * 10f)) % (2 * PI)
                val pX = (centerX + ringRadius * cos(angle)).toFloat()
                val pY = (centerY + ringRadius * sin(angle)).toFloat()

                // Spacetime warping grid background representation (draw concentric fading distortion circles)
                val distortionCount = (3 + relativeMass * 2).toInt().coerceIn(3, 15)
                for (i in 1..distortionCount) {
                    val rad = 8f * i * (1.0 + vRatio)
                    drawCircle(
                        color = Color(0xFFEC4899).copy(alpha = (0.25f / i).toFloat()),
                        radius = rad.toFloat(),
                        center = Offset(pX, pY),
                        style = Stroke(width = 2f)
                    )
                }

                // Draw Glowing Particle
                val pRadius = (6f + relativeMass * 3f).toFloat().coerceIn(5f, 35f)
                drawCircle(
                    color = Color(0xFFEC4899), // Hot Pink
                    radius = pRadius,
                    center = Offset(pX, pY)
                )
                drawCircle(
                    color = Color.White,
                    radius = pRadius * 0.4f,
                    center = Offset(pX, pY)
                )

                // Dashboard stats
                drawContext.canvas.nativeCanvas.drawText(
                    "Speed: %.3fc".format(vRatio),
                    centerX - 40f,
                    centerY - 5f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 15f
                        isFakeBoldText = true
                    }
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "m_rel: %.2f".format(relativeMass),
                    centerX - 40f,
                    centerY + 15f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.MAGENTA
                        textSize = 15f
                        isFakeBoldText = true
                    }
                )

                // Assign orbital relativistic particle center coordinates
                targetX = pX
                targetY = pY
            }
            "quantum" -> {
                // Quantum Mechanics: Schrödinger Wave Function in a 1D Box
                val n = (variables.firstOrNull { it.key == "energy_level" }?.value ?: 1.0).toFloat().toInt().coerceIn(1, 5)
                val superposition = (variables.firstOrNull { it.key == "superposition" }?.value ?: 0.0).toFloat().coerceIn(0f, 1f)
                val wellWidthVal = (variables.firstOrNull { it.key == "well_width" }?.value ?: 20.0).toFloat().coerceIn(10f, 30f)
                val particleMassVal = (variables.firstOrNull { it.key == "particle_mass" }?.value ?: 1.0).toFloat().coerceIn(0.5f, 5.0f)

                val L = wellWidthVal * 12f // 120px to 360px wide
                val xStart = centerX - L / 2f
                val xEnd = centerX + L / 2f
                val floorY = centerY + 80f
                val baselineY = centerY - 20f

                // Draw Potential Well Walls (Steel blue boundaries)
                // Left Wall
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(xStart, centerY - 120f),
                    end = Offset(xStart, floorY),
                    strokeWidth = 6f
                )
                // Right Wall
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(xEnd, centerY - 120f),
                    end = Offset(xEnd, floorY),
                    strokeWidth = 6f
                )
                // Floor line
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(xStart - 40f, floorY),
                    end = Offset(xEnd + 40f, floorY),
                    strokeWidth = 4f
                )

                // Label infinite potential walls
                drawContext.canvas.nativeCanvas.drawText(
                    "V = ∞",
                    xStart - 50f,
                    centerY - 80f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 14f
                        isFakeBoldText = true
                    }
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "V = ∞",
                    xEnd + 10f,
                    centerY - 80f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 14f
                        isFakeBoldText = true
                    }
                )

                // Draw the waves
                val pointsCount = 120
                val pathRe = Path()
                val pathIm = Path()
                val pathProb = Path()

                val S = superposition
                val c1 = sqrt(1f - S * S)
                val c2 = S

                val baseFreq = 2.0 / particleMassVal
                val omega1 = (n * n) * baseFreq
                val omega2 = ((n + 1) * (n + 1)) * baseFreq
                val tF = time

                var peakX = centerX
                var peakY = baselineY
                var maxProb = -1f

                for (i in 0..pointsCount) {
                    val normX = i.toFloat() / pointsCount
                    val px = xStart + normX * L

                    // Wavefunctions (sin)
                    val psi1 = sin(n * Math.PI * normX).toFloat()
                    val psi2 = sin((n + 1) * Math.PI * normX).toFloat()

                    // Time dependency
                    val re1 = psi1 * cos(omega1 * tF).toFloat()
                    val im1 = -psi1 * sin(omega1 * tF).toFloat()

                    val re2 = psi2 * cos(omega2 * tF).toFloat()
                    val im2 = -psi2 * sin(omega2 * tF).toFloat()

                    // Superposition
                    val re = c1 * re1 + c2 * re2
                    val im = c1 * im1 + c2 * im2
                    val prob = re * re + im * im

                    val ampScale = 65f
                    val pyRe = baselineY - re * ampScale
                    val pyIm = baselineY - im * ampScale
                    val pyProb = floorY - prob * 110f // Plot prob density sitting on well floor

                    if (i == 0) {
                        pathRe.moveTo(px, pyRe)
                        pathIm.moveTo(px, pyIm)
                        pathProb.moveTo(px, floorY)
                        pathProb.lineTo(px, pyProb)
                    } else {
                        pathRe.lineTo(px, pyRe)
                        pathIm.lineTo(px, pyIm)
                        pathProb.lineTo(px, pyProb)
                    }

                    if (prob > maxProb) {
                        maxProb = prob
                        peakX = px
                        peakY = pyProb
                    }
                }
                pathProb.lineTo(xEnd, floorY)
                pathProb.close()

                // Draw Probability Density (Lime Green transparent fill)
                drawPath(
                    path = pathProb,
                    color = Color(0xFFADFF2F).copy(alpha = 0.25f)
                )
                // Draw Probability Density border
                drawPath(
                    path = pathProb,
                    color = Color(0xFFADFF2F),
                    style = Stroke(width = 3f)
                )

                // Draw Real Part (Neon Cyan)
                drawPath(
                    path = pathRe,
                    color = Color(0xFF00E5FF),
                    style = Stroke(width = 2.5f)
                )

                // Draw Imaginary Part (Neon Magenta / Orange)
                drawPath(
                    path = pathIm,
                    color = Color(0xFFFF3D00),
                    style = Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )

                // Track the peak as target coordinates for other visualizers
                targetX = peakX
                targetY = peakY

                // Render floating peak marker/sphere
                drawCircle(
                    color = Color(0xFFADFF2F),
                    radius = 6f,
                    center = Offset(targetX, targetY)
                )

                // Dynamic quantum HUD display
                drawContext.canvas.nativeCanvas.drawText(
                    "Re(Ψ) [Cyan]",
                    xStart,
                    centerY - 130f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.CYAN
                        textSize = 13f
                    }
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "Im(Ψ) [Red Dash]",
                    centerX - 40f,
                    centerY - 130f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 13f
                    }
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "|Ψ|² [Lime]",
                    xEnd - 60f,
                    centerY - 130f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 13f
                    }
                )
            }
            "custom" -> {
                // Custom mathematical Sandbox formula renderer
                val customVal = evaluateFormula(formulaExpression, time)
                val displayVal = customVal.coerceIn(-9999.0, 9999.0)
                
                val sphereY = centerY + (customVal.toFloat() * 10f).coerceIn(-120f, 120f)
                targetX = centerX
                targetY = sphereY
                
                // Weighing scale pedestal
                val baseWidth = 140f
                drawRect(
                    color = Color(0xFF334155),
                    topLeft = Offset(centerX - baseWidth / 2f, height - 60f),
                    size = Size(baseWidth, 15f)
                )
                
                // Pulsing energy core
                val coreRadius = (30f + abs(customVal).toFloat() * 2f).coerceIn(15f, 75f)
                val coreColor = if (customVal >= 0) Color(0xFF00E5FF) else Color(0xFFFF3D00)
                
                drawCircle(
                    color = coreColor.copy(alpha = 0.15f),
                    radius = coreRadius * 1.5f,
                    center = Offset(centerX, sphereY)
                )
                drawCircle(
                    color = coreColor,
                    radius = coreRadius,
                    center = Offset(centerX, sphereY)
                )
                drawCircle(
                    color = Color.White,
                    radius = coreRadius * 0.4f,
                    center = Offset(centerX, sphereY)
                )
                
                // Dynamic formula floating HUD overlay
                drawContext.canvas.nativeCanvas.drawText(
                    "f(t) = %.2f".format(displayVal),
                    centerX - 45f,
                    sphereY - coreRadius - 15f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 18f
                        isFakeBoldText = true
                    }
                )
            }
            else -> {
                // Standard default generic bouncing ball particle drawing
                val bounceY = centerY + sin(time.toFloat() * 3f) * 40f
                targetX = centerX
                targetY = bounceY
                drawCircle(
                    color = Color(0xFF3B82F6),
                    radius = 35f,
                    center = Offset(centerX, bounceY)
                )
            }
        }

        // OVERLAY VISUALIZATIONS
        when (visualizationStyle) {
            "heatmap" -> {
                // Thermal-density Heatmap centered at target mass
                val radMax = 180f
                for (i in 1..8) {
                    val r = radMax * (i / 8f)
                    val alpha = (0.35f * (1.0f - i / 8f)).coerceAtLeast(0f)
                    drawCircle(
                        color = Color(0xFFFF5722).copy(alpha = alpha), // Flame orange
                        radius = r,
                        center = Offset(targetX, targetY)
                    )
                }
                // Hotspot core
                drawCircle(
                    color = Color(0xFFFFEB3B).copy(alpha = 0.5f),
                    radius = 20f,
                    center = Offset(targetX, targetY)
                )
            }
            "vector" -> {
                // Vector field representing gravitational attractive potential flow
                val rows = 6
                val cols = 8
                val stepX = width / (cols + 1)
                val stepY = height / (rows + 1)
                for (r in 1..rows) {
                    for (c in 1..cols) {
                        val gx = c * stepX
                        val gy = r * stepY
                        
                        val dx = targetX - gx
                        val dy = targetY - gy
                        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        
                        val ux = dx / dist
                        val uy = dy / dist
                        
                        val strength = (80f / dist).coerceIn(0.1f, 1.0f)
                        val arrowLen = 15f * strength
                        
                        val start = Offset(gx, gy)
                        val end = Offset(gx + ux * arrowLen, gy + uy * arrowLen)
                        
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = 0.45f * strength),
                            start = start,
                            end = end,
                            strokeWidth = 2f
                        )
                        // Arrow heads
                        val headSize = 4f * strength
                        val leftWing = Offset(
                            end.x - ux * headSize + uy * headSize * 0.7f,
                            end.y - uy * headSize - ux * headSize * 0.7f
                        )
                        val rightWing = Offset(
                            end.x - ux * headSize - uy * headSize * 0.7f,
                            end.y - uy * headSize + ux * headSize * 0.7f
                        )
                        drawLine(color = Color(0xFF00E5FF).copy(alpha = 0.45f * strength), start = end, end = leftWing, strokeWidth = 2f)
                        drawLine(color = Color(0xFF00E5FF).copy(alpha = 0.45f * strength), start = end, end = rightWing, strokeWidth = 2f)
                    }
                }
            }
            "particles" -> {
                // Particle system orbiting/spiraling into the center of mass
                for (i in 0 until 24) {
                    val phase = i * (2 * PI / 24)
                    val speed = 1.2 + (i % 3) * 0.6
                    val baseRadius = 35f + (i % 4) * 18f
                    
                    val age = (time * speed + i) % 4.0
                    val currentRad = baseRadius * (1.0 - (age / 4.0)).toFloat().coerceAtLeast(0.1f)
                    val angle = phase + time * speed * 2.0
                    
                    val px = targetX + currentRad * cos(angle).toFloat()
                    val py = targetY + currentRad * sin(angle).toFloat()
                    val pAlpha = (1.0f - (age.toFloat() / 4.0f)).coerceIn(0f, 1f)
                    
                    drawCircle(
                        color = Color(0xFFADFF2F).copy(alpha = pAlpha * 0.8f),
                        radius = 3f + (i % 2),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
