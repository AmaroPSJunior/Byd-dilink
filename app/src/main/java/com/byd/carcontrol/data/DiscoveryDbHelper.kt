package com.byd.carcontrol.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.byd.carcontrol.discovery.ApiSafety
import com.byd.carcontrol.discovery.DiscoveryStatus

class DiscoveryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "byd_discovery_lab.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_DISCOVERIES = "discoveries"
        private const val TABLE_BINDERS = "binder_services"
        private const val TABLE_PROVIDERS = "content_providers"
        private const val TABLE_PERMISSIONS = "permissions"
        private const val TABLE_CLASSES = "api_classes"
        private const val TABLE_METHODS = "api_methods"
        private const val TABLE_TEST_RESULTS = "test_results"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_DISCOVERIES (
                id TEXT PRIMARY KEY,
                category TEXT,
                name TEXT,
                status TEXT,
                evidenceJson TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_BINDERS (
                name TEXT PRIMARY KEY,
                exists INTEGER,
                descriptor TEXT,
                isAlive INTEGER,
                status TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_PROVIDERS (
                authority TEXT PRIMARY KEY,
                packageName TEXT,
                name TEXT,
                exported INTEGER,
                readPermission TEXT,
                writePermission TEXT,
                classification TEXT,
                status TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_PERMISSIONS (
                permissionName TEXT PRIMARY KEY,
                exists INTEGER,
                protectionLevel TEXT,
                isGranted INTEGER,
                status TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CLASSES (
                className TEXT PRIMARY KEY,
                packageName TEXT,
                exists INTEGER,
                superclass TEXT,
                interfaces TEXT,
                status TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_METHODS (
                id TEXT PRIMARY KEY,
                className TEXT,
                methodName TEXT,
                visibility TEXT,
                isStatic INTEGER,
                returnType TEXT,
                parameterTypes TEXT,
                safety TEXT,
                status TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_TEST_RESULTS (
                id TEXT PRIMARY KEY,
                className TEXT,
                methodName TEXT,
                parameters TEXT,
                returnType TEXT,
                execution TEXT,
                resultType TEXT,
                result TEXT,
                exceptionType TEXT,
                exceptionMessage TEXT,
                stackTrace TEXT,
                durationMs INTEGER,
                timestamp INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DISCOVERIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BINDERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROVIDERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PERMISSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLASSES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_METHODS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEST_RESULTS")
        onCreate(db)
    }

    fun saveDiscovery(entity: DiscoveryEntity) {
        val cv = ContentValues().apply {
            put("id", entity.id)
            put("category", entity.category)
            put("name", entity.name)
            put("status", entity.status.name)
            put("evidenceJson", entity.evidenceJson)
            put("timestamp", entity.timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_DISCOVERIES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveBinder(entity: BinderServiceEntity) {
        val cv = ContentValues().apply {
            put("name", entity.name)
            put("exists", if (entity.exists) 1 else 0)
            put("descriptor", entity.descriptor)
            put("isAlive", if (entity.isAlive) 1 else 0)
            put("status", entity.status.name)
            put("timestamp", entity.timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_BINDERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveProvider(entity: ContentProviderEntity) {
        val cv = ContentValues().apply {
            put("authority", entity.authority)
            put("packageName", entity.packageName)
            put("name", entity.name)
            put("exported", if (entity.exported) 1 else 0)
            put("readPermission", entity.readPermission)
            put("writePermission", entity.writePermission)
            put("classification", entity.classification)
            put("status", entity.status.name)
            put("timestamp", entity.timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_PROVIDERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun savePermission(entity: PermissionEntity) {
        val cv = ContentValues().apply {
            put("permissionName", entity.permissionName)
            put("exists", if (entity.exists) 1 else 0)
            put("protectionLevel", entity.protectionLevel)
            put("isGranted", if (entity.isGranted) 1 else 0)
            put("status", entity.status.name)
            put("timestamp", entity.timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_PERMISSIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveClass(entity: ApiClassEntity) {
        val cv = ContentValues().apply {
            put("className", entity.className)
            put("packageName", entity.packageName)
            put("exists", if (entity.exists) 1 else 0)
            put("superclass", entity.superclass)
            put("interfaces", entity.interfaces.joinToString(","))
            put("status", entity.status.name)
            put("timestamp", entity.timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_CLASSES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveTestResult(entity: TestResultEntity) {
        val cv = ContentValues().apply {
            put("id", entity.id)
            put("className", entity.className)
            put("methodName", entity.methodName)
            put("parameters", entity.parameters)
            put("returnType", entity.returnType)
            put("execution", entity.execution)
            put("resultType", entity.resultType)
            put("result", entity.result)
            put("exceptionType", entity.exceptionType)
            put("exceptionMessage", entity.exceptionMessage)
            put("stackTrace", entity.stackTrace)
            put("durationMs", entity.durationMs)
            put("timestamp", entity.timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_TEST_RESULTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllDiscoveries(): List<DiscoveryEntity> {
        val list = mutableListOf<DiscoveryEntity>()
        val cursor = readableDatabase.query(TABLE_DISCOVERIES, null, null, null, null, null, "timestamp DESC")
        cursor.use { c ->
            val idxId = c.getColumnIndexOrThrow("id")
            val idxCat = c.getColumnIndexOrThrow("category")
            val idxName = c.getColumnIndexOrThrow("name")
            val idxStatus = c.getColumnIndexOrThrow("status")
            val idxEvidence = c.getColumnIndexOrThrow("evidenceJson")
            val idxTime = c.getColumnIndexOrThrow("timestamp")

            while (c.moveToNext()) {
                list.add(
                    DiscoveryEntity(
                        id = c.getString(idxId),
                        category = c.getString(idxCat),
                        name = c.getString(idxName),
                        status = try { DiscoveryStatus.valueOf(c.getString(idxStatus)) } catch (_: Exception) { DiscoveryStatus.UNKNOWN },
                        evidenceJson = c.getString(idxEvidence),
                        timestamp = c.getLong(idxTime)
                    )
                )
            }
        }
        return list
    }
}
