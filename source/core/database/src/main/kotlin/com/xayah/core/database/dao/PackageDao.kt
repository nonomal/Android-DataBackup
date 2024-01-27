package com.xayah.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.xayah.core.model.CompressionType
import com.xayah.core.model.OpType
import com.xayah.core.model.database.PackageEntity
import com.xayah.core.model.database.PackageEntityWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Upsert(entity = PackageEntity::class)
    suspend fun upsert(items: List<PackageEntity>)

    @Upsert(entity = PackageEntity::class)
    suspend fun upsert(item: PackageEntity)

    @Query("SELECT * FROM PackageEntity WHERE indexInfo_packageName = :packageName AND indexInfo_opType = :opType AND indexInfo_userId = :userId AND indexInfo_preserveId = :preserveId LIMIT 1")
    suspend fun query(packageName: String, opType: OpType, userId: Int, preserveId: Long): PackageEntity?

    @Query("SELECT * FROM PackageEntity WHERE indexInfo_packageName = :packageName AND indexInfo_opType = :opType AND indexInfo_userId = :userId")
    suspend fun query(packageName: String, opType: OpType, userId: Int): List<PackageEntity>

    @Query("SELECT * FROM PackageEntity WHERE indexInfo_packageName = :packageName AND indexInfo_opType = :opType AND indexInfo_userId = :userId AND indexInfo_preserveId = :preserveId AND indexInfo_compressionType = :ct LIMIT 1")
    suspend fun query(packageName: String, opType: OpType, userId: Int, preserveId: Long, ct: CompressionType): PackageEntity?

    @Query("SELECT * FROM PackageEntity WHERE extraInfo_activated = 1")
    suspend fun queryActivated(): List<PackageEntity>

    @Query("SELECT COUNT(*) FROM PackageEntity WHERE extraInfo_activated = 1")
    fun countActivatedFlow(): Flow<Long>

    @Query("UPDATE PackageEntity SET extraInfo_activated = 0")
    suspend fun clearActivated()

    @Query("UPDATE PackageEntity SET extraInfo_existed = 0 WHERE indexInfo_opType = :opType")
    suspend fun clearExisted(opType: OpType)

    @Query("SELECT * FROM PackageEntity WHERE indexInfo_packageName = :packageName AND indexInfo_opType = :opType AND indexInfo_userId = :userId AND indexInfo_preserveId = :preserveId LIMIT 1")
    fun queryFlow(packageName: String, opType: OpType, userId: Int, preserveId: Long): Flow<PackageEntity?>

    @Query("SELECT * FROM PackageEntity WHERE indexInfo_packageName = :packageName AND indexInfo_opType = :opType AND indexInfo_userId = :userId")
    fun queryFlow(packageName: String, opType: OpType, userId: Int): Flow<List<PackageEntity>>

    @Query(
        "SELECT *, COUNT(*) AS count" +
                " FROM (SELECT * FROM PackageEntity ORDER BY indexInfo_opType)" +
                " GROUP BY indexInfo_packageName, indexInfo_userId"
    )
    fun queryFlow(): Flow<List<PackageEntityWithCount>>

    @Delete(entity = PackageEntity::class)
    suspend fun delete(item: PackageEntity)
}
