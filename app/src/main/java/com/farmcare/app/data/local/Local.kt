package com.farmcare.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** A write made while offline. opId is also sent as client_op_id, so the server rejects duplicates. */
@Entity(tableName = "pending_ops")
data class PendingOp(@PrimaryKey val opId: String, val tableName: String, val label: String, val payload: String,
                     val status: String = "pending", val error: String? = null, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "snapshot")
data class SnapshotRow(@PrimaryKey val id: Int = 1, val json: String, val savedAt: Long)

@Dao
interface LocalDao {
    @Query("SELECT * FROM pending_ops ORDER BY createdAt") fun observeOps(): Flow<List<PendingOp>>
    @Query("SELECT * FROM pending_ops WHERE status != 'synced' ORDER BY createdAt") suspend fun openOps(): List<PendingOp>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOp(op: PendingOp)
    @Query("UPDATE pending_ops SET status = :status, error = :error WHERE opId = :id") suspend fun mark(id: String, status: String, error: String?)
    @Query("DELETE FROM pending_ops WHERE status = 'synced'") suspend fun clearSynced()
    @Query("DELETE FROM pending_ops WHERE opId = :id") suspend fun delete(id: String)
    @Query("DELETE FROM pending_ops") suspend fun clearOps()
    @Upsert suspend fun saveSnapshot(row: SnapshotRow)
    @Query("SELECT * FROM snapshot WHERE id = 1") suspend fun snapshot(): SnapshotRow?
    @Query("DELETE FROM snapshot") suspend fun clearSnapshot()
}

@Database(entities = [PendingOp::class, SnapshotRow::class], version = 1, exportSchema = false)
abstract class LocalDb : RoomDatabase() { abstract fun dao(): LocalDao }
