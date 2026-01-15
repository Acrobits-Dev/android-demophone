package cz.acrobits.demophone.android.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import cz.acrobits.demophone.android.MainActivity
import cz.acrobits.demophone.android.R
import cz.acrobits.libsoftphone.Instance
import cz.acrobits.libsoftphone.assets.toImageAssetDescriptor
import cz.acrobits.libsoftphone.contacts.ContactSource
import cz.acrobits.libsoftphone.data.Call
import cz.acrobits.libsoftphone.data.ResolvedPeerAddress
import cz.acrobits.libsoftphone.event.CallEvent
import cz.acrobits.libsoftphone.glide.asImageAsset
import cz.acrobits.libsoftphone.internal.contacts.ContactsUtil
import cz.acrobits.libsoftphone.support.Listeners
import cz.acrobits.libsoftphone.support.Listeners.OnCallStateChanged

class DemoNotificationHandler(private val context: Context, listeners: Listeners) : OnCallStateChanged
{
    companion object
    {
        const val ID_INCOMING_CALL: Int = 200
        private const val CHANNEL_INCOMING_CALL: String = "demophone_incomnig_call"
    }

    private val notificationManager: NotificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)!!

    init
    {
        notificationManager.cancelAll()

        if (Build.VERSION.SDK_INT >= 26)
            createNotificationChannels()

        listeners.register(this)
    }

    @RequiresApi(26)
    private fun createNotificationChannels()
    {
        val channel = NotificationChannel(
            CHANNEL_INCOMING_CALL,
            context.getString(R.string.notification_call_incoming),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(false)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun notifyIncomingRinging(callEvent: CallEvent)
    {
        val messageText = context.resources.getString(R.string.notification_call_incoming)

        val pIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_EVENT_ID, callEvent.eventId)
            },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteUser = callEvent.remoteUser!!
        val streamParty = remoteUser.toStreamParty()

        var personUri: String? = null
        if (streamParty.contactId != null && streamParty.contactId.source == ContactSource.ADDRESS_BOOK)
            personUri = ContactsUtil.getLookupUri(streamParty.contactId.id)

        val eventAddress = if (streamParty.displayName.isNullOrEmpty()) ResolvedPeerAddress(streamParty.currentOriginalTransportUri).humanReadable else streamParty.displayName

        val person = Person.Builder()
            .setName(remoteUser.displayName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.person_24px))
            .setUri(personUri)
            .setKey(remoteUser.transportUri)
            .setImportant(true)
            .build()

        val hasVideo = callEvent.hasAttribute(CallEvent.Attributes.INCOMING_VIDEO) || callEvent.hasAttribute(CallEvent.Attributes.OUTGOING_VIDEO)

        val declineIntent = createDismissAction(callEvent.eventId)
        val answerIntent = createAnswerAction(callEvent.eventId)

        val style: NotificationCompat.Style = NotificationCompat.CallStyle
            .forIncomingCall(
                person,
                declineIntent,
                answerIntent
            )
            .setIsVideo(hasVideo)

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_call_black_24dp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pIntent)
            .setTicker(messageText)
            .setContentTitle(eventAddress)
            .setContentText(messageText)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pIntent, true)
            .setStyle(style)
            .setAutoCancel(false)
            .setOngoing(true)

        notificationManager.notify(ID_INCOMING_CALL, builder.build())

        val avatarLarge = streamParty.contactId?.let { contactId ->
            Instance.Contacts.getAvatarLarge(streamParty.contactId)
        }

        if (avatarLarge != null)
        {
            // We have an avatar to load - load it asynchronously
            Glide
                .with(context)
                .asImageAsset()
                .load(avatarLarge.toImageAssetDescriptor())
                .into(object : CustomTarget<Drawable>()
                      {
                          override fun onResourceReady(res: Drawable, t: Transition<in Drawable>?)
                          {
                              val updatedPerson = person
                                  .toBuilder()
                                  .setIcon(IconCompat.createWithBitmap(res.toBitmap()))
                                  .build()

                              val updatedStyle: NotificationCompat.Style = NotificationCompat.CallStyle
                                  .forIncomingCall(updatedPerson, declineIntent, answerIntent)
                                  .setIsVideo(hasVideo)

                              builder.setStyle(updatedStyle)

                              notificationManager.notify(ID_INCOMING_CALL, builder.build())
                          }

                          override fun onLoadCleared(placeholder: Drawable?)
                          {
                              // Do nothing
                          }
                      })
        }
    }

    fun cancelIncomingCallNotification()
    {
        notificationManager.cancel(ID_INCOMING_CALL)
    }

    override fun onCallStateChanged(callEvent: CallEvent, state: Call.State)
    {
        when (state)
        {
            Call.State.IncomingRinging ->
            {
                notifyIncomingRinging(callEvent)
                return
            }

            else ->
            {
                // Do nothing
            }
        }

        cancelIncomingCallNotification()
    }

    private fun createDismissAction(callEventId: Long): PendingIntent =
        createCallNotificationPendingIntent(DemoNotificationBroadcastReceiver.ACTION_DECLINE_INCOMING_CALL, callEventId)

    private fun createAnswerAction(callEventId: Long): PendingIntent =
        createCallNotificationPendingIntent(DemoNotificationBroadcastReceiver.ACTION_ANSWER_INCOMING_CALL, callEventId)

    private fun createCallNotificationPendingIntent(action: String, callId: Long): PendingIntent
    {
        val intent = Intent(action, null, context, DemoNotificationBroadcastReceiver::class.java).apply {
            putExtras(Bundle().apply {
                putLong(MainActivity.EXTRA_EVENT_ID, callId)
            })
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
