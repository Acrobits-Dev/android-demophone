package cz.acrobits.demophone.android.services

import android.content.Context
import cz.acrobits.ali.Log
import cz.acrobits.libsoftphone.event.CallEvent
import cz.acrobits.libsoftphone.permission.PermissionCallback
import cz.acrobits.libsoftphone.support.Listeners
import java.io.IOException

class DemoListeners(
    private val context: Context,
    private val demoPermissionManager: DemoPermissionManager
) : Listeners()
{
    companion object
    {
        private val LOG = Log("DemoListeners")
    }


    override fun getRingtone(p0: CallEvent): kotlin.Any?
    {
        try
        {
            return context.resources.assets.openFd("relax_ringtone.mp3")
        }
        catch (e: IOException)
        {
            LOG.error("Failed to open ringtone: %s", e);
        }

        return null;
    }

    override fun onPermissionRequested(
        permission: String,
        callback: PermissionCallback
    ) {
        demoPermissionManager.requestPermission(permission, callback)
    }
}
