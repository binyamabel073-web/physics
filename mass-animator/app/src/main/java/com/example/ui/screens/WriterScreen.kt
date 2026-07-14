package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel

data class ScribblePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriterScreen(
    viewModel: MainViewModel,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedAnimation by viewModel.selectedAnimation.collectAsStateWithLifecycle()
    val writerLoading by viewModel.writerLoading.collectAsStateWithLifecycle()
    val writerError by viewModel.writerError.collectAsStateWithLifecycle()
    val generatedReport by viewModel.generatedReport.collectAsStateWithLifecycle()

    val eqLoading by viewModel.equationExplanationLoading.collectAsStateWithLifecycle()
    val eqExplanation by viewModel.equationExplanation.collectAsStateWithLifecycle()
    val eqError by viewModel.equationExplanationError.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Scribble Pad, 1 = Equation Composer, 2 = AI Report Writer

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Scientific Writing & Workspace", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Header Row with 3 options
            TabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier.fillMaxWidth().testTag("writer_tabs"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Scratchpad", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Gesture, contentDescription = "Draw") },
                    modifier = Modifier.testTag("tab_scratchpad")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Equation Writer", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Calculate, contentDescription = "Equations") },
                    modifier = Modifier.testTag("tab_equation_composer")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("AI Lab Writer", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.BorderColor, contentDescription = "Write") },
                    modifier = Modifier.testTag("tab_ai_report")
                )
            }

            when (activeTab) {
                0 -> {
                    // Scribble Pad View
                    ScribblePadTabContent(context = context)
                }
                1 -> {
                    // Equation Composer View
                    EquationComposerTabContent(
                        context = context,
                        eqLoading = eqLoading,
                        eqExplanation = eqExplanation,
                        eqError = eqError,
                        onExplainEquation = { equation ->
                            viewModel.explainComposedEquation(equation)
                        },
                        onClearExplanation = {
                            viewModel.clearExplanationState()
                        },
                        onCopyExplanation = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Explanation copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        onSimulateEquation = { formula ->
                            viewModel.validateAndLoadFormula(formula)
                            onNavigateToDashboard()
                            Toast.makeText(context, "Loaded into Math Sandbox!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                2 -> {
                    // AI Writer View
                    AIReportWriterTabContent(
                        selectedAnimName = selectedAnimation?.name ?: "No Simulation Selected",
                        writerLoading = writerLoading,
                        writerError = writerError,
                        generatedReport = generatedReport,
                        onGenerateReport = { format, prompt ->
                            viewModel.generateReport(format, prompt)
                        },
                        onClearState = {
                            viewModel.clearWriterState()
                        },
                        onCopyReport = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScribblePadTabContent(context: android.content.Context) {
    val paths = remember { mutableStateListOf<ScribblePath>() }
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    
    var selectedColor by remember { mutableStateOf(Color(0xFF3B82F6)) }
    var brushSize by remember { mutableFloatStateOf(6f) }

    val colorsPalette = listOf(
        Color(0xFF3B82F6) to "Primary Blue",
        Color(0xFFEC4899) to "Hot Pink",
        Color(0xFF10B981) to "Neon Green",
        Color(0xFFF59E0B) to "Orange Yellow",
        Color(0xFFECF0F1) to "Chalk White",
        Color(0xFF1E293B) to "Eraser" // matches canvas BG
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scribble Intro
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Draw quick diagrams, outline physics formulas, or write freeform scratchpad notes about the active mechanical system.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Custom Canvas drawing area
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Toolbar row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: brush size indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Size:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        listOf(4f, 8f, 14f).forEach { sz ->
                            val isSelected = brushSize == sz
                            Button(
                                onClick = { brushSize = sz },
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(sz.toInt().toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Right: Clear Button
                    OutlinedButton(
                        onClick = {
                            paths.clear()
                            currentPathPoints.clear()
                            Toast.makeText(context, "Scribble canvas cleared", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("clear_scratchpad_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Board", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Palette selection row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Palette:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    colorsPalette.forEach { (color, name) ->
                        val isSelected = selectedColor == color
                        val border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.LightGray)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(border, CircleShape)
                                .clickable { selectedColor = color }
                                .testTag("brush_color_$name"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (name == "Eraser") {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = "Eraser", tint = Color.White, modifier = Modifier.size(14.dp))
                            } else if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = if (name == "Chalk White") Color.Black else Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Main Draw Board
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(24.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPathPoints.clear()
                                    currentPathPoints.add(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPathPoints.add(change.position)
                                },
                                onDragEnd = {
                                    if (currentPathPoints.isNotEmpty()) {
                                        paths.add(ScribblePath(currentPathPoints.toList(), selectedColor, brushSize))
                                        currentPathPoints.clear()
                                    }
                                }
                            )
                        }
                        .testTag("scribble_canvas")
                ) {
                    // Draw completed paths
                    paths.forEach { scribblePath ->
                        val path = Path().apply {
                            if (scribblePath.points.isNotEmpty()) {
                                moveTo(scribblePath.points.first().x, scribblePath.points.first().y)
                                for (i in 1 until scribblePath.points.size) {
                                    lineTo(scribblePath.points[i].x, scribblePath.points[i].y)
                                }
                            }
                        }
                        drawPath(
                            path = path,
                            color = scribblePath.color,
                            style = Stroke(
                                width = scribblePath.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    
                    // Draw active path
                    if (currentPathPoints.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                            for (i in 1 until currentPathPoints.size) {
                                lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = selectedColor,
                            style = Stroke(
                                width = brushSize,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AIReportWriterTabContent(
    selectedAnimName: String,
    writerLoading: Boolean,
    writerError: String?,
    generatedReport: String?,
    onGenerateReport: (String, String) -> Unit,
    onClearState: () -> Unit,
    onCopyReport: (String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf("Formal Lab Report") }
    var promptOverride by remember { mutableStateOf("") }

    val formatOptions = listOf(
        "Formal Lab Report",
        "Conceptual Summary Sheet",
        "Experimental Lab Guide",
        "Homework Question & Answer Solution"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Writer Intro Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Lab Report Writer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Compile custom academic papers or structured guides based on the currently selected simulation model and active physics configuration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }

        // Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Simulation context info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Science, contentDescription = "Active Lab", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Active Context: $selectedAnimName",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Document Format Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Format Selection Dropdown options
                    formatOptions.forEach { format ->
                        val isSelected = selectedFormat == format
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFormat = format }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFormat = format }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = format,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Additional Writing Guidelines (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = promptOverride,
                        onValueChange = { promptOverride = it },
                        placeholder = { Text("e.g. Focus on natural oscillations, derive damping curves, or write 3 homework questions...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("report_prompt_guidelines"),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (writerLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gemini is compiling scientific sections...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                onGenerateReport(selectedFormat, promptOverride)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("generate_report_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Write")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Write Scientific Document", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Error Box
                    AnimatedVisibility(visible = writerError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = writerError ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Generated Output View Box
        if (generatedReport != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Compiled Document",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { onCopyReport(generatedReport) },
                                    modifier = Modifier.testTag("copy_report_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                                }
                                IconButton(
                                    onClick = { onClearState() }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear document")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Divider()

                        Spacer(modifier = Modifier.height(16.dp))

                        // Scrollable Report Body Text Box (wrapped in selection container for ease of use)
                        SelectionContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Basic Markdown Parsing & Presentation for Compose
                                val lines = generatedReport.split("\n")
                                lines.forEach { line ->
                                    val trimmedLine = line.trim()
                                    when {
                                        trimmedLine.startsWith("###") -> {
                                            Text(
                                                text = trimmedLine.replace("#", "").trim(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(top = 10.dp)
                                            )
                                        }
                                        trimmedLine.startsWith("##") -> {
                                            Text(
                                                text = trimmedLine.replace("#", "").trim(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 14.dp)
                                            )
                                        }
                                        trimmedLine.startsWith("#") -> {
                                            Text(
                                                text = trimmedLine.replace("#", "").trim(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                        }
                                        trimmedLine.startsWith("-") || trimmedLine.startsWith("*") -> {
                                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                                Text("• ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = trimmedLine.substring(1).trim(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        else -> {
                                            if (trimmedLine.isNotEmpty()) {
                                                Text(
                                                    text = trimmedLine,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquationComposerTabContent(
    context: android.content.Context,
    eqLoading: Boolean,
    eqExplanation: String?,
    eqError: String?,
    onExplainEquation: (String) -> Unit,
    onClearExplanation: () -> Unit,
    onCopyExplanation: (String) -> Unit,
    onSimulateEquation: (String) -> Unit
) {
    var formulaInput by remember { mutableStateOf("3 * sin(2 * t) * exp(-0.15 * t)") }

    val templates = listOf(
        Triple("iħ * ∂Ψ/∂t = ĤΨ", "Schrödinger Equation (General)", "iħ * ∂Ψ/∂t = ĤΨ"),
        Triple("Ψ(x,t) = A * sin(k*x - ω*t)", "Quantum Wave Function State", "5 * sin(2 * t) * cos(t)"),
        Triple("Δx * Δp ≥ ħ/2", "Heisenberg Uncertainty Relation", "Δx * Δp >= h/2"),
        Triple("E = ħ * ω", "Planck-Einstein Relation", "h * f"),
        Triple("f = (1 / (2*pi)) * sqrt(k / m)", "Harmonic Spring Frequency", "cos(t) * exp(-0.1 * t)"),
        Triple("a = F_net / m", "Newton's Second Law", "2.5 * t"),
        Triple("m = ρ * V", "Mass-Density Equation", "7.87 * 1000")
    )

    val symbols = listOf(
        "θ" to "Theta",
        "λ" to "Lambda",
        "ψ" to "Psi (lowercase)",
        "Ψ" to "Psi (uppercase)",
        "ħ" to "Dirac h-bar",
        "π" to "Pi",
        "ω" to "Omega",
        "ρ" to "Rho",
        "σ" to "Sigma",
        "Δ" to "Delta",
        "Ĥ" to "Hamiltonian",
        "√" to "Square Root",
        "∫" to "Integral",
        "∂" to "Partial Derivative",
        "∑" to "Sigma Sum",
        "±" to "Plus-Minus",
        "≈" to "Approximately Equal",
        "≠" to "Not Equal",
        "²" to "Squared",
        "³" to "Cubed",
        "^" to "Power caret",
        "*" to "Multiplication",
        "t" to "Time variable"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Intro Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Equation Composer",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Build mathematical models and quantum expressions. Tweak with scientific symbols, explain physical meanings using Gemini, or load formulas directly into the visual Sandbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Live Composer input and Greek symbols pad
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Compose Scientific Equation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formulaInput,
                        onValueChange = { formulaInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("composed_equation_input"),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Active Expression") },
                        singleLine = true,
                        trailingIcon = {
                            if (formulaInput.isNotEmpty()) {
                                IconButton(onClick = { formulaInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Scientific & Quantum Symbol Keypad",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Wrap symbols in an FlowRow to fit screen width dynamically
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        symbols.forEach { (sym, name) ->
                            Button(
                                onClick = { formulaInput += sym },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .testTag("insert_sym_$sym")
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = sym,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Beautiful Live Mathematical Render Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACADEMIC RENDER PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Render styled equation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (formulaInput.isBlank()) {
                            Text(
                                text = "empty_expression",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            // High contrast mathematical serif typography!
                            val styledText = formulaInput
                                .replace("*", " × ")
                                .replace("/", " ÷ ")
                                .replace("hbar", "ħ")
                                .replace("psi", "ψ")
                                .replace("Psi", "Ψ")
                                .replace("omega", "ω")
                                .replace("rho", "ρ")
                                .replace("lambda", "λ")
                                .replace("theta", "θ")
                                .replace("sigma", "σ")
                                .replace("Delta", "Δ")
                                .replace("sqrt", "√")
                                .replace(">=", " ≥ ")
                                .replace("<=", " ≤ ")

                            Text(
                                text = styledText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("math_render_preview")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Actions row: Explain and Simulate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // AI Explain Button
                        Button(
                            onClick = {
                                if (formulaInput.isNotEmpty()) {
                                    onExplainEquation(formulaInput)
                                } else {
                                    Toast.makeText(context, "Please enter an equation first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("explain_equation_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Explain", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Explain (Gemini)", style = MaterialTheme.typography.labelLarge)
                        }

                        // Sandbox Simulator Load Button
                        Button(
                            onClick = {
                                if (formulaInput.isNotEmpty()) {
                                    // Strip non-sandbox friendly greek letters/symbols
                                    val cleanedFormula = formulaInput
                                        .replace("π", "pi")
                                        .replace("ħ", "1.054e-34")
                                        .replace("Ψ", "1.0")
                                        .replace("ψ", "1.0")
                                    onSimulateEquation(cleanedFormula)
                                } else {
                                    Toast.makeText(context, "Please enter an equation first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1.0f)
                                .height(48.dp)
                                .testTag("simulate_equation_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Simulate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Interactive Preset Templates Carousel/List
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Load Equation Presets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templates.forEach { (expr, desc, simExpr) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        formulaInput = expr
                                        Toast.makeText(context, "Template loaded!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = expr,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Load",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // Gemini Explanation Response Box
        if (eqExplanation != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gemini Physical Interpretation",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { onCopyExplanation(eqExplanation) },
                                    modifier = Modifier.testTag("copy_explanation_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                                IconButton(onClick = { onClearExplanation() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        SelectionContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val lines = eqExplanation.split("\n")
                                lines.forEach { line ->
                                    val trimmedLine = line.trim()
                                    when {
                                        trimmedLine.startsWith("###") -> {
                                            Text(
                                                text = trimmedLine.replace("#", "").trim(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(top = 10.dp)
                                            )
                                        }
                                        trimmedLine.startsWith("##") -> {
                                            Text(
                                                text = trimmedLine.replace("#", "").trim(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 14.dp)
                                            )
                                        }
                                        trimmedLine.startsWith("#") -> {
                                            Text(
                                                text = trimmedLine.replace("#", "").trim(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                        }
                                        trimmedLine.startsWith("-") || trimmedLine.startsWith("*") -> {
                                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                                Text("• ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = trimmedLine.substring(1).trim(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        else -> {
                                            if (trimmedLine.isNotEmpty()) {
                                                Text(
                                                    text = trimmedLine,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading and error indicators
        if (eqLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gemini is deciphering physical terms...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (eqError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = eqError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
