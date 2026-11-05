package app.allinonereminder.data.local

import app.allinonereminder.data.local.dao.ChangeLogDao
import app.allinonereminder.data.local.entity.ChangeLogEntity
import java.util.UUID

class ChangeLogWriter(
    private val changeLogDao: ChangeLogDao
) {
    suspend fun recordChange(
        entityType: String,
        entityId: String,
        field: String,
        oldValue: String?,
        newValue: String?,
        changedAt: Long = System.currentTimeMillis(),
        deviceId: String
    ) {
        if (oldValue != newValue) {
            val entry = ChangeLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                field = field,
                oldValue = oldValue,
                newValue = newValue,
                changedAt = changedAt,
                deviceId = deviceId
            )
            changeLogDao.insert(entry)
        }
    }

    suspend fun recordDiffs(
        entityType: String,
        entityId: String,
        deviceId: String,
        oldFields: Map<String, Any?>,
        newFields: Map<String, Any?>,
        changedAt: Long = System.currentTimeMillis()
    ) {
        val allKeys = oldFields.keys + newFields.keys
        val logs = mutableListOf<ChangeLogEntity>()

        for (key in allKeys) {
            val oldVal = oldFields[key]?.toString()
            val newVal = newFields[key]?.toString()
            if (oldVal != newVal) {
                logs.add(
                    ChangeLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = entityType,
                        entityId = entityId,
                        field = key,
                        oldValue = oldVal,
                        newValue = newVal,
                        changedAt = changedAt,
                        deviceId = deviceId
                    )
                )
            }
        }

        if (logs.isNotEmpty()) {
            changeLogDao.insertAll(logs)
        }
    }
}

