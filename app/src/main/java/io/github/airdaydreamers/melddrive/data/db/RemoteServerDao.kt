package io.github.airdaydreamers.melddrive.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteServerDao {
    @Query("SELECT * FROM remote_servers")
    fun getAllServers(): Flow<List<RemoteServer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertServer(server: RemoteServer): Long

    @Delete
    fun deleteServer(server: RemoteServer)

    @Query("SELECT * FROM remote_servers WHERE id = :id")
    fun getServerById(id: Long): RemoteServer?
}
