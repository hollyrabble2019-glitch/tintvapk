package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val logo: String,
    val country: String,
    val category: String,
    val streamUrl: String,
    val isFavorite: Boolean,
    val isCustom: Boolean,
    val streamsJson: String? = null
)
