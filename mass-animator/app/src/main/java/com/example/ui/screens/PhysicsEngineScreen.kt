package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

// Represents a 2D physical body
data class PhysicalBody(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var mass: Float,
    var color: Color,
    var isBeingDragged: Boolean = false,
    val trail: MutableList<Offset> = mutableListOf() // Holds last few positions for rendering paths
)

// A brief collision event visual representation
data class CollisionFlash(
    val x: Float,
    val y: Float,
    var age: Float = 0f, // 0 to 1
    val maxRadius: Float = 30f
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhysicsEngineScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Simulation states
    var bodies by remember { mutableStateOf(listOf<PhysicalBody>()) }
    var collisionFlashes by remember { mutableStateOf(listOf<CollisionFlash>()) }
    var isPaused by remember { mutableStateOf(false) }

    // Adjustable Parameters
    var gravityStrength by remember { mutableFloatStateOf(9.81f) }
    var gravityAngleDegrees by remember { mutableFloatStateOf(90f) } // 90 = straight down
    var restitution by remember { mutableFloatStateOf(0.75f) } // bounciness (0 to 1)
    var airResistance by remember { mutableFloatStateOf(0.01f) } // damping coefficient (0 to 0.1)
    var showVectors by remember { mutableStateOf(true) }
    var showTrails by remember { mutableStateOf(true) }

    // Spawn Configuration
    var spawnRadius by remember { mutableFloatStateOf(24f) }
    var spawnMass by remember { mutableFloatStateOf(10f) }
    val colorsList = listOf(
        Color(0xFF00E5FF), // Neon Cyan
        Color(0xFFFF3D00), // Neon Orange/Red
        Color(0xFFADFF2F), // Lime Green
        Color(0xFFFFEB3B), // Yellow
        Color(0xFFE040FB), // Neon Magenta
        Color(0xFF3F51B5), // Deep Indigo
        Color(0xFF00E676), // Bright Green
        Color(0xFFFF4081)  // Hot Pink
    )
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    val selectedColor = colorsList[selectedColorIndex]

    // Canvas boundary tracking
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Slingshot / drag-fling physics helpers
    var activeDraggedId by remember { mutableStateOf<String?>(null) }
    var lastTouchOffset by remember { mutableStateOf(Offset.Zero) }
    var prevTouchOffset by remember { mutableStateOf(Offset.Zero) }

    // Quick Stats Calculations
    val activeBodyCount = bodies.size
    val totalKineticEnergy = remember(bodies) {
        bodies.sumOf { 0.5 * it.mass * (it.vx * it.vx + it.vy * it.vy) }
    }
    val systemCenterOfMass = remember(bodies) {
        if (bodies.isEmpty()) Offset.Zero else {
            var totalMass = 0f
            var cx = 0f
            var cy = 0f
            bodies.forEach {
                cx += it.x * it.mass
                cy += it.y * it.mass
                totalMass += it.mass
            }
            Offset(cx / totalMass, cy / totalMass)
        }
    }

    // Interactive Pre-configured Presets
    fun loadPreset(presetName: String) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            Toast.makeText(context, "Tap inside canvas first to measure viewport size!", Toast.LENGTH_SHORT).show()
            return
        }
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        
        when (presetName) {
            "cradle" -> {
                // Newton's Cradle setup in 2D
                val list = mutableListOf<PhysicalBody>()
                val count = 5
                val rad = 22f
                val startX = w / 2f - (count - 1) * rad
                val centerY = h / 2f
                for (i in 0 until count) {
                    val xVal = startX + i * (rad * 2f)
                    // The leftmost one starts with velocity to kick off momentum propagation!
                    val initialVx = if (i == 0) -15f else 0f
                    list.add(
                        PhysicalBody(
                            x = xVal,
                            y = centerY,
                            vx = initialVx,
                            vy = 0f,
                            radius = rad,
                            mass = 20f,
                            color = if (i == 0) colorsList[1] else colorsList[0]
                        )
                    )
                }
                bodies = list
                gravityStrength = 0f // Zero gravity for optimal horizontal collision observation
                Toast.makeText(context, "Loaded Newton's Cradle Preset!", Toast.LENGTH_SHORT).show()
            }
            "chaos" -> {
                // Gravitational chaos
                val list = mutableListOf<PhysicalBody>()
                for (i in 0 until 8) {
                    list.add(
                        PhysicalBody(
                            x = Random.nextFloat() * (w - 100f) + 50f,
                            y = Random.nextFloat() * (h - 100f) + 50f,
                            vx = (Random.nextFloat() - 0.5f) * 30f,
                            vy = (Random.nextFloat() - 0.5f) * 30f,
                            radius = Random.nextFloat() * 16f + 14f,
                            mass = Random.nextFloat() * 20f + 5f,
                            color = colorsList[Random.nextInt(colorsList.size)]
                        )
                    )
                }
                bodies = list
                gravityStrength = 15f
                gravityAngleDegrees = 90f // straight down
                restitution = 0.9f
                Toast.makeText(context, "Loaded Gravity Chaos Preset!", Toast.LENGTH_SHORT).show()
            }
            "billiards" -> {
                // Zero-G Elastic Collisions
                val list = mutableListOf<PhysicalBody>()
                // Grid of static circles plus one dynamic fast white cue ball!
                val columns = 3
                val rows = 3
                val startX = w / 2f - 40f
                val startY = h / 2f - 40f
                for (c in 0 until columns) {
                    for (r in 0 until rows) {
                        list.add(
                            PhysicalBody(
                                x = startX + c * 50f,
                                y = startY + r * 50f,
                                vx = 0f,
                                vy = 0f,
                                radius = 18f,
                                mass = 15f,
                                color = colorsList[3] // Yellow balls
                            )
                        )
                    }
                }
                // Fast cue ball hitting the rack
                list.add(
                    PhysicalBody(
                        x = w * 0.15f,
                        y = h / 2f,
                        vx = 35f,
                        vy = 2f,
                        radius = 20f,
                        mass = 25f,
                        color = Color.White
                    )
                )
                bodies = list
                gravityStrength = 0f // Zero-g space billiard
                restitution = 1.0f  // Perfect elastic
                Toast.makeText(context, "Loaded Space Billiards Preset!", Toast.LENGTH_SHORT).show()
            }
            "heavyrain" -> {
                // Dense vertical simulation
                val list = mutableListOf<PhysicalBody>()
                for (i in 0 until 12) {
                    list.add(
                        PhysicalBody(
                            x = (w / 13f) * (i + 1),
                            y = 40f + Random.nextFloat() * 80f,
                            vx = 0f,
                            vy = Random.nextFloat() * 5f,
                            radius = 16f + Random.nextFloat() * 8f,
                            mass = 12f,
                            color = colorsList[selectedColorIndex]
                        )
                    )
                }
                bodies = list
                gravityStrength = 12f
                gravityAngleDegrees = 90f
                restitution = 0.4f
                Toast.makeText(context, "Loaded Rain Shower Preset!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Main Simulation Coroutine Loop
    LaunchedEffect(isPaused) {
        if (isPaused) return@LaunchedEffect
        while (true) {
            delay(16) // ~60fps step
            
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            if (w <= 0f || h <= 0f) continue

            // Compute gravity acceleration components
            val gravityRads = Math.toRadians(gravityAngleDegrees.toDouble())
            val ax = (gravityStrength * cos(gravityRads)).toFloat() * 0.25f // Scaling acceleration for dt
            val ay = (gravityStrength * sin(gravityRads)).toFloat() * 0.25f

            val currentBodies = bodies.map { it } // Clone/reference list
            
            // Age and filter collision flashes
            val updatedFlashes = collisionFlashes.mapNotNull { flash ->
                flash.age += 0.08f
                if (flash.age < 1.0f) flash else null
            }
            collisionFlashes = updatedFlashes

            val newFlashes = mutableListOf<CollisionFlash>()

            // Update states, positions, friction and apply collisions
            currentBodies.forEach { body ->
                if (body.isBeingDragged) {
                    // Instantly update velocity based on pointer displacement
                    val instantVx = (lastTouchOffset.x - prevTouchOffset.x) * 0.4f
                    val instantVy = (lastTouchOffset.y - prevTouchOffset.y) * 0.4f
                    body.vx = instantVx.coerceIn(-40f, 40f)
                    body.vy = instantVy.coerceIn(-40f, 40f)
                    prevTouchOffset = lastTouchOffset
                } else {
                    // Apply Gravity acceleration
                    body.vx += ax
                    body.vy += ay

                    // Apply air resistance / damping friction
                    body.vx *= (1f - airResistance)
                    body.vy *= (1f - airResistance)

                    // Update positions
                    body.x += body.vx
                    body.y += body.vy

                    // Trail tracking
                    if (showTrails) {
                        body.trail.add(Offset(body.x, body.y))
                        if (body.trail.size > 8) {
                            body.trail.removeAt(0)
                        }
                    } else {
                        body.trail.clear()
                    }
                }

                // BOUNDARY COLLISION RESOLUTION (Standard Wall-bounce solver)
                // Left Wall
                if (body.x - body.radius < 0f) {
                    body.x = body.radius
                    body.vx = -body.vx * restitution
                    // Friction drag along the wall
                    body.vy *= 0.95f
                    if (abs(body.vx) > 1.5f) {
                        newFlashes.add(CollisionFlash(0f, body.y, maxRadius = body.radius * 0.8f))
                    }
                }
                // Right Wall
                else if (body.x + body.radius > w) {
                    body.x = w - body.radius
                    body.vx = -body.vx * restitution
                    body.vy *= 0.95f
                    if (abs(body.vx) > 1.5f) {
                        newFlashes.add(CollisionFlash(w, body.y, maxRadius = body.radius * 0.8f))
                    }
                }

                // Top Wall
                if (body.y - body.radius < 0f) {
                    body.y = body.radius
                    body.vy = -body.vy * restitution
                    body.vx *= 0.95f
                    if (abs(body.vy) > 1.5f) {
                        newFlashes.add(CollisionFlash(body.x, 0f, maxRadius = body.radius * 0.8f))
                    }
                }
                // Bottom Wall
                else if (body.y + body.radius > h) {
                    body.y = h - body.radius
                    body.vy = -body.vy * restitution
                    body.vx *= 0.95f
                    if (abs(body.vy) > 1.5f) {
                        newFlashes.add(CollisionFlash(body.x, h, maxRadius = body.radius * 0.8f))
                    }
                }
            }

            // RIGID BODY INTER-OBJECT COLLISION RESOLUTION
            // Dual loop collision solver (Elastic momentum + static separation)
            for (i in currentBodies.indices) {
                for (j in (i + 1) until currentBodies.size) {
                    val b1 = currentBodies[i]
                    val b2 = currentBodies[j]

                    val dx = b2.x - b1.x
                    val dy = b2.y - b1.y
                    val distance = sqrt(dx * dx + dy * dy)
                    val minDist = b1.radius + b2.radius

                    if (distance < minDist && distance > 0.01f) {
                        // 1. STATIC RESOLUTION: Push bodies apart to prevent merging/overlap gluing
                        val overlap = minDist - distance
                        val nx = dx / distance
                        val ny = dy / distance

                        // Push ratios based on relative inverse mass
                        val totalInvMass = (1f / b1.mass) + (1f / b2.mass)
                        val separationX = nx * overlap
                        val separationY = ny * overlap

                        if (!b1.isBeingDragged) {
                            b1.x -= separationX * (1f / b1.mass) / totalInvMass
                            b1.y -= separationY * (1f / b1.mass) / totalInvMass
                        }
                        if (!b2.isBeingDragged) {
                            b2.x += separationX * (1f / b2.mass) / totalInvMass
                            b2.y += separationY * (1f / b2.mass) / totalInvMass
                        }

                        // 2. DYNAMIC VELOCITY IMPULSE RESOLUTION: Conservation of linear momentum
                        val rvx = b2.vx - b1.vx
                        val rvy = b2.vy - b1.vy
                        val velAlongNormal = rvx * nx + rvy * ny

                        // Resolve only if bodies are moving towards each other
                        if (velAlongNormal < 0f) {
                            val impulseScalar = -(1f + restitution) * velAlongNormal / totalInvMass

                            if (!b1.isBeingDragged) {
                                b1.vx -= (impulseScalar * nx) / b1.mass
                                b1.vy -= (impulseScalar * ny) / b1.mass
                            }
                            if (!b2.isBeingDragged) {
                                b2.vx += (impulseScalar * nx) / b2.mass
                                b2.vy += (impulseScalar * ny) / b2.mass
                            }

                            // Dynamic collision impact flash at midpoint
                            val midX = b1.x + nx * b1.radius
                            val midY = b1.y + ny * b1.radius
                            newFlashes.add(CollisionFlash(midX, midY, maxRadius = (b1.radius + b2.radius) * 0.5f))
                        }
                    }
                }
            }

            if (newFlashes.isNotEmpty()) {
                collisionFlashes = (collisionFlashes + newFlashes).take(15) // Limit total flashes
            }

            // Trigger recomposition explicitly
            bodies = currentBodies
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Aesthetic Title Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "2D PHYSICS ENGINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Cosmic Particle Sandbox",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                bodies = emptyList()
                                collisionFlashes = emptyList()
                                Toast.makeText(context, "Canvas Reset!", Toast.LENGTH_SHORT).show()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("clear_sandbox_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Canvas", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // Live Dynamic Canvas Surface (The 2D Engine Viewport)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)) // High contrast Cosmic Slate Black background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                ) {
                    // Draw grid mesh behind inside canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    // Spawning validation: Ensure not intersecting existing body directly
                                    val isOverlapping = bodies.any {
                                        val dx = it.x - offset.x
                                        val dy = it.y - offset.y
                                        sqrt(dx * dx + dy * dy) < (it.radius + spawnRadius)
                                    }
                                    if (!isOverlapping && canvasSize.width > 0) {
                                        val newBody = PhysicalBody(
                                            x = offset.x,
                                            y = offset.y,
                                            vx = (Random.nextFloat() - 0.5f) * 12f,
                                            vy = (Random.nextFloat() - 0.5f) * 12f,
                                            radius = spawnRadius,
                                            mass = spawnMass,
                                            color = selectedColor
                                        )
                                        bodies = bodies + newBody
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                "Space occupied! Cannot spawn body.",
                                                Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        // Check if clicked near/inside any body
                                        val hitBody = bodies.firstOrNull {
                                            val dx = it.x - startOffset.x
                                            val dy = it.y - startOffset.y
                                            sqrt(dx * dx + dy * dy) <= it.radius + 15f
                                        }
                                        if (hitBody != null) {
                                            activeDraggedId = hitBody.id
                                            hitBody.isBeingDragged = true
                                            lastTouchOffset = startOffset
                                            prevTouchOffset = startOffset
                                        }
                                    },
                                    onDragEnd = {
                                        if (activeDraggedId != null) {
                                            bodies.firstOrNull { it.id == activeDraggedId }?.let {
                                                it.isBeingDragged = false
                                            }
                                            activeDraggedId = null
                                        }
                                    },
                                    onDragCancel = {
                                        if (activeDraggedId != null) {
                                            bodies.firstOrNull { it.id == activeDraggedId }?.let {
                                                it.isBeingDragged = false
                                            }
                                            activeDraggedId = null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeDraggedId != null) {
                                            bodies.firstOrNull { it.id == activeDraggedId }?.let { body ->
                                                val nextX = body.x + dragAmount.x
                                                val nextY = body.y + dragAmount.y
                                                // Constrain coordinates within canvas boundary safely
                                                body.x = nextX.coerceIn(body.radius, canvasSize.width.toFloat() - body.radius)
                                                body.y = nextY.coerceIn(body.radius, canvasSize.height.toFloat() - body.radius)
                                                
                                                lastTouchOffset = Offset(body.x, body.y)
                                            }
                                        }
                                    }
                                )
                            }
                            .testTag("physics_simulation_canvas")
                    ) {
                        val w = size.width
                        val h = size.height

                        // 1. Draw Grid lines (Slate style)
                        val gridSpacing = 40f
                        for (x in 0..(w / gridSpacing).toInt()) {
                            val lineX = x * gridSpacing
                            drawLine(
                                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                start = Offset(lineX, 0f),
                                end = Offset(lineX, h),
                                strokeWidth = 1f
                            )
                        }
                        for (y in 0..(h / gridSpacing).toInt()) {
                            val lineY = y * gridSpacing
                            drawLine(
                                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                start = Offset(0f, lineY),
                                end = Offset(w, lineY),
                                strokeWidth = 1f
                            )
                        }

                        // 2. Render Motion Trails
                        if (showTrails) {
                            bodies.forEach { body ->
                                if (body.trail.size > 1) {
                                    val path = Path()
                                    path.moveTo(body.trail.first().x, body.trail.first().y)
                                    for (i in 1 until body.trail.size) {
                                        path.lineTo(body.trail[i].x, body.trail[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = body.color.copy(alpha = 0.3f),
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }
                        }

                        // 3. Render Bodies
                        bodies.forEach { body ->
                            // Ambient glow under bodies
                            drawCircle(
                                color = body.color.copy(alpha = 0.25f),
                                radius = body.radius + 8f,
                                center = Offset(body.x, body.y)
                            )
                            // Main solid particle
                            drawCircle(
                                color = body.color,
                                radius = body.radius,
                                center = Offset(body.x, body.y)
                            )
                            // Inside highlights to add glass/3D bubble texture
                            drawCircle(
                                color = Color.White.copy(alpha = 0.4f),
                                radius = body.radius * 0.4f,
                                center = Offset(body.x - body.radius * 0.3f, body.y - body.radius * 0.3f)
                            )

                            // Render dynamic velocity vector arrows
                            if (showVectors && (abs(body.vx) > 0.1f || abs(body.vy) > 0.1f)) {
                                val speedScalar = 2.5f
                                val start = Offset(body.x, body.y)
                                val end = Offset(body.x + body.vx * speedScalar, body.y + body.vy * speedScalar)
                                
                                drawLine(
                                    color = Color.White.copy(alpha = 0.8f),
                                    start = start,
                                    end = end,
                                    strokeWidth = 3f
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3f,
                                    center = end
                                )
                            }
                        }

                        // 4. Render slingshot indicator if dragging
                        if (activeDraggedId != null) {
                            bodies.firstOrNull { it.id == activeDraggedId }?.let { draggedBody ->
                                // Draw a rubber spring vector showing tension f = -k*x
                                drawLine(
                                    color = draggedBody.color,
                                    start = Offset(draggedBody.x, draggedBody.y),
                                    end = lastTouchOffset,
                                    strokeWidth = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            }
                        }

                        // 5. Render Collision Flashes/Ripples
                        collisionFlashes.forEach { flash ->
                            val currentRad = flash.maxRadius * flash.age
                            val alpha = 1.0f - flash.age
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.6f),
                                radius = currentRad,
                                center = Offset(flash.x, flash.y),
                                style = Stroke(width = 3f)
                            )
                        }

                        // 6. Draw System Center of Mass if populated
                        if (bodies.size > 1) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.7f),
                                radius = 7f,
                                center = systemCenterOfMass
                            )
                            // Draw fine crosshair lines for COM
                            drawLine(
                                color = Color.Red.copy(alpha = 0.4f),
                                start = Offset(systemCenterOfMass.x - 20f, systemCenterOfMass.y),
                                end = Offset(systemCenterOfMass.x + 20f, systemCenterOfMass.y),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color.Red.copy(alpha = 0.4f),
                                start = Offset(systemCenterOfMass.x, systemCenterOfMass.y - 20f),
                                end = Offset(systemCenterOfMass.x, systemCenterOfMass.y + 20f),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // Floating canvas instruction HUD overlay
                    if (bodies.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = "Tap",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TAP TO SPAWN PARTICLES",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Drag and release any body to fling/throw it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Live Real-Time Dashboard Stats HUD
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total Particles Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Active Bodies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$activeBodyCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Kinetic Energy Card
                Card(
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total System Energy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        val formattedEnergy = String.format("%.1f J", totalKineticEnergy)
                        Text(formattedEnergy, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Engine Control & Parameter Adjustments Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Global Environment Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Gravity Strength Controls
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Gravity Accel: ${String.format("%.2f m/s²", gravityStrength)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            if (gravityStrength == 0f) {
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text("Zero-G", modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                        Slider(
                            value = gravityStrength,
                            onValueChange = { gravityStrength = it },
                            valueRange = 0f..25f,
                            modifier = Modifier.testTag("gravity_strength_slider")
                        )
                    }

                    // Gravity Angle/Vector Direction Controls
                    Column {
                        Text(
                            "Gravity Angle (Direction): ${gravityAngleDegrees.toInt()}°",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = gravityAngleDegrees,
                            onValueChange = { gravityAngleDegrees = it },
                            valueRange = 0f..360f,
                            modifier = Modifier.testTag("gravity_angle_slider")
                        )
                    }

                    // Bounciness / Restitution factor
                    Column {
                        Text(
                            "Elastic Restitution (Bounciness): ${String.format("%.2f", restitution)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = restitution,
                            onValueChange = { restitution = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("restitution_slider")
                        )
                    }

                    // Damping/Air Resistance
                    Column {
                        Text(
                            "Atmospheric Friction (Drag): ${String.format("%.3f", airResistance)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = airResistance,
                            onValueChange = { airResistance = it },
                            valueRange = 0f..0.08f,
                            modifier = Modifier.testTag("air_resistance_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle HUD options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showVectors,
                                onCheckedChange = { showVectors = it },
                                modifier = Modifier.testTag("toggle_vectors")
                            )
                            Text("Velocity Vectors", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showTrails,
                                onCheckedChange = { showTrails = it },
                                modifier = Modifier.testTag("toggle_trails")
                            )
                            Text("Motion Trails", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Particle Spawning Control & Colors
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Spawn Particle Blueprint",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Mass Configuration
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Mass: ${spawnMass.toInt()} kg",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = spawnMass,
                                onValueChange = { spawnMass = it },
                                valueRange = 1f..100f,
                                modifier = Modifier.testTag("spawn_mass_slider")
                            )
                        }

                        // Radius Configuration
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Radius: ${spawnRadius.toInt()} px",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = spawnRadius,
                                onValueChange = { spawnRadius = it },
                                valueRange = 10f..45f,
                                modifier = Modifier.testTag("spawn_radius_slider")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Spawn Color Accent:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Modern Color Picker row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorsList.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        BorderStroke(
                                            if (selectedColorIndex == idx) 4.dp else 1.dp,
                                            if (selectedColorIndex == idx) Color.White else Color.Transparent
                                        ),
                                        CircleShape
                                    )
                                    .clickable { selectedColorIndex = idx }
                                    .testTag("color_picker_$idx")
                            )
                        }
                    }
                }
            }
        }

        // Quick Preset Simulations Carousel
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Load Interactive Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { loadPreset("cradle") },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("preset_cradle"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = "Cradle", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cradle", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = { loadPreset("chaos") },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("preset_chaos"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = "Chaos", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chaos", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = { loadPreset("billiards") },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .testTag("preset_billiards"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.SportsTennis, contentDescription = "Billiards", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Space Billiard", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { loadPreset("heavyrain") },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("preset_heavy_rain"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Opacity, contentDescription = "Rain", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Heavy Rain", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = { isPaused = !isPaused },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .testTag("toggle_play_pause_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "PlayPause",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPaused) "Resume Engine" else "Pause Engine", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
