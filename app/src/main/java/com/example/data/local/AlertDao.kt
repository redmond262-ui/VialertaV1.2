package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM cached_alerts WHERE status = 'active' ORDER BY timestampMillis DESC")
    fun getActiveAlerts(): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<AlertEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("UPDATE cached_alerts SET status = :status WHERE id = :id")
    suspend fun updateAlertStatus(id: String, status: String)

    @Query("DELETE FROM cached_alerts WHERE id = :id")
    suspend fun deleteAlert(id: String)

    // SOS queries
    @Query("SELECT * FROM cached_sos WHERE status = 'active' ORDER BY timestampMillis DESC")
    fun getActiveSos(): Flow<List<SosEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSos(sos: SosEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSosList(sosList: List<SosEntity>)

    @Query("UPDATE cached_sos SET status = :status WHERE id = :id")
    suspend fun updateSosStatus(id: String, status: String)

    @Query("DELETE FROM cached_sos")
    suspend fun clearAllCachedSos()

    @Query("DELETE FROM cached_alerts")
    suspend fun clearAllCachedAlerts()
}
