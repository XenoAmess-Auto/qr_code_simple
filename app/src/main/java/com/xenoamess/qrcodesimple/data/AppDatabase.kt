package com.xenoamess.qrcodesimple.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Room 数据库（支持加密）
 */
@Database(entities = [HistoryItem::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 数据库加密密钥存储
        private const val PREFS_NAME = "db_security"
        private const val KEY_DB_PASSWORD = "db_password"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "qr_code_history_db_encrypted"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()

            // Robolectric does not provide native SQLCipher support; run unencrypted in unit tests.
            if (!android.os.Build.FINGERPRINT.contains("robolectric")) {
                val passphrase = getDatabasePassword(context)
                builder.openHelperFactory(SupportFactory(passphrase.toByteArray()))
            }

            return builder.build()
        }

        /**
         * 获取数据库密码（首次生成并保存）
         */
        private fun getDatabasePassword(context: Context): String {
            val prefs = getEncryptedSharedPreferences(context)
            var password = prefs.getString(KEY_DB_PASSWORD, null)

            if (password == null) {
                // 生成随机密码
                password = generateRandomPassword()
                prefs.edit().putString(KEY_DB_PASSWORD, password).apply()
            }

            return password
        }

        private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            return EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /**
         * 生成随机密码
         */
        private fun generateRandomPassword(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
            return (1..32)
                .map { chars.random() }
                .joinToString("")
        }

        /**
         * 数据库迁移：从版本1（未加密）到版本2（加密）
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 表结构没有变化，只是切换到加密数据库
                // Room 会自动处理
            }
        }

        /**
         * 数据库迁移：从版本2到版本3，添加标签字段
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE history ADD COLUMN tags TEXT")
            }
        }

        /**
         * 重置数据库（用于密码丢失等情况）
         */
        fun resetDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null

                // 删除数据库文件
                context.deleteDatabase("qr_code_history_db")
                context.deleteDatabase("qr_code_history_db_encrypted")

                // 清除密码
                getEncryptedSharedPreferences(context)
                    .edit()
                    .remove(KEY_DB_PASSWORD)
                    .apply()
            }
        }
    }
}