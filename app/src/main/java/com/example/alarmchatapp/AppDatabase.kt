package com.example.alarmchatapp

import android.content.Context
import androidx.room.*

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val timeMillis: Long,
    val recurring: Boolean = false,
    val recurringDays: List<Int>? = null,
    val hour: Int = 0,
    val minute: Int = 0
)

@Dao
interface AlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(alarm: Alarm): Long

    @Query("SELECT * FROM alarms ORDER BY timeMillis ASC")
    fun getAll(): List<Alarm>

    @Delete
    fun delete(alarm: Alarm)
}

@Database(entities = [Alarm::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                val temp = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alarm_db"
                ).build()
                instance = temp
                temp
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromList(list: List<Int>?): String? = list?.joinToString(",")

    @TypeConverter
    fun toList(data: String?): List<Int>? = data?.split(",")?.map { it.toInt() }
}
