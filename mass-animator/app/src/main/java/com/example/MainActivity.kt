package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GeminiSolverScreen
import com.example.ui.screens.PhysicsEngineScreen
import com.example.ui.screens.SavedAnimationsScreen
import com.example.ui.screens.WriterScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: MainViewModel = viewModel()
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
              NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Simulator") },
                label = { Text("Simulator") },
                modifier = Modifier.testTag("nav_tab_simulator")
              )
              NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.Storage, contentDescription = "Library") },
                label = { Text("Library") },
                modifier = Modifier.testTag("nav_tab_library")
              )
              NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Solver") },
                label = { Text("AI Solver") },
                modifier = Modifier.testTag("nav_tab_ai_solver")
              )
              NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                icon = { Icon(Icons.Default.Edit, contentDescription = "Writer") },
                label = { Text("Writer") },
                modifier = Modifier.testTag("nav_tab_writer")
              )
              NavigationBarItem(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                icon = { Icon(Icons.Default.Science, contentDescription = "2D Engine") },
                label = { Text("2D Engine") },
                modifier = Modifier.testTag("nav_tab_physics_engine")
              )
            }
          }
        ) { innerPadding ->
          val contentModifier = Modifier.padding(innerPadding)
          when (selectedTab) {
            0 -> DashboardScreen(viewModel = viewModel, modifier = contentModifier)
            1 -> SavedAnimationsScreen(
              viewModel = viewModel,
              onAnimationSelected = { selectedTab = 0 },
              modifier = contentModifier
            )
            2 -> GeminiSolverScreen(
              viewModel = viewModel,
              onSolveSuccess = { selectedTab = 0 },
              modifier = contentModifier
            )
            3 -> WriterScreen(
              viewModel = viewModel,
              onNavigateToDashboard = { selectedTab = 0 },
              modifier = contentModifier
            )
            4 -> PhysicsEngineScreen(modifier = contentModifier)
          }
        }
      }
    }
  }
}

