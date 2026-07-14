package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiSolverScreen(
    viewModel: MainViewModel,
    onSolveSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf("") }
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()

    val sampleQueries = remember {
        listOf(
            "An astronaut weighing a heavy lead block on Mars gravity",
            "A copper container on a friction-less ice rink being pushed by a 250N thruster",
            "A high-altitude scientific balloon burning helium weight over a 15-second ascent",
            "An ultra-relativistic cosmic ray particle traveling at 95% lightspeed"
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("AI Custom Simulator Builder", fontWeight = FontWeight.Bold) },
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
            // Hero Intro Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
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
                                text = "Gemini Physics Creator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Describe any custom physical formula, mass problem, or word query. Gemini will parse variables, derive the mathematical sequence of the solution, and build a custom canvas simulation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Input Console
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Describe your physical system:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            placeholder = { Text("e.g., A heavy wooden payload on a spring with k=150...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .testTag("ai_solver_text_input"),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (aiLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Gemini is deriving physical matrices...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (queryText.isNotBlank()) {
                                        viewModel.solveWithAI(queryText) {
                                            onSolveSuccess()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("ai_solve_button"),
                                shape = RoundedCornerShape(16.dp),
                                enabled = queryText.isNotBlank()
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Solve")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Interactive Simulation", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Error message
                        AnimatedVisibility(visible = aiError != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = aiError ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sample Prompts Selection List
            item {
                Text(
                    text = "Or choose a quick laboratory sample:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(sampleQueries.size) { index ->
                val sample = sampleQueries[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { queryText = sample }
                        .testTag("sample_query_$index"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Sample",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
