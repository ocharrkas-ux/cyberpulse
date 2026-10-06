package com.cyberpulse.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.cyberpulse.app.domain.SystemType
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun systemTypesToString(value: Set<SystemType>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToSystemTypes(value: String): Set<SystemType> =
        value.split(',').mapNotNullTo(linkedSetOf()) { name ->
            SystemType.entries.firstOrNull { it.name == name }
        }

    @TypeConverter
    fun stringsToString(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun stringToStrings(value: String): List<String> = value.split(',').filter { it.isNotBlank() }
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Article>

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(items: List<Article>)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM articles WHERE publishedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Database(entities = [Article::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cyberpulse.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
