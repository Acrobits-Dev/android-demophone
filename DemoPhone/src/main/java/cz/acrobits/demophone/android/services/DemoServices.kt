package cz.acrobits.demophone.android.services

import android.app.Application
import cz.acrobits.ali.Xml
import cz.acrobits.demophone.android.BuildConfig
import cz.acrobits.libsoftphone.Instance
import cz.acrobits.libsoftphone.SDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DemoServices private constructor(
    // No need to use Activity context, everything can be application-scoped
    private val application: Application
)
{
    companion object
    {
        lateinit var instance: DemoServices

        /**
         * Initialize the Demo SDK singleton.
         *
         * @param application Application instance.
         * @return DemoSdk singleton instance.
         */
        fun initialize(application: Application)
        {
            if (::instance.isInitialized)
                throw IllegalStateException("DemoServices is already initialized")

            instance = DemoServices(application)
        }
    }

    // LibSoftphone is mostly main-thread oriented, so we use Main dispatcher here
    val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val preferences: DemoPreferences
    val listeners: DemoListeners
    val permissions: DemoPermissionManager
    val eventHandler: DemoEventHandler
    val pushHandler: DemoPushHandler
    val accountManager: DemoAccountManager
    val notificationHandler: DemoNotificationHandler
    val stateManager: DemoStateManager

    init
    {
        instance = this

        // This is how you can verify the validity of JWT license keys
        // before initializing the SDK
        assert(!Instance.isValidJwtLicense(application, "my-jwt-key"))

        // Load Softphone SDK library
        // This must be done before using any SDK functionality
        // This function may throw UnsatisfiedLinkError if the library cannot be loaded
        // In such cases make sure you haven't misconfigured abiFilters in your build.gradle file
        Instance.loadLibrary(application)

        // Ensure that required features are enabled in the SDK
        assert(SDK.features.contains(SDK.Feature.Provisioning))

        // Prepare the Provisioning XML
        // In DemoPhone, the base version is bundled in the assets
        // and then we add the SaaS license identifier to it
        val provisioningXml = Xml.parse(application.assets.open("provisioning.xml"))!!.apply {
            // You can set the SaaS identifier however you want;
            // here we use the one defined in gradle.properties
            setChild(Xml("saas").apply {
                setChildValue("identifier", BuildConfig.SAAS_IDENTIFIER);
            })
        }

        Instance.init(application, provisioningXml) {
            DemoPreferences(application)
        }
        preferences = Instance.preferences as DemoPreferences
        permissions = DemoPermissionManager()

        listeners = DemoListeners(application, permissions)
        Instance.setObserver(listeners)

        eventHandler = DemoEventHandler(application, listeners)
        accountManager = DemoAccountManager(application)
        pushHandler = DemoPushHandler(coroutineScope, application)
        notificationHandler = DemoNotificationHandler(application, listeners)
        stateManager = DemoStateManager(coroutineScope)

        // Initial setup done - apply registration state and let the SDK roll
        Instance.Registration.updateAll()
    }
}