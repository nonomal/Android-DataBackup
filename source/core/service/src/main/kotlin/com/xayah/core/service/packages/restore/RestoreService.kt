package com.xayah.core.service.packages.restore

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.view.SurfaceControlHidden
import com.xayah.core.data.repository.PackageRepository
import com.xayah.core.data.repository.TaskRepository
import com.xayah.core.database.dao.PackageDao
import com.xayah.core.database.dao.TaskDao
import com.xayah.core.datastore.readAutoScreenOff
import com.xayah.core.datastore.readKillAppOption
import com.xayah.core.datastore.readResetRestoreList
import com.xayah.core.datastore.readRestoreUser
import com.xayah.core.datastore.readScreenOffTimeout
import com.xayah.core.datastore.readSelectionType
import com.xayah.core.datastore.saveLastRestoreTime
import com.xayah.core.datastore.saveScreenOffCountDown
import com.xayah.core.datastore.saveScreenOffTimeout
import com.xayah.core.model.DataType
import com.xayah.core.model.KillAppOption
import com.xayah.core.model.OpType
import com.xayah.core.model.OperationState
import com.xayah.core.model.ProcessingType
import com.xayah.core.model.TaskType
import com.xayah.core.model.database.Info
import com.xayah.core.model.database.ProcessingInfoEntity
import com.xayah.core.model.database.TaskDetailPackageEntity
import com.xayah.core.model.database.TaskEntity
import com.xayah.core.rootservice.service.RemoteRootService
import com.xayah.core.rootservice.util.withIOContext
import com.xayah.core.service.R
import com.xayah.core.service.util.PackagesRestoreUtil
import com.xayah.core.util.DateUtil
import com.xayah.core.util.LogUtil
import com.xayah.core.util.NotificationUtil
import com.xayah.core.util.PathUtil
import com.xayah.core.util.command.BaseUtil
import com.xayah.core.util.command.PreparationUtil
import com.xayah.core.util.localBackupSaveDir
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi

@AndroidEntryPoint
internal abstract class RestoreService : Service() {
    companion object {
        private const val TAG = "PackagesRestoreServiceImpl"
    }

    private val binder = OperationLocalBinder()

    override fun onBind(intent: Intent): IBinder {
        startForeground(1, NotificationUtil.getForegroundNotification(applicationContext))
        return binder
    }

    inner class OperationLocalBinder : Binder() {
        fun getService(): RestoreService = this@RestoreService
    }

    private val mutex = Mutex()
    internal val context by lazy { applicationContext }

    internal fun log(onMsg: () -> String): String = run {
        val msg = onMsg()
        LogUtil.log { TAG to msg }
        msg
    }

    abstract val rootService: RemoteRootService
    abstract val pathUtil: PathUtil
    abstract val taskDao: TaskDao
    abstract val packageDao: PackageDao
    abstract val packagesRestoreUtil: PackagesRestoreUtil
    abstract val taskRepository: TaskRepository
    abstract val packageRepository: PackageRepository

    private val notificationBuilder by lazy { NotificationUtil.getProgressNotificationBuilder(context) }
    internal var startTimestamp: Long = 0
    internal var endTimestamp: Long = 0
    abstract val taskEntity: TaskEntity

    private lateinit var preSetUpInstEnvEntity: ProcessingInfoEntity
    private lateinit var postDataProcessingEntity: ProcessingInfoEntity
    private val pkgEntities: MutableList<TaskDetailPackageEntity> = mutableListOf()

    private var isInitialized: Boolean = false
    internal var restoreUser = -1

    @SuppressLint("StringFormatInvalid")
    suspend fun initialize(cloudName: String, cloudRemote: String): Long {
        mutex.withLock {
            if (rootService.getScreenOffTimeout() != Int.MAX_VALUE) {
                context.saveScreenOffTimeout(rootService.getScreenOffTimeout())
            }
            restoreUser = context.readRestoreUser().first()
            if (isInitialized.not()) {
                taskEntity.also {
                    it.id = taskDao.upsert(it)
                }
                preSetUpInstEnvEntity = ProcessingInfoEntity(
                    id = 0,
                    taskId = taskEntity.id,
                    title = context.getString(R.string.set_up_inst_env),
                    type = ProcessingType.PREPROCESSING,
                ).apply {
                    id = taskDao.upsert(this)
                }
                postDataProcessingEntity = ProcessingInfoEntity(
                    id = 0,
                    taskId = taskEntity.id,
                    title = context.getString(R.string.necessary_remaining_data_processing),
                    type = ProcessingType.POST_PROCESSING,
                ).apply {
                    id = taskDao.upsert(this)
                }

                val packages = packageRepository.filterRestore(
                    if (cloudName.isEmpty())
                        packageRepository.queryActivated(OpType.RESTORE, "", context.localBackupSaveDir())
                    else
                        packageRepository.queryActivated(OpType.RESTORE, cloudName, cloudRemote)
                )
                packages.forEach { pkg ->
                    pkgEntities.add(TaskDetailPackageEntity(
                        id = 0,
                        taskId = taskEntity.id,
                        packageEntity = pkg,
                        apkInfo = Info(title = context.getString(com.xayah.core.data.R.string.args_restore, DataType.PACKAGE_APK.type.uppercase())),
                        userInfo = Info(title = context.getString(com.xayah.core.data.R.string.args_restore, DataType.PACKAGE_USER.type.uppercase())),
                        userDeInfo = Info(title = context.getString(com.xayah.core.data.R.string.args_restore, DataType.PACKAGE_USER_DE.type.uppercase())),
                        dataInfo = Info(title = context.getString(com.xayah.core.data.R.string.args_restore, DataType.PACKAGE_DATA.type.uppercase())),
                        obbInfo = Info(title = context.getString(com.xayah.core.data.R.string.args_restore, DataType.PACKAGE_OBB.type.uppercase())),
                        mediaInfo = Info(title = context.getString(com.xayah.core.data.R.string.args_restore, DataType.PACKAGE_MEDIA.type.uppercase())),
                    ).apply {
                        id = taskDao.upsert(this)
                    })
                }
                isInitialized = true
            }
            return taskEntity.id
        }
    }

