package com.hang2515.personalcalendar.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_state", primaryKeys = ["id"])
data class LocalState(
    val id: Int = 1,
    val datasetId: String = UUID.randomUUID().toString(),
    val localRevision: Long = 0,
    val boundAccountSub: String? = null,
    val backupEnabled: Boolean = false,
    val lastBackedUpRevision: Long? = null,
    val lastBackupAt: String? = null,
    val cloudETag: String? = null,
)

@Dao
interface LocalStateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initialize(state: LocalState): Long

    @Query("SELECT id, datasetId, localRevision, boundAccountSub, backupEnabled, lastBackedUpRevision, lastBackupAt, cloudETag FROM local_state WHERE id = 1")
    suspend fun get(): LocalState?

    @Query("SELECT id, datasetId, localRevision, boundAccountSub, backupEnabled, lastBackedUpRevision, lastBackupAt, cloudETag FROM local_state WHERE id = 1")
    fun observe(): Flow<LocalState?>

    @Update
    suspend fun update(state: LocalState): Int

    @Query("UPDATE local_state SET localRevision = localRevision + 1 WHERE id = 1")
    suspend fun incrementRevision(): Int

    suspend fun recordChange() {
        initialize(LocalState())
        check(incrementRevision() == 1)
    }
}
