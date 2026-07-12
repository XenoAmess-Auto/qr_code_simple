package com.xenoamess.qrcodesimple.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
@Database(entities = [HistoryItem::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 数据库加密密钥存储
        private const val PREFS_NAME = "db_security"
        private const val KEY_DB_PASSWORD = "db_password"
        private const val LEGACY_DB_NAME = "qr_code_history_db"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    val db = buildDatabase(context)
                    // Force-open the DB so SQLCipher password mismatches throw HERE
                    // (instead of later inside a coroutine that can't be caught).
                    db.openHelper.writableDatabase
                    INSTANCE = db
                    db
                } catch (e: Exception) {
                    // 数据库无法打开（密码不匹配、密钥损坏等），重置后重建
                    Log.e(TAG, "Failed to open database, resetting and recreating", e)
                    resetDatabase(context)
                    val db = buildDatabase(context)
                    db.openHelper.writableDatabase
                    INSTANCE = db
                    db
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "qr_code_history_db_encrypted"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)

            // Robolectric does not provide native SQLCipher support; run unencrypted in unit tests.
            if (!android.os.Build.FINGERPRINT.contains("robolectric")) {
                val passphrase = getDatabasePassword(context)
                builder.openHelperFactory(SupportFactory(passphrase.toByteArray()))
            }

            return builder.build()
        }

        /**
         * 获取数据库密码。
         *
         * 历史上密码曾以明文存在普通 SharedPreferences 里（commit c6d8b55），
         * 后来改成 EncryptedSharedPreferences（commit c23484b）。
         * 老用户升级后 EncryptedSharedPreferences 无法解密旧的明文条目 → 崩溃。
         * 这里做三级回退：加密 prefs → 明文 prefs（迁移）→ 生成新密码。
         */
        private fun getDatabasePassword(context: Context): String {
            // 1. 先尝试 EncryptedSharedPreferences（新方式）
            var password: String? = null
            try {
                val encPrefs = getEncryptedSharedPreferences(context)
                password = encPrefs.getString(KEY_DB_PASSWORD, null)
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable, trying legacy plain prefs", e)
            }

            // 2. 回退到普通 SharedPreferences（老方式），并把密码迁移到加密 prefs
            if (password == null) {
                try {
                    val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    password = plainPrefs.getString(KEY_DB_PASSWORD, null)
                    if (password != null) {
                        Log.i(TAG, "Migrating DB password from plain to encrypted prefs")
                        try {
                            getEncryptedSharedPreferences(context)
                                .edit()
                                .putString(KEY_DB_PASSWORD, password)
                                .apply()
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not migrate password to encrypted prefs, keeping plain", e)
                        }
                        plainPrefs.edit().remove(KEY_DB_PASSWORD).apply()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read legacy plain prefs", e)
                }
            }

            // 3. 都没有就生成新密码
            if (password == null) {
                password = generateRandomPassword()
                Log.i(TAG, "Generating new DB password")
                try {
                    getEncryptedSharedPreferences(context)
                        .edit()
                        .putString(KEY_DB_PASSWORD, password)
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save password to encrypted prefs, using plain", e)
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_DB_PASSWORD, password)
                        .apply()
                }
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
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
            return (1..32)
                .map { chars.random() }
                .joinToString("")
        }

    /**
     * 数据库迁移：从版本3到版本4，添加生成样式参数字段
     */
    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE history ADD COLUMN styleJson TEXT")
        }
    }

    /**
     * 数据库迁移：从版本1（未加密）到版本2（加密）
     */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 表结构没有变化，只是切换到加密数据库
                // Room 会自动处理
            }
        }

        /**
         * 数据库迁移：从版本2到版本3，添加标签字段
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN tags TEXT")
            }
        }

        /**
         * 重置数据库（用于密码丢失等情况）
         */
        fun resetDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null

                // 删除数据库文件（旧的非加密 + 新的加密）
                context.deleteDatabase(LEGACY_DB_NAME)
                context.deleteDatabase("qr_code_history_db_encrypted")

                // 清除密码（加密 + 明文都清）
                try {
                    getEncryptedSharedPreferences(context)
                        .edit()
                        .remove(KEY_DB_PASSWORD)
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear encrypted prefs password", e)
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_DB_PASSWORD)
                    .apply()
            }
        }
    }
}