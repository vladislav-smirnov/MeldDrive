package io.github.airdaydreamers.melddrive.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_servers")
data class RemoteServer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val host: String,
    val port: Int = 445,
    val username: String? = null,
    val password: String? = null,
    val isAnonymous: Boolean = false,
    val type: String = "SMB"
)
