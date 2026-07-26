package com.vasilisneo.trackstar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// One queued write that failed because the device was offline, to be replayed when connectivity
// returns (the Room analogue of iOS's UserActionObject outbox). `type` selects the executor,
// `payload` is the JSON body it decodes. Auto-increment id gives a stable FIFO order. Kept per-user
// and — unlike the read caches — NOT wiped on logout, so an unsynced offline write survives a
// logout/login and still reaches the server.
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val type: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
)
