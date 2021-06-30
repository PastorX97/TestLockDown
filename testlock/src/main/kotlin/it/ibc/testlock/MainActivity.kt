package it.ibc.testlock

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.ProfileManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PROFILE_NAME = "TestLock"
    }

    private val setLockAfterInit = AtomicBoolean(false)
    private val lockAfterInitValue = AtomicBoolean(false)

    private var profileManager: ProfileManager? = null
    private lateinit var lockButton: Button
    private lateinit var unlockButton: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lockButton = findViewById(R.id.lockButton)
        unlockButton = findViewById(R.id.unlockButton)
        lockButton.setOnClickListener {
            lockButton.visibility = View.INVISIBLE
            unlockButton.visibility = View.VISIBLE
            lockDevice()
        }
        unlockButton.setOnClickListener {
            lockButton.visibility = View.VISIBLE
            unlockButton.visibility = View.INVISIBLE
            unlockDevice()
        }

        Timber.i("initializing Zebra PS20 device locker")

        val result = EMDKManager.getEMDKManager(this, object : EMDKManager.EMDKListener {
            override fun onOpened(emdkManager: EMDKManager) {
                Timber.d("successfully opened EMDKManager")
                val profileManager = emdkManager
                    .getInstance(EMDKManager.FEATURE_TYPE.PROFILE) as ProfileManager
                this@MainActivity.profileManager = profileManager

                if (setLockAfterInit.getAndSet(false)) {
                    profileManager.setDeviceLocked(lockAfterInitValue.get())
                }
            }

            override fun onClosed() {
                Timber.d("EMDKManager has ben disposed")
                profileManager = null
                setLockAfterInit.getAndSet(false)
            }
        })

        if (result.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            throw Exception("failed to open EMDKManager ${result.extendedStatusMessage}")
        }
    }


    private fun lockDevice() {
        setOrPrepareDeviceLock(true)
    }

    private fun unlockDevice() {
        setOrPrepareDeviceLock(false)
    }

    /**
     * Checks if [ProfileManager] is initialized and:
     *
     * - Sets lock if a valid instance exists
     * - Enqueues the operation after the initialization
     */
    private fun setOrPrepareDeviceLock(locked: Boolean) {
        val profileManager = profileManager

        if (profileManager == null) {
            setLockAfterInit.set(true)
            lockAfterInitValue.set(locked)
        } else {
            profileManager.setDeviceLocked(locked)
        }
    }

    /**
     * Locks the device using a dedicated profile.
     */
    private fun ProfileManager.setDeviceLocked(locked: Boolean) {
        val codexml = arrayOf(
            """<?xml version="1.0" encoding="utf-8"?>
<wap-provisioningdoc>
    <characteristic type="Profile">
        <parm name="ProfileName" value="$PROFILE_NAME"/>
        <characteristic type="UiMgr" version="9.1" >
            <parm name="emdk_name" value="blk"/>
            <parm name="NavigationBarUsage" value="${if (locked) "2" else "1"}"/>
            <parm name="NotificationPullDown" value="${if (locked) "2" else "1"}"/>
            <parm name="OnScreenPowerButton" value="${if (locked) "2" else "1"}"/>
        </characteristic>
    </characteristic>
</wap-provisioningdoc>""".trimIndent()
        )
        Timber.d("setDeviceLocked before sending xml config")
        val result = processProfile(
            "${PROFILE_NAME}/UiMgr/blk",
            ProfileManager.PROFILE_FLAG.SET,
            codexml
        )
        Timber.d("setDeviceLocked result: ${result.statusString}")
    }

}