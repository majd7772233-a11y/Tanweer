package com.magd.tanweer.data.repository

import com.magd.tanweer.data.local.GroupEntity
import com.magd.tanweer.data.model.GroupItem

/**
 * Room-to-UI mapping must preserve access-control metadata. Dropping visibility
 * here silently converted private discoverable groups into PUBLIC defaults and
 * bypassed the join-reason/approval flow on the discovery screen.
 */
internal fun GroupEntity.toGroupItem(): GroupItem = GroupItem(
    id = id,
    name = name,
    type = type,
    description = description,
    icon = icon,
    role = role,
    memberCount = memberCount,
    visibility = visibility,
    chatPolicy = chatPolicy
)
