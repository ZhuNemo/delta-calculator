package com.nemo.deltacalc.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 输入的 LaTeX，原文保留 */
    val latex: String,
    /** 结果文本 */
    val result: String,
    /** 求解步骤，换行分隔；普通计算时为空 */
    val detail: String = "",
    /** "calc" / "quadratic" / "system" */
    val mode: String,
    val createdAt: Long,
)

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(item: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "delta.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

class HistoryRepository(private val dao: HistoryDao) {

    fun observe(): Flow<List<HistoryEntity>> = dao.observeRecent(300)

    suspend fun add(latex: String, result: String, detail: String, mode: String) {
        if (latex.isBlank()) return
        dao.insert(
            HistoryEntity(
                latex = latex,
                result = result,
                detail = detail,
                mode = mode,
                createdAt = System.currentTimeMillis(),
            )
        )
        // 只保留最近 300 条，避免数据库无限增长
        if (dao.count() > 320) dao.trim(300)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.clear()
}
