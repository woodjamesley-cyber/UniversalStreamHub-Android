package com.universalstreamhub.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlist_local")
data class WatchlistEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val genres: String,
    val addedAt: Long,
)

@Entity(tableName = "progress_local")
data class ProgressEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val posterUrl: String?,
    val genres: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "history_local")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val posterUrl: String?,
    val genres: String,
    val viewedAt: Long,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String?,
    val sourceUrl: String,
    val fileUri: String,
    val bytesTotal: Long,
    val bytesDownloaded: Long,
    val status: String,
    val quality: String?,
    val license: String?,
    val errorMessage: String?,
    val updatedAt: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM watchlist_local ORDER BY addedAt DESC")
    fun watchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchlist(items: List<WatchlistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist_local WHERE id = :id")
    suspend fun removeFromWatchlist(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_local WHERE id = :id)")
    fun isInWatchlist(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_local WHERE id = :id)")
    suspend fun isInWatchlistNow(id: String): Boolean

    @Query("SELECT * FROM progress_local WHERE completed = 0 AND durationMs > 0 ORDER BY updatedAt DESC")
    fun continueWatching(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress_local WHERE id = :id")
    suspend fun progressFor(id: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(item: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgressAll(items: List<ProgressEntity>)

    @Query("SELECT * FROM progress_local ORDER BY updatedAt DESC")
    suspend fun allProgress(): List<ProgressEntity>

    @Query("SELECT * FROM history_local ORDER BY viewedAt DESC LIMIT 100")
    fun history(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(item: HistoryEntity)

    @Query("DELETE FROM watchlist_local")
    suspend fun clearWatchlist()

    @Query("DELETE FROM progress_local")
    suspend fun clearProgress()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun downloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun find(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads WHERE status = 'COMPLETE'")
    suspend fun completedBytes(): Long

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads WHERE status != 'FAILED'")
    suspend fun usedBytes(): Long
}

@Database(
    entities = [WatchlistEntity::class, ProgressEntity::class, HistoryEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "universal_stream_hub.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
