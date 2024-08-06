package dev.godjango.sunmiPaymentLibrary

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import dev.godjango.sunmiPaymentLibrary.databinding.ActivityInitBinding
import sunmi.paylib.SunmiPayKernel

class InitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInitBinding
    private var mSMPayKernel: SunmiPayKernel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mSMPayKernel = SunmiPayKernel.getInstance()
        mSMPayKernel!!.initPaySDK(this, object : SunmiPayKernel.ConnectCallback {
            override fun onDisconnectPaySDK() {}

            override fun onConnectPaySDK() {
                try {
                    BaseApp.mReadCardOptV2 = mSMPayKernel!!.mReadCardOptV2
                    BaseApp.mEMVOptV2 = mSMPayKernel!!.mEMVOptV2
                    BaseApp.mPinPadOptV2 = mSMPayKernel!!.mPinPadOptV2
                    BaseApp.mBasicOptV2 = mSMPayKernel!!.mBasicOptV2
                    BaseApp.mSecurityOptV2 = mSMPayKernel!!.mSecurityOptV2
                    Log.e("dd--", "SDK INIT SUCCESSFUL")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })

        binding.button.setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            startActivity(i)
            finish()
        }
    }
}
