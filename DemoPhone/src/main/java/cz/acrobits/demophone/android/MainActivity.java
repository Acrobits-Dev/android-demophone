package cz.acrobits.demophone.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.google.firebase.FirebaseApp;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import cz.acrobits.ali.Log;
import cz.acrobits.commons.util.CollectionUtil;
import cz.acrobits.demophone.android.services.DemoServices;
import cz.acrobits.libsoftphone.Instance;
import cz.acrobits.libsoftphone.SDK;
import cz.acrobits.libsoftphone.VideoInstance;
import cz.acrobits.libsoftphone.contacts.ContactSourceType;
import cz.acrobits.libsoftphone.data.AssetRequest;
import cz.acrobits.libsoftphone.data.Call;
import cz.acrobits.libsoftphone.data.CameraInfo;
import cz.acrobits.libsoftphone.data.ColorRequest;
import cz.acrobits.libsoftphone.data.DialAction;
import cz.acrobits.libsoftphone.data.RegistrationState;
import cz.acrobits.libsoftphone.event.CallEvent;
import cz.acrobits.libsoftphone.event.StreamParty;
import cz.acrobits.libsoftphone.glide.GlideImageAssetLoaderExtKt;
import cz.acrobits.libsoftphone.internal.util.TextChangedListener;
import cz.acrobits.libsoftphone.permission.Permission;
import cz.acrobits.libsoftphone.permission.PermissionRequestLauncher;
import cz.acrobits.libsoftphone.permission.PermissionRequestLauncherOwner;
import cz.acrobits.libsoftphone.support.Listeners;
import cz.acrobits.libsoftphone.telecom.AudioEndpoint;
import cz.acrobits.libsoftphone.telecom.CallType;

/**
 * Main activity for DemoPhone.
 * <p>
 * This activity serves as an example how to use the Acrobits libSoftphone for Android.
 */
//******************************************************************
public class MainActivity
        extends Activity
        implements Listeners.OnAudioEndpointChanged,
                   Listeners.OnCallHoldStateChanged,
                   Listeners.OnCallStateChanged,
                   Listeners.OnNewCall,
                   Listeners.OnRegistrationErrorMessage,
                   Listeners.OnRegistrationStateChanged,
                   PermissionRequestLauncherOwner,
                   Runnable,
                   View.OnClickListener,
                   View.OnTouchListener
//******************************************************************
{
    /** Logging. */
    private static final Log LOG = new Log("DemoPhone");

    public static final String EXTRA_EVENT_ID = "eventId";
    public static final String EXTRA_ANSWER_CALL = "answerCall";

    public static final String TRANSIENT_FROM_GUI = "from_gui";

    // Contains the permission request launcher for this activity.
    // This is how you delegate a permission request to the SDK, without having to provide an instance of your activity to the SDK.
    private final PermissionRequestLauncher mPermissionRequestLauncher = PermissionRequestLauncher.of(this);

    /** Generated phone number. */
    private String mNumber;

    /** Used account ID. <code>null</code> for single-account builds. */
    private String mAccountId;

    /** Timer. */
    private final Timer mTimer = new Timer();

    /** Task executed in the timer. */
    private TimerTask mTask;

    /** View showing {@link #mNumber the number used for registration}. */
    private EditText mNumberView;

    /** View showing the registration state. */
    private TextView mRegistrationStateView;

    /** View showing the call state. */
    private TextView mCallStateView;

    /** View showing calling/caller contact. */
    private TextView mContactView;

    /** View showing current audio route. */
    private TextView mRouteView;

    /** View showing packet loss statistics. */
    private TextView mPacketLossView;

    /** View showing jitter statistics. */
    private TextView mJitterView;

    /** Button to delete last digit in the number field. */
    private Button mBackspaceButton;

    /** Button to clear the number field. */
    private Button mClearButton;

    /** Button to dial. */
    private Button mCallButton;

    /** Button to mute the call. */
    private Button mMuteButton;

    /** Button to switch the audio route. */
    private Button mSelectAudioEndpointButton;

    /** Button to switch camera. */
    private Button mCameraButton;

    /** Button to switch logging into LogCat. */
    private Button mLogButton;

    /** Button to put the call on hold. */
    private Button mHoldButton;

    /** Button to hang up. */
    private Button mHangUpButton;

    /** Button to answer a ringing call. */
    private Button mAnswerButton;

    /** Button to reject a ringing call. */
    private Button mRejectButton;

    /** Button to test PNs. */
    private Button mPushTestButton;

    /** Button to exit app. */
    private Button mExitButton;

    /** Prepared notification builder. */
    private Notification.Builder mNotification;

    /** Intent used by the prepared notification. */
    private PendingIntent mNotificationIntent;

    /** Current call. */
    private CallEvent mCall;

    @NonNull
    @Override
    public PermissionRequestLauncher getPermissionRequestLauncher() {
        return mPermissionRequestLauncher;
    }

    /**
     * Timer task updating statistics.
     */
    //******************************************************************
    private final class Task
            extends TimerTask
    //******************************************************************
    {
        //******************************************************************
        @Override
        public void run()
        //******************************************************************
        {
            runOnUiThread(MainActivity.this);
        }
    }

    /**
     * Activity creation handler.
     * <p>
     * Initializes the SDK and generates and registers an account.
     *
     * @param savedInstanceState Saved instance state.
     */
    //******************************************************************
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    //******************************************************************
    {
        super.onCreate(savedInstanceState);

        DemoServices.instance.getListeners().register(this);

        setContentView(R.layout.main);

        mNumberView = findViewById(R.id.number);
        mRegistrationStateView = findViewById(R.id.registration_state);
        mCallStateView = findViewById(R.id.call_state);
        mContactView = findViewById(R.id.contact);
        mRouteView = findViewById(R.id.route);
        mPacketLossView = findViewById(R.id.packet_loss);
        mJitterView = findViewById(R.id.jitter);
        mBackspaceButton = findViewById(R.id.backspace);
        mClearButton = findViewById(R.id.clear);
        mCallButton = findViewById(R.id.call);
        mMuteButton = findViewById(R.id.mute);
        mSelectAudioEndpointButton = findViewById(R.id.select_audio_endpoint);
        mCameraButton = findViewById(R.id.camera);
        mLogButton = findViewById(R.id.log);
        mHoldButton = findViewById(R.id.hold);
        mHangUpButton = findViewById(R.id.hangup);
        mAnswerButton = findViewById(R.id.answer);
        mRejectButton = findViewById(R.id.reject);
        mPushTestButton = findViewById(R.id.push_test);
        mExitButton = findViewById(R.id.exit_app);
        if (!CollectionUtil.contains(SDK.features, SDK.Feature.Video))
            mCameraButton.setVisibility(View.INVISIBLE);

        findViewById(R.id.log).setOnClickListener(this);
        findViewById(R.id.reset).setOnClickListener(this);
        mBackspaceButton.setOnClickListener(this);
        mClearButton.setOnClickListener(this);
        mCallButton.setOnClickListener(this);
        mHangUpButton.setOnClickListener(this);
        mMuteButton.setOnClickListener(this);
        mSelectAudioEndpointButton.setOnClickListener(this);
        mLogButton.setOnClickListener(this);
        mHoldButton.setOnClickListener(this);
        mPushTestButton.setOnClickListener(this);
        mExitButton.setOnClickListener(this);
        findViewById(R.id.answer).setOnClickListener(this);
        findViewById(R.id.reject).setOnClickListener(this);

        mNumberView.addTextChangedListener((TextChangedListener) text -> {
            final boolean empty = text.length() == 0;
            mBackspaceButton.setEnabled(!empty);
            mClearButton.setEnabled(!empty);
            mCallButton.setEnabled(!empty && mCall == null);
        });

        setAccordingToCall(false, false);
        updateWindowFlags(false, false);
        mBackspaceButton.setEnabled(false);
        mClearButton.setEnabled(false);
        mLogButton.setText(Instance.preferences.trafficLogging.get()
                                   ? R.string.log_off
                                   : R.string.log_on);

        LinearLayout dtmf = findViewById(R.id.dtmf);
        for (int i = 0; i < dtmf.getChildCount(); ++i)
        {
            LinearLayout line = (LinearLayout) dtmf.getChildAt(i);
            for (int j = 0; j < line.getChildCount(); ++j)
            {
                Button b = (Button) line.getChildAt(j);
                b.setOnTouchListener(this);
                b.setText((String) b.getTag());
            }
        }

        mNumber = DemoServices.instance.getAccountManager().getCurrentNumber().getValue();
        ((TextView) findViewById(R.id.registration_number)).setText(mNumber);

        LOG.info("Sources: %s", (Object) Instance.Contacts.getSources());

        // Start address book synchronization
        /* If the app does not have contacts permission, the SDK won't automatically start address
         * book synchronization. When that happens, you need to explicitly start the
         * synchronization which will also ask for the permission. This is so that you can select
         * when the user will be asked for the permission (e.g. only after accepting EULA or
         * requesting to populate contacts view). Once the permission is granted, the SDK will
         * watch for changes in the address book and resync automatically so you don't need to.
         * For SaaS: you need to enable address book contact source in provisioning, see
         *           assets/provisioning.xml. */
        if (CollectionUtil.contains(Instance.Contacts.getSources(), ContactSourceType.AddressBook)
            && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            Instance.Contacts.ensureValidState(ContactSourceType.AddressBook);

        // Prepare a notification icon
        mNotificationIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);

        mNotification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.icon_notification)
                .setOngoing(true)
                .setContentTitle(getResources().getText(R.string.app_name))
                .setContentIntent(mNotificationIntent);
        if (Build.VERSION.SDK_INT >= 26)
            mNotification.setChannelId(Instance.Notifications.CHANNEL_ID);
        
        // Request notification permission on Android 13
        NotificationManager nm = Objects.requireNonNull(ContextCompat.getSystemService(this, NotificationManager.class));
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled())
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);

        mPushTestButton.setVisibility(FirebaseApp
                .getApps(this).size() == 0 ? View.GONE : View.VISIBLE);

        mCall = CallUtil.getCallById(getIntent().getLongExtra(EXTRA_EVENT_ID, -1));

        ViewGroup callContainer = findViewById(R.id.call_screen_container);
        ViewCompat.setOnApplyWindowInsetsListener(callContainer, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Synthetic AssetRequest, In normal usage you will get them from the SDK such as Instance.Contacts.getAvatarLarge
        AssetRequest.UrlAssetRequest light = new AssetRequest.UrlAssetRequest(
                "https://static.acrobits.net/acrobits-logo-bw-1024.png",
                new AssetRequest.Common(
                        AssetRequest.Fit.Contain,
                        null,
                        null,
                        ColorRequest.HexColorRequest.Companion.hex(Color.valueOf(Color.parseColor("#FFFFFF")))
                ));
        AssetRequest assetRequest = new AssetRequest(light, light);

        ImageView assetRequestExampleImageView = findViewById(R.id.asset_request_example);
        GlideImageAssetLoaderExtKt
                .asImageAsset(Glide.with(assetRequestExampleImageView))
                .load(assetRequest)
                .into(assetRequestExampleImageView);

        /* I Kotlin this would be:
            Glide
                .with(imageView)
                .asImageAsset()
                .load(assetRequest)
                .into(imageView)
         */
    }

    //******************************************************************
    @Override
    protected void onNewIntent(Intent intent)
    //******************************************************************
    {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_ANSWER_CALL, false))
            answerIncomingCall();
    }

    /**
     * Activity start.
     */
    //******************************************************************
    @Override
    protected void onStart()
    //******************************************************************
    {
        super.onStart();
        if (mCall != null)
            onCallStateChanged(mCall, Instance.Calls.getState(mCall));
    }

    /**
     * Activity destruction handler.
     * <p>
     * Closes active call, then unregisters and kills the application.
     */
    //******************************************************************
    @Override
    protected void onDestroy()
    //******************************************************************
    {
        DemoServices.instance.getListeners().unregister(this);

        super.onDestroy();
    }

    /**
     * Handle a click on a button.
     *
     * @param v The button that was clicked on.
     */
    //******************************************************************
    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(@NonNull View v)
    //******************************************************************
    {
        switch (v.getId())
        {
            case R.id.camera:
                for (CameraInfo info : VideoInstance.enumerateCameras())
                {
                    if (!info.equals(VideoInstance.getCurrentCamera()))
                    {
                        VideoInstance.switchCamera(info.id);
                        break;
                    }
                }
                break;
            case R.id.log:
                boolean enabled = !Instance.preferences.trafficLogging.get();
                Instance.preferences.trafficLogging.set(enabled);
                mLogButton.setText(enabled
                                           ? R.string.log_off
                                           : R.string.log_on);
                break;
            case R.id.reset:
                resetSDK();
                break;
            case R.id.backspace:
                mNumberView
                        .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                break;
            case R.id.clear:
                mNumberView.setText(null);
                break;
            case R.id.call:
                openCallDialog(mNumberView.getText().toString());
                break;
            case R.id.hangup:
                hangup("User interaction");
                break;
            case R.id.mute:
                toggleMute();
                break;
            case R.id.select_audio_endpoint:
                selectAudioEndpointPressed();
                break;
            case R.id.hold:
                toggleHold();
                break;
            case R.id.answer:
                answerIncomingCall();
                break;
            case R.id.reject:
                rejectIncomingCall();
                break;
            case R.id.push_test:
                schedulePushTest();
                break;
            case R.id.exit_app:
                exitApp();
                break;
        }
    }

    /**
     * Handle a touch on the dialpad.
     *
     * @param v     The view that was touched.
     * @param event The associated event,
     * @return Whether the touch was processed.
     */
    //******************************************************************
    @Override
    public boolean onTouch(@NonNull View v,
                           @NonNull MotionEvent event)
    //******************************************************************
    {
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                final Object tag = v.getTag();
                if (tag == null || !(tag instanceof String))
                    return false;
                final byte[] bytes = ((String) tag).getBytes();
                if (bytes.length != 1)
                    return false;
                Instance.Audio.dtmfOn(bytes[0]);
                if (mCall == null || Instance.Calls.getState(mCall) != Call.State.Established)
                    mNumberView.append(new String(bytes, 0, 1));
                return true;
            case MotionEvent.ACTION_UP:
                v.performClick();
                dtmfOff();
                return true;
            default:
                return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    //******************************************************************
    @Override
    public void onRegistrationStateChanged(@Nullable String accountId,
                                           @NonNull RegistrationState state)
    //******************************************************************
    {
        mAccountId = accountId;
        mRegistrationStateView.setText(state.getLabel());

        if (mNotification == null)
            return;
        mNotification.setContentText(String.format("%s: %s", mNumber, state.getLabel()));
        Instance.Notifications.update(mNotification.build());
    }

    /**
     * {@inheritDoc}
     */
    //******************************************************************
    @Override
    public void onNewCall(@NonNull CallEvent call)
    //******************************************************************
    {
        if (mCall != null)
        {
            // Only one call at a time
            Instance.Calls.close(call);
            return;
        }

        mCall = call;
        try
        {
            // Bring the application to front
            mNotificationIntent.send();
        }
        catch (PendingIntent.CanceledException e)
        {}
    }

    /**
     * {@inheritDoc}
     */
    //******************************************************************
    @Override
    public void onAudioEndpointChanged(@Nullable AudioEndpoint endpoint)
    //******************************************************************
    {
        mRouteView.setText(endpoint == null ? "" : getEndpointName(endpoint));
    }

    /**
     * {@inheritDoc}
     */
    //******************************************************************
    @Override
    public void onCallStateChanged(@NonNull CallEvent call,
                                   @NonNull Call.State state)
    // ******************************************************************
    {
        if (!call.equals(mCall))
            return;

        mCallStateView.setText(state.getLabel());
        mContactView.setText(call.getRemoteUser().getDisplayName());

        if (state.isTerminal())
        {
            setAccordingToCall(false, false);
            updateWindowFlags(false, false);
            mHoldButton.setText(R.string.hold);
            Instance.Calls.close(mCall);
            mCall = null;
            return;
        }

        switch (state)
        {
            case IncomingRinging:
                // Ring ring
                setAccordingToCall(false, true);
                updateWindowFlags(true, true);
                break;
            case Trying:
            case Ringing:
            case Established:
                boolean hasVideo = call.hasAttribute(CallEvent.Attributes.INCOMING_VIDEO) || call.hasAttribute(CallEvent.Attributes.OUTGOING_VIDEO);
                setAccordingToCall(true, false);
                updateWindowFlags(true, hasVideo);
                statistics();
                break;
            default:
                break;
        }
    }

    /**
     * Check if a call is held.
     *
     * @param call Call to check.
     * @return Whether the call is held.
     */
    // ******************************************************************
    public boolean isCallHeld(@NonNull CallEvent call)
    // ******************************************************************
    {
        final Call.HoldStates states = Instance.Calls.isHeld(call);
        return states.local == Call.HoldState.Held || states.remote == Call.HoldState.Held;
    }

    /**
     * {@inheritDoc}
     */
    // ******************************************************************
    @Override
    public void onCallHoldStateChanged(@NonNull CallEvent call,
                                       @NonNull Call.HoldStates states)
    // ******************************************************************
    {
        if (!call.equals(mCall))
            return;

        if (isCallHeld(mCall))
            mCallStateView.setText(R.string.on_hold);
        else
            onCallStateChanged(mCall, Instance.Calls.getState(mCall));
    }

    /**
     * Update statistics during a call.
     */
    // ******************************************************************
    @Override
    public void run()
    // ******************************************************************
    {
        if (mCall == null || Instance.Calls.getState(mCall).isTerminal())
        {
            if (mTask != null)
            {
                mTask.cancel();
                mTask = null;
            }
            mPacketLossView.setText(R.string.na);
            mJitterView.setText(R.string.na);
            return;
        }

        if (isCallHeld(mCall))
        {
            mPacketLossView.setText(R.string.na);
            mJitterView.setText(R.string.na);
            return;
        }

        /**
         * Jitter is in timestamp units, which are defined by the clockRateInHertz member
         * (1 TU = 1.0 / clockRateInHertz seconds), therefore to convert it to *milli*seconds,
         * we have to multiply the jitter value by a factor of (1000.0 / clockRateInHertz). */
        final Call.Statistics stats = Instance.Calls.getStatistics(mCall);
        mPacketLossView.setText(String.format(Locale.ROOT,
                                              "%d%%",
                                              stats.jitterBufferPacketLossPercentage));
        if (stats.clockRateInHertz > 0)
            mJitterView.setText(String.format(Locale.ROOT,
                                              "%.2g ms",
                                              stats.maxJitter * (1000.0 / stats.clockRateInHertz)));
    }

    /**
     * Schedule statistics update.
     */
    // ******************************************************************
    private void statistics()
    // ******************************************************************
    {
        if (mTask != null)
            return;
        mTask = new Task();
        mTimer.schedule(mTask, 0, 500);
    }

    /**
     * Set views enabled or disabled according to whether there is a call in progress.
     *
     * @param inCall  Are we in a call?
     * @param ringing Is there a call ringing?
     */
    // ******************************************************************
    private void setAccordingToCall(boolean inCall,
                                    boolean ringing)
    // ******************************************************************
    {
        mCallButton.setEnabled(!inCall && !ringing && mNumberView.getText().length() > 0);
        mSelectAudioEndpointButton.setEnabled(inCall);
        mMuteButton.setEnabled(inCall);
        mCameraButton.setEnabled(inCall && Instance.Calls.getDesiredMedia(mCall).outgoingVideoEnabled);
        mHoldButton.setEnabled(inCall);
        mHangUpButton.setEnabled(inCall);
        mAnswerButton.setEnabled(ringing);
        mRejectButton.setEnabled(ringing);
    }

    /**
     * Updates the window flags, based usually on call state.
     *
     * @param wakeup Whether the device should turn its screen on. Works on locked screen too.
     * @param keepScreenOn Whether to keep the screen on, or allow it to dim.
     */
    // ******************************************************************
    private void updateWindowFlags(boolean wakeup, boolean keepScreenOn)
    // ******************************************************************
    {
        // More information on window flags can be found in Android documentation at:
        //  - "WindowManager.LayoutParams": https://developer.android.com/reference/android/view/WindowManager.LayoutParams
        //  - "Keep the device awake": https://developer.android.com/training/scheduling/wakelock

        Window window = getWindow();

        // Note: those flags can also be applied in AndroidManifest.xml to activities
        //
        // Warning: If you wish to display your call activity while the device is locked, before
        //          the keyguard is dismissed you have to use the old deprecated window flags.
        int wakeupFlags = 0;
        if (wakeup)
        {
            wakeupFlags = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            if (ContextCompat.getSystemService(this, KeyguardManager.class).isKeyguardLocked())
                wakeupFlags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        }

        window.setFlags(wakeup ? wakeupFlags : 0, wakeupFlags);

        // Note: this flag can also be applied in layout XML: `android:keepScreenOn="true"`
        int keepScreenOnFlags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        window.setFlags(keepScreenOn ? keepScreenOnFlags : 0, keepScreenOnFlags);

    }

    // ******************************************************************
    private void openCallDialog(@NonNull String number)
    // ******************************************************************
    {
        String[] names = new String[2];
        CallType[] callTypes = new CallType[2];

        names[0] = getString(R.string.audio_call);
        callTypes[0] = CallType.Audio;

        names[1] = getString(R.string.video_call);
        callTypes[1] = CallType.Video;

        new AlertDialog.Builder(this)
                .setItems(names, (dialog, which) -> callNumber(number, callTypes[which]))
                .show();
    }

    /**
     * Call a number.
     *
     * @param number The number to call.
     */
    // ******************************************************************
    private void callNumber(@NonNull String number, @NonNull CallType callType)
    // ******************************************************************
    {
        if (TextUtils.isEmpty(number))
            return;

        if (mCall != null)
            Instance.Calls.close(mCall);

        mCall = new CallEvent(new StreamParty(number).match(mAccountId).toRemoteUser());
        mCall.transients.put(CallEvent.Transients.DIAL_ACTION, getDialActionTransient(callType));
        int res = Instance.Events.post(mCall);
        if (res != Instance.Events.PostResult.SUCCESS)
        {
            Toast.makeText(this, String.format(Locale.ROOT, "Call failed: %d", res), Toast.LENGTH_SHORT).show();
            mCall = null;
            return;
        }
        mContactView.setText(number);
        statistics();
    }

    /**
     * Pick which call action to use for a call, based on the call type and whether video is supported.
     * @param callType The desired call type (audio or video).
     * @return The dial action to use for the call.
     */
    // ******************************************************************
    private String getDialActionTransient(@NonNull CallType callType)
    // ******************************************************************
    {
        if (!CollectionUtil.contains(SDK.features, SDK.Feature.Video))
            return DialAction.VOICE_CALL.id;

        return switch (callType)
        {
            case Audio -> DialAction.VOICE_CALL.id;
            case Video -> DialAction.VIDEO_CALL.id;
        };
    }
        

    /**
     * Hang up.
     */
    // ******************************************************************
    private void hangup(String reason)
    // ******************************************************************
    {
        if (mCall == null)
            return;
        Instance.Calls.hangup(mCall, reason);
    }

    /**
     * Toggle mute.
     */
    // ******************************************************************
    private void toggleMute()
    // ******************************************************************
    {
        final boolean mute = Instance.Audio.isMuted();
        Instance.Audio.setMuted(!mute);
        mMuteButton.setText(mute ? R.string.mute : R.string.unmute);
    }

    /**
     * Switch the audio route.
     */
    // ******************************************************************
    private void selectAudioEndpointPressed()
    // ******************************************************************
    {
        if (mCall == null)
            return;

        List<AudioEndpoint> endpoints = Instance.Audio.getAvailableCallAudioEndpoints().getValue();
        if (endpoints.isEmpty())
            return;

        String[] names = new String[endpoints.size()];
        for (int i = 0; i < endpoints.size(); ++i)
            names[i] = getEndpointName(endpoints.get(i));

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_audio_endpoint)
                .setItems(names, (dialog, which) -> Instance.Audio.setCallAudioEndpoint(endpoints.get(which)))
                .show();
    }

    /**
     * Toggle hold.
     */
    // ******************************************************************
    private void toggleHold()
    // ******************************************************************
    {
        if (mCall == null)
            return;

        final boolean held = Instance.Calls.isHeld(mCall).local == Call.HoldState.Held;
        Instance.Calls.setHeld(mCall, !held);
        mHoldButton.setText(held ? R.string.hold : R.string.unhold);
    }

    /**
     * Answer an incoming call.
     */
    // ******************************************************************
    private void answerIncomingCall()
    // ******************************************************************
    {
        if (mCall == null || Instance.Calls.getState(mCall) != Call.State.IncomingRinging)
            return;

        mCall.transients.put(TRANSIENT_FROM_GUI, true);
        if (!Instance.Calls.answerIncoming(mCall, Call.DesiredMedia.videoBothWays()))
            Instance.Calls.close(mCall);
    }

    /**
     * Reject an incoming call.
     */
    // ******************************************************************
    private void rejectIncomingCall()
    // ******************************************************************
    {
        if (mCall == null || Instance.Calls.getState(mCall) != Call.State.IncomingRinging)
            return;

        boolean rejected = Instance.Calls.rejectIncomingEverywhere(
                mCall,
                new Call.RejectReason("Declined by user from call screen", Call.RejectReason.Type.User)
        );

        if (!rejected)
            Instance.Calls.close(mCall);
    }

    /**
     * Schedule a push test.
     */
    // ******************************************************************
    private void schedulePushTest()
    // ******************************************************************
    {
        if (Instance.Notifications.Push.scheduleTest(null, 0))
            LOG.debug("Scheduling push test");
        else
            LOG.error("Push test could not be scheduled");
    }

    /**
     * Exit from app.
     */
    // ******************************************************************
    private void exitApp()
    // ******************************************************************
    {
        if (Instance.State.isTerminating())
            return;

        // Terminate the SDK
        //noinspection resource
        Instance.State.requestTermination(this::finish);
    }

    /**
     * Stop DTMF tone playback.
     */
    // ******************************************************************
    private void dtmfOff()
    // ******************************************************************
    {
        Instance.Audio.dtmfOff();
    }

    /**
     * Example of how the SDK can be safely stopped and then (after a delay) restarted.
     */
    // ******************************************************************
    private void resetSDK()
    // ******************************************************************
    {
        DemoServices.instance.getStateManager().restartSdkSync();
    }

    /**
     * {@inheritDoc}
     */
    // ******************************************************************
    @Override
    public void onRegistrationErrorMessage(@Nullable String accountId,
                                           @NonNull String message)
    // ******************************************************************
    {
        mAccountId = accountId;
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    // ******************************************************************
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] results)
    // ******************************************************************
    {
        Permission.onRequestPermissionResult(this, requestCode, permissions, results);
    }

    // ******************************************************************
    public static Intent getMainActivityIntent(Context context, long eventId)
    // ******************************************************************
    {
        return new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(MainActivity.EXTRA_EVENT_ID, eventId);
    }

    // ******************************************************************
    public static void startMainActivity(Context context, long eventId)
    // ******************************************************************
    {
        context.startActivity(getMainActivityIntent(context, eventId));
    }

    // ******************************************************************
    public static void startMainActivityAndAnswer(Context context, long eventId)
    // ******************************************************************
    {
        Intent intent = getMainActivityIntent(context, eventId)
                .putExtra(EXTRA_ANSWER_CALL, true);
        context.startActivity(intent);
    }

    // ******************************************************************
    @Override
    protected void onResume()
    // ******************************************************************
    {
        super.onResume();
        DemoServices.instance.getPermissions().notifyOwnerAvailable(this);
    }

    // ******************************************************************
    @Override
    protected void onStop()
    // ******************************************************************
    {
        super.onStop();
        DemoServices.instance.getPermissions().notifyOwnerUnavailable(this);
    }

    // ******************************************************************
    private String getEndpointName(@NonNull AudioEndpoint endpoint)
    // ******************************************************************
    {
        return switch (endpoint.getType()) {
            case Unknown -> getString(R.string.audio_endpoint_unknown);
            case Receiver -> getString(R.string.audio_endpoint_receiver);
            case Speaker -> getString(R.string.audio_endpoint_speaker);
            case Bluetooth ->
                    endpoint.getName() != null ? endpoint.getName() : getString(R.string.audio_endpoint_bluetooth);
            case Headset -> getString(R.string.audio_endpoint_headset);
            case Streaming -> getString(R.string.audio_endpoint_audio_stream);
        };
    }
}
