package app.allinonereminder.data.mapper

import app.allinonereminder.data.local.entity.ChangeLogEntity
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.ItemPhotoEntity
import app.allinonereminder.data.local.entity.MealAnchorEntity
import app.allinonereminder.data.local.entity.MealSessionEntity
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
import app.allinonereminder.data.local.entity.StockEventEntity
import app.allinonereminder.model.ChangeLog
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemPhoto
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealSession
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.Profile
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.StockEvent

fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    colorArgb = colorArgb,
    photoPath = photoPath,
    birthYear = birthYear,
    allergies = allergies,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    photoPath = photoPath,
    birthYear = birthYear,
    allergies = allergies,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun MealAnchorEntity.toDomain() = MealAnchor(
    id = id,
    profileId = profileId,
    dayType = dayType,
    meal = meal,
    minuteOfDay = minuteOfDay,
    updatedAt = updatedAt
)

fun MealAnchor.toEntity() = MealAnchorEntity(
    id = id,
    profileId = profileId,
    dayType = dayType,
    meal = meal,
    minuteOfDay = minuteOfDay,
    updatedAt = updatedAt
)

fun ItemEntity.toDomain() = Item(
    id = id,
    profileId = profileId,
    type = type,
    name = name,
    dosageAmount = dosageAmount,
    dosageUnit = dosageUnit,
    form = form,
    instructions = instructions,
    criticality = criticality,
    graceMinutes = graceMinutes,
    reRingMinutes = reRingMinutes,
    minGapMinutes = minGapMinutes,
    timePolicy = timePolicy,
    homeZoneId = homeZoneId,
    startDate = startDate,
    endDate = endDate,
    exerciseTargetMinutes = exerciseTargetMinutes,
    maxPerDay = maxPerDay,
    unitsPerDose = unitsPerDose,
    refillThreshold = refillThreshold,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Item.toEntity() = ItemEntity(
    id = id,
    profileId = profileId,
    type = type,
    name = name,
    dosageAmount = dosageAmount,
    dosageUnit = dosageUnit,
    form = form,
    instructions = instructions,
    criticality = criticality,
    graceMinutes = graceMinutes,
    reRingMinutes = reRingMinutes,
    minGapMinutes = minGapMinutes,
    timePolicy = timePolicy,
    homeZoneId = homeZoneId,
    startDate = startDate,
    endDate = endDate,
    exerciseTargetMinutes = exerciseTargetMinutes,
    maxPerDay = maxPerDay,
    unitsPerDose = unitsPerDose,
    refillThreshold = refillThreshold,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun ItemPhotoEntity.toDomain() = ItemPhoto(
    id = id,
    itemId = itemId,
    filePath = filePath,
    sortOrder = sortOrder,
    createdAt = createdAt,
    deletedAt = deletedAt
)

fun ItemPhoto.toEntity() = ItemPhotoEntity(
    id = id,
    itemId = itemId,
    filePath = filePath,
    sortOrder = sortOrder,
    createdAt = createdAt,
    deletedAt = deletedAt
)

fun ScheduleEntity.toDomain() = Schedule(
    id = id,
    itemId = itemId,
    anchorType = anchorType,
    minuteOfDay = minuteOfDay,
    meal = meal,
    relation = relation,
    offsetMinutes = offsetMinutes,
    recurrence = recurrence,
    weekdayMask = weekdayMask,
    intervalN = intervalN,
    anchorDate = anchorDate,
    onceDate = onceDate,
    dayOfMonth = dayOfMonth,
    nthWeek = nthWeek,
    intervalHours = intervalHours,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    validFrom = validFrom,
    validUntil = validUntil,
    doseAmountOverride = doseAmountOverride,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Schedule.toEntity() = ScheduleEntity(
    id = id,
    itemId = itemId,
    anchorType = anchorType,
    minuteOfDay = minuteOfDay,
    meal = meal,
    relation = relation,
    offsetMinutes = offsetMinutes,
    recurrence = recurrence,
    weekdayMask = weekdayMask,
    intervalN = intervalN,
    anchorDate = anchorDate,
    onceDate = onceDate,
    dayOfMonth = dayOfMonth,
    nthWeek = nthWeek,
    intervalHours = intervalHours,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    validFrom = validFrom,
    validUntil = validUntil,
    doseAmountOverride = doseAmountOverride,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun OccurrenceEntity.toDomain() = Occurrence(
    id = id,
    itemId = itemId,
    scheduleId = scheduleId,
    profileId = profileId,
    plannedAt = plannedAt,
    dueAt = dueAt,
    remindAt = remindAt,
    status = status,
    ringCount = ringCount,
    actedAt = actedAt,
    skipReason = skipReason,
    note = note,
    deviceId = deviceId,
    updatedAt = updatedAt
)

fun Occurrence.toEntity() = OccurrenceEntity(
    id = id,
    itemId = itemId,
    scheduleId = scheduleId,
    profileId = profileId,
    plannedAt = plannedAt,
    dueAt = dueAt,
    remindAt = remindAt,
    status = status,
    ringCount = ringCount,
    actedAt = actedAt,
    skipReason = skipReason,
    note = note,
    deviceId = deviceId,
    updatedAt = updatedAt
)

fun MealSessionEntity.toDomain() = MealSession(
    id = id,
    profileId = profileId,
    meal = meal,
    date = date,
    startedAt = startedAt,
    endedAt = endedAt
)

fun MealSession.toEntity() = MealSessionEntity(
    id = id,
    profileId = profileId,
    meal = meal,
    date = date,
    startedAt = startedAt,
    endedAt = endedAt
)

fun ChangeLogEntity.toDomain() = ChangeLog(
    id = id,
    entityType = entityType,
    entityId = entityId,
    field = field,
    oldValue = oldValue,
    newValue = newValue,
    changedAt = changedAt,
    deviceId = deviceId
)

fun ChangeLog.toEntity() = ChangeLogEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    field = field,
    oldValue = oldValue,
    newValue = newValue,
    changedAt = changedAt,
    deviceId = deviceId
)

fun StockEventEntity.toDomain() = StockEvent(
    id = id,
    itemId = itemId,
    delta = delta,
    reason = reason,
    occurrenceId = occurrenceId,
    createdAt = createdAt
)

fun StockEvent.toEntity() = StockEventEntity(
    id = id,
    itemId = itemId,
    delta = delta,
    reason = reason,
    occurrenceId = occurrenceId,
    createdAt = createdAt
)