    suspend fun preprocessing() = withIOContext {
        mutex.withLock {
            if (context.readAutoScreenOff().first()) {
                context.saveScreenOffCountDown(3)
            }
            preSetUpInstEnvEntity.also {
                it.state = OperationState.PROCESSING
                taskDao.upsert(it)
            }
            startTimestamp = DateUtil.getTimestamp()

            NotificationUtil.notify(context, notificationBuilder, context.getString(R.string.restoring), context.getString(R.string.preprocessing))
            log { "Preprocessing is starting." }

            log { "Trying to enable adb install permissions." }
            PreparationUtil.setInstallEnv().apply {
                preSetUpInstEnvEntity.state = if (isSuccess) OperationState.DONE else OperationState.ERROR
                if (isSuccess.not()) preSetUpInstEnvEntity.log = outString
            }

            runCatchingOnService { createTargetDirs() }

            taskDao.upsert(preSetUpInstEnvEntity)

            taskEntity.also {
                it.processingIndex++
                taskDao.upsert(it)
            }
        }
    }

    abstract suspend fun createTargetDirs()
    abstract suspend fun restorePackage(t: TaskDetailPackageEntity)
    abstract suspend fun clear()

    private suspend fun runCatchingOnService(block: suspend () -> Unit) = runCatching { block() }.onFailure {
        log { it.message.toString() }
        rootService.onFailure(it)
    }

    @ExperimentalSerializationApi
    suspend fun processing() = withIOContext {
        mutex.withLock {
            log { "Processing is starting." }
            val selectionType = context.readSelectionType().first()
            log { "Selection: $selectionType." }

            // createTargetDirs() before readStatFs().
            taskEntity.also {
                it.startTimestamp = startTimestamp
                it.rawBytes = taskRepository.getRawBytes(TaskType.PACKAGE)
                it.availableBytes = taskRepository.getAvailableBytes(OpType.RESTORE)
                it.totalBytes = taskRepository.getTotalBytes(OpType.RESTORE)
            }

            log { "Task count: ${pkgEntities.size}." }
            taskEntity.also {
                it.totalCount = pkgEntities.size
                taskDao.upsert(it)
            }

            val killAppOption = context.readKillAppOption().first()

            pkgEntities.forEachIndexed { index, pkg ->
                NotificationUtil.notify(
                    context,
                    notificationBuilder,
                    context.getString(R.string.restoring),
                    pkg.packageEntity.packageInfo.label,
                    pkgEntities.size,
                    index
                )
                log { "Current package: ${pkg.packageEntity}" }

                // Kill the package.
                when (killAppOption) {
                    KillAppOption.DISABLED -> {
                        log { "Won't kill ${pkg.packageEntity.packageName}." }
                    }

                    KillAppOption.OPTION_I -> {
                        log { "Trying to kill ${pkg.packageEntity.packageName}." }
                        BaseUtil.killPackage(context = context, userId = pkg.packageEntity.userId, packageName = pkg.packageEntity.packageName)
                    }

                    KillAppOption.OPTION_II -> {
                        log { "Trying to kill ${pkg.packageEntity.packageName}." }
                        rootService.forceStopPackageAsUser(pkg.packageEntity.packageName, pkg.packageEntity.userId)
                    }
                }

                runCatchingOnService { restorePackage(pkg) }

                taskEntity.also {
                    it.processingIndex++
                    taskDao.upsert(it)
                }
            }
        }
    }

    @ExperimentalSerializationApi
    suspend fun postProcessing() = withIOContext {
        mutex.withLock {
            NotificationUtil.notify(
                context,
                notificationBuilder,
                context.getString(R.string.restoring),
                context.getString(R.string.wait_for_remaining_data_processing)
            )
            log { "PostProcessing is starting." }

            postDataProcessingEntity.also {
                it.state = OperationState.PROCESSING
                taskDao.upsert(it)
            }

            runCatchingOnService { clear() }

            postDataProcessingEntity.also {
                it.state = OperationState.DONE
                taskDao.upsert(it)
            }

            if (context.readResetRestoreList().first()) packageDao.clearActivated()
            endTimestamp = DateUtil.getTimestamp()
            taskEntity.also {
                it.endTimestamp = endTimestamp
                it.isProcessing = false
                taskDao.upsert(it)
            }
            val time = DateUtil.getShortRelativeTimeSpanString(context = context, time1 = startTimestamp, time2 = endTimestamp)
            context.saveLastRestoreTime(endTimestamp)
            NotificationUtil.notify(
                context,
                notificationBuilder,
                context.getString(R.string.restore_completed),
                "${time}, ${taskEntity.successCount} ${context.getString(R.string.succeed)}, ${taskEntity.failureCount} ${context.getString(R.string.failed)}",
                ongoing = false
            )

            taskEntity.also {
                it.processingIndex++
                taskDao.upsert(it)
            }

            rootService.setScreenOffTimeout(context.readScreenOffTimeout().first())
            rootService.setDisplayPowerMode(SurfaceControlHidden.POWER_MODE_NORMAL)
        }
    }
}
