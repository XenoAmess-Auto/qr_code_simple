// security-crypto EncryptedSharedPreferences/MasterKeys are deprecated at import level;
// migration is a separate security-track upgrade, so the whole file is suppressed.
@file:Suppress("DEPRECATION")

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
import com.xenoamess.qrcodesimple.SecurePrefs
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room 数据库（支持加密）
 */
@Database(entities = [HistoryItem::class], version = 5, exportSchema = false)
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
                    Log.e(TAG, "Failed to open database; preserving data for explicit user recovery", e)
                    throw IllegalStateException("Unable to open history database", e)
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "qr_code_history_db_encrypted"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

            // Robolectric does not provide native SQLCipher support; run unencrypted in unit tests.
            if (!android.os.Build.FINGERPRINT.contains("robolectric")) {
                // sqlcipher-android 4.6+（net.zetetic 新坐标）不再隐式加载 native 库，必须显式 loadLibrary
                System.loadLibrary("sqlcipher")
                val passphrase = getDatabasePassword(context)
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray()))
            }

            return builder.build()
        }

        /**
         * 获取数据库密码。
         *
         * 历史链：明文 SharedPreferences（c6d8b55）→ EncryptedSharedPreferences（c23484b）
         * → Keystore AES/GCM 自管加密（SecurePrefs，security-crypto 弃用后）。
         * 读取顺序：SecurePrefs → 旧 EncryptedSharedPreferences（命中即迁移并删除）
         * → 明文（命中即迁移并删除）→ 生成新密码。
         */
        private fun getDatabasePassword(context: Context): String {
            // 1. 新格式：SecurePrefs（Keystore AES/GCM）
            SecurePrefs.getString(context, PREFS_NAME, KEY_DB_PASSWORD)?.let { return it }

            // 2. 旧 EncryptedSharedPreferences：命中后迁移到 SecurePrefs
            var password: String? = null
            try {
                val encPrefs = getEncryptedSharedPreferences(context)
                password = encPrefs.getString(KEY_DB_PASSWORD, null)
                if (password != null) {
                    Log.i(TAG, "Migrating DB password from EncryptedSharedPreferences to SecurePrefs")
                    SecurePrefs.putString(context, PREFS_NAME, KEY_DB_PASSWORD, password)
                    try {
                        encPrefs.edit().remove(KEY_DB_PASSWORD).apply()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not remove legacy encrypted entry", e)
                    }
                    return password
                }
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable, trying legacy plain prefs", e)
            }

            // 3. 明文存量：迁移到 SecurePrefs 后删除
            try {
                val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                password = plainPrefs.getString(KEY_DB_PASSWORD, null)
                if (password != null) {
                    Log.i(TAG, "Migrating DB password from plain prefs to SecurePrefs")
                    SecurePrefs.putString(context, PREFS_NAME, KEY_DB_PASSWORD, password)
                    plainPrefs.edit().remove(KEY_DB_PASSWORD).apply()
                    return password
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read legacy plain prefs", e)
            }

            // 4. 都没有就生成新密码
            password = generateRandomPassword()
            Log.i(TAG, "Generating new DB password")
            SecurePrefs.putString(context, PREFS_NAME, KEY_DB_PASSWORD, password)
            return password
        }

        // EncryptedSharedPreferences/MasterKeys are deprecated; migrating them is a
        // security-crypto upgrade tracked separately from routine cleanup.
        @Suppress("DEPRECATION")
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
                // Some v1 builds shipped these fields without a version bump.
                addColumnIfMissing(db, "barcodeFormat", "TEXT")
                addColumnIfMissing(db, "isFavorite", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "notes", "TEXT")
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

        /** Consolidates legacy duplicates before enforcing one row per history identity. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM history
                    WHERE EXISTS (
                        SELECT 1 FROM history AS newer
                        WHERE newer.content = history.content
                          AND newer.isGenerated = history.isGenerated
                          AND (
                              newer.timestamp > history.timestamp
                              OR (newer.timestamp = history.timestamp AND newer.id > history.id)
                          )
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_content_isGenerated ON history(content, isGenerated)")
            }
        }

        private fun addColumnIfMissing(db: SupportSQLiteDatabase, name: String, definition: String) {
            db.query("PRAGMA table_info(history)").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == name) return
                }
            }
            db.execSQL("ALTER TABLE history ADD COLUMN $name $definition")
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
