package cz.acrobits.demophone.android.services

import cz.acrobits.libsoftphone.permission.Permission
import cz.acrobits.libsoftphone.permission.PermissionCallback
import cz.acrobits.libsoftphone.permission.PermissionRequestLauncherOwner

/**
 * A simple permission manager that queues permission requests until a PermissionRequestLauncherOwner is available.
 */
class DemoPermissionManager {
    data class PermissionRequest(
        val permission: String,
        val callback: PermissionCallback
    )

    private val _pendingPermissionRequests = mutableListOf<PermissionRequest>()
    private var _owner: PermissionRequestLauncherOwner? = null

    fun requestPermission(
        permission: String,
        callback: PermissionCallback
    ) {
        // If the owner is available, request the permission immediately. Otherwise, queue the request.
        val owner = _owner

        if (owner != null) {
            Permission.request(
                owner.permissionRequestLauncher,
                permission,
            ) {
                callback.onResult(it)
            }
        } else {
            _pendingPermissionRequests.add(PermissionRequest(permission, callback))
        }
    }

    fun notifyOwnerAvailable(owner: PermissionRequestLauncherOwner) {
        _owner = owner

        val pendingPermissionRequests = _pendingPermissionRequests.toMutableList()
        _pendingPermissionRequests.clear()

        for (request in pendingPermissionRequests) {
            Permission.request(
                owner.permissionRequestLauncher,
                request.permission,
            ) {
                request.callback.onResult(it)
            }
        }
    }

    fun notifyOwnerUnavailable(owner: PermissionRequestLauncherOwner) {
        if (_owner === owner) {
            _owner = null
        }
    }
}
