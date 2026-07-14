package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimationDao {
    @Query("SELECT * FROM saved_animations ORDER BY timestamp DESC")
    fun getAllAnimations(): Flow<List<SavedAnimation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnimation(animation: SavedAnimation): Long

    @Query("DELETE FROM saved_animations WHERE id = :id")
    suspend fun deleteAnimationById(id: Int)
    
    @Query("SELECT COUNT(*) FROM saved_animations")
    suspend fun getCount(): Int
}
