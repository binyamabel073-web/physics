package com.example.data

data class SimulationVariable(
    val key: String,
    val value: Double,
    val displayName: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val isEditable: Boolean = true
) {
    companion object {
        fun serializeList(list: List<SimulationVariable>): String {
            return list.joinToString(";") { 
                // Escape colons and semicolons in display name and unit
                val escapedName = it.displayName.replace(":", "\\:").replace(";", "\\;")
                val escapedUnit = it.unit.replace(":", "\\:").replace(";", "\\;")
                "${it.key}:${it.value}:$escapedName:$escapedUnit:${it.min}:${it.max}:${it.isEditable}" 
            }
        }

        fun deserializeList(str: String): List<SimulationVariable> {
            if (str.isBlank()) return emptyList()
            return try {
                str.split(";").mapNotNull { item ->
                    val parts = item.split(":")
                    if (parts.size >= 7) {
                        val key = parts[0]
                        val value = parts[1].toDoubleOrNull() ?: 0.0
                        val displayName = parts[2].replace("\\:", ":").replace("\\;", ";")
                        val unit = parts[3].replace("\\:", ":").replace("\\;", ";")
                        val min = parts[4].toDoubleOrNull() ?: 0.0
                        val max = parts[5].toDoubleOrNull() ?: 100.0
                        val isEditable = parts[6].toBooleanStrictOrNull() ?: true
                        SimulationVariable(key, value, displayName, unit, min, max, isEditable)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
