package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_animations")
data class SavedAnimation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val formulaType: String, // "density", "spring", "newton", "rocket", "relativity", "custom"
    val formulaExpression: String, // standard math rendering e.g. "m = ρ * V"
    val variablesJson: String, // serialized parameters (sliders, defaults, limits)
    val timestamp: Long = System.currentTimeMillis()
)
