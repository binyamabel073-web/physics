package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedAnimation
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAnimationsScreen(
    viewModel: MainViewModel,
    onAnimationSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedList by viewModel.savedAnimations.collectAsStateWithLifecycle()
    val activeAnim by viewModel.selectedAnimation.collectAsStateWithLifecycle()
    
    val builtInPresets = remember { viewModel.getBuiltInPresets() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Simulation Library", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Group 1: Core Standard Physics Models
            item {
                Text(
                    text = "Standard Physical Models",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            items(builtInPresets) { preset ->
                val isActive = activeAnim?.formulaType == preset.formulaType && activeAnim?.id == preset.id
                PresetItemRow(
                    preset = preset,
                    isActive = isActive,
                    onClick = {
                        viewModel.selectAnimation(preset)
                        onAnimationSelected()
                    },
                    modifier = Modifier.testTag("preset_item_${preset.formulaType}")
                )
            }

            // Group 2: Custom / Saved Workspace Models
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "My Saved Library Workspace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (savedList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = "Empty",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No custom simulations saved yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Tweak variables and click Save inside the simulator dashboard.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(savedList) { custom ->
                    val isActive = activeAnim?.id == custom.id
                    PresetItemRow(
                        preset = custom,
                        isActive = isActive,
                        onClick = {
                            viewModel.selectAnimation(custom)
                            onAnimationSelected()
                        },
                        onDelete = {
                            viewModel.deleteSavedAnimation(custom.id)
                        },
                        modifier = Modifier.testTag("saved_item_${custom.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun PresetItemRow(
    preset: SavedAnimation,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 0.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.0f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Formula: ${preset.formulaExpression}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_saved_${preset.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
