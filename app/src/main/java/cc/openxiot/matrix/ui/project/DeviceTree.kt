package cc.openxiot.matrix.ui.project

import cc.openxiot.matrix.data.api.DeviceEntity

/**
 * 「子设备」的口径。
 *
 * 设备之间的从属关系在数据里只有 [DeviceEntity.parentId] 一个字段：后端不下发 children，
 * 也没有「查子设备」的专用接口，所以子设备一律拿**整张扁平设备表**按 parentId 过滤出来
 * —— 与 webapp-matrix 完全同源（`device.component.ts` 里建 byParent、`device.children
 * .component.ts` 里的 filter）。
 *
 * 项目页和设备页共用这一份规则，免得两边对「有没有孩子」的判断跑偏。
 */

/** parentId → 它的直接子设备。parentId 指向自己的（根设备）不收，避免自环。 */
internal fun buildDeviceChildren(devices: List<DeviceEntity>): Map<String, List<DeviceEntity>> {
    val map = mutableMapOf<String, MutableList<DeviceEntity>>()
    devices.forEach { device ->
        val did = device.did ?: return@forEach
        val parentId = device.parentId
        if (!parentId.isNullOrEmpty() && parentId != did) {
            map.getOrPut(parentId) { mutableListOf() }.add(device)
        }
    }
    return map
}

/** 全部 did，用来判断「父设备在不在本表里」 */
internal fun deviceDids(devices: List<DeviceEntity>): Set<String> =
    devices.mapNotNull { it.did }.toSet()

/**
 * 设备在这张表里算不算顶层：没有父设备 / 父设备是自己 / 父设备不在本表里。
 *
 * 最后一种是**断链兜底** —— 父设备不在当前项目里（被删了、或挪去了别的空间）时，
 * 把子设备当顶层铺出来，否则它会被藏在一个永远打不开的父节点下面，整个从界面上消失。
 */
internal fun isDeviceTreeRoot(device: DeviceEntity, allDids: Set<String>): Boolean {
    val parentId = device.parentId
    return parentId.isNullOrEmpty() || parentId == device.did || !allDids.contains(parentId)
}

/** 按 did 分组的服务（两个页面都要按 did 找服务，省得每次线性 filter） */
internal fun <T> groupServicesByDid(services: List<T>, didOf: (T) -> String?): Map<String, List<T>> {
    val map = mutableMapOf<String, MutableList<T>>()
    services.forEach { service ->
        val did = didOf(service) ?: return@forEach
        map.getOrPut(did) { mutableListOf() }.add(service)
    }
    return map
}
