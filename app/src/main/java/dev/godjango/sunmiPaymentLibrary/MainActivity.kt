package dev.godjango.sunmiPaymentLibrary

import android.annotation.SuppressLint
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidl.AidlConstants.EMV.TLVOpCode
import com.sunmi.pay.hardware.aidl.AidlErrorCode
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.bean.EMVTransDataV2
import com.sunmi.pay.hardware.aidlv2.bean.PinPadConfigV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import dev.godjango.sunmiPaymentLibrary.callback.CheckCardCallback
import dev.godjango.sunmiPaymentLibrary.callback.EMVCallback
import dev.godjango.sunmiPaymentLibrary.callback.PinPadCallback
import dev.godjango.sunmiPaymentLibrary.databinding.ActivityMainBinding
import dev.godjango.sunmiPaymentLibrary.util.ByteUtil
import dev.godjango.sunmiPaymentLibrary.util.EmvUtil
import dev.godjango.sunmiPaymentLibrary.util.TLV
import dev.godjango.sunmiPaymentLibrary.util.TLVUtil
import java.nio.charset.StandardCharsets
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    private var mReadCardOptV2: ReadCardOptV2? = null
    private var mEMVOptV2: EMVOptV2? = null
    private var mPinPadOptV2: PinPadOptV2? = null

    //FOR UI
    private val cardType = MutableLiveData<String>()
    private val result = MutableLiveData<String>()

    private val amount = "40000"
    private var mCardNo: String = ""
    private var mCardType = 0
    private var mPinType: Int? = null
    private var mCertInfo: String = ""

    override fun onStart() {
        super.onStart()
        EmvUtil().init()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mReadCardOptV2 = BaseApp.mReadCardOptV2
        mEMVOptV2 = BaseApp.mEMVOptV2
        mPinPadOptV2 = BaseApp.mPinPadOptV2

        if (mReadCardOptV2 == null || mEMVOptV2 == null || mPinPadOptV2 == null) {
            binding.txtResult.text = "Error: No se pudo inicializar el lector de tarjetas."
            binding.btnIC.isEnabled = false
            binding.btnMSC.isEnabled = false
            binding.btnNFC.isEnabled = false
            binding.btnAll.isEnabled = false
            return
        }

        //show card type
        cardType.observe(this, Observer {
            binding.txtType.text = it
        })

        //show result
        result.observe(this, Observer {
            binding.txtResult.text = it
        })

        binding.btnIC.setOnClickListener {
            checkCard(AidlConstants.CardType.IC.value)
        }

        binding.btnMSC.setOnClickListener {
            checkCard(AidlConstants.CardType.MAGNETIC.value)
        }

        binding.btnNFC.setOnClickListener {
            checkCard(AidlConstants.CardType.NFC.value)
        }

        binding.btnAll.setOnClickListener {
            val cardType: Int =
                AidlConstants.CardType.MAGNETIC.value or AidlConstants.CardType.NFC.value or
                        AidlConstants.CardType.IC.value
            checkCard(cardType)
        }

    }

    private fun checkCard(cardType: Int) {
        try {
            mEMVOptV2?.abortTransactProcess()
            mEMVOptV2?.initEmvProcess()

            mReadCardOptV2?.checkCard(cardType, mCheckCardCallback, 60)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mCheckCardCallback: CheckCardCallbackV2 = object : CheckCardCallback() {
        @SuppressLint("SetTextI18n")
        override fun findICCard(atr: String) {
            super.findICCard(atr)
            runOnUiThread {
                cardType.value = "Type: IC"
                result.value = "Result: $atr"
                Log.e("dd--", "Type:IC")
                Log.e("dd--", "ID: $atr")
                mCardType = AidlConstants.CardType.IC.value
                transactProcess()
            }
        }

        @SuppressLint("SetTextI18n")
        override fun findMagCard(info: Bundle) {
            super.findMagCard(info)
            val track1 = info.getString("TRACK1") ?: ""
            val track2 = info.getString("TRACK2") ?: ""
            val track3 = info.getString("TRACK3") ?: ""
            runOnUiThread {
                cardType.value = "Type: Magnetic"
                result.value = "Track 1: $track1\nTrack 2: $track2\nTrack 3: $track3"
                Log.e("dd--", "Type:Magnetic")
                Log.e("dd--", "ID: ${result.value}")
                mCardType = AidlConstants.CardType.MAGNETIC.value
            }
        }

        @SuppressLint("SetTextI18n")
        override fun findRFCard(uuid: String) {
            super.findRFCard(uuid)
            runOnUiThread {
                cardType.value = "Type: NFC"
                result.value = "Result:\n UUID: $uuid"
                Log.e("dd--", "Type:NFC")
                Log.e("dd--", "ID: $uuid")
                mCardType = AidlConstants.CardType.NFC.value
                transactProcess()
            }}

        override fun onError(code: Int, message: String) {
            super.onError(code, message)
            val error = "onError:$message -- $code"
            println("Error : $error")
            runOnUiThread {
                result.value = "Error: $error"
            }
        }
    }

    private fun transactProcess() {
        Log.e("dd--", "transactProcess")
        try {
            val emvTransData = EMVTransDataV2()
            emvTransData.amount = amount //in cent (9F02)
            emvTransData.flowType = 1 //1 Standard Flow, 2 Simple Flow, 3 QPass
            emvTransData.cardType = mCardType
            mEMVOptV2?.transactProcess(emvTransData, mEMVCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mEMVCallback = object : EMVCallback(){

        override fun onWaitAppSelect(p0: MutableList<EMVCandidateV2>?, p1: Boolean) {
            super.onWaitAppSelect(p0, p1)

            Log.e("dd--", "onWaitAppSelect isFirstSelect:$p1")

            //Debit Card might have 2 AID
            //Priority 1 should be 'Debit <MS/VISA>'
            //Priority 2 should be 'ATM'
            p0?.forEach {
                Log.e("dd--", "EMVCandidate:$it")
            }
            //default take 1 priority
            mEMVOptV2?.importAppSelect(0)
        }

        override fun onAppFinalSelect(p0: String?) {
            super.onAppFinalSelect(p0)

            Log.e("dd--", "onAppFinalSelect value:$p0")


            val tags = arrayOf("5F2A", "5F36", "9F33", "9F66")
            val value = arrayOf("0458", "00", "E0F8C8", "B6C0C080")
            mEMVOptV2?.setTlvList(TLVOpCode.OP_NORMAL, tags, value)


            if (!p0.isNullOrEmpty()){
                val isVisa = p0.startsWith("A000000003")
                val isMaster =
                    (p0.startsWith("A000000004") || p0.startsWith("A000000005"))

                if (isVisa){
                    // VISA(PayWave)
                    Log.e("dd--", "detect VISA card")
                }else if(isMaster){

                    // MasterCard(PayPass)
                    Log.e("dd--", "detect MasterCard card")
                    val tagsPayPass = arrayOf(
                        "DF8117", "DF8118", "DF8119", "DF811B", "DF811D",
                        "DF811E", "DF811F", "DF8120", "DF8121", "DF8122",
                        "DF8123", "DF8124", "DF8125", "DF812C"
                    )
                    val valuesPayPass = arrayOf(
                        "E0", "F8", "F8", "30", "02",
                        "00", "E8", "F45084800C", "0000000000", "F45084800C",
                        "000000000000", "999999999999", "999999999999", "00"
                    )
                    mEMVOptV2?.setTlvList(TLVOpCode.OP_PAYPASS, tagsPayPass, valuesPayPass)

                    //Reader CVM Required Limit (Malaysia => RM250)
                    mEMVOptV2?.setTlv(TLVOpCode.OP_PAYPASS,"DF8126","000000025000")

                }

            }
            mEMVOptV2?.importAppFinalSelectStatus(0)
        }

        override fun onConfirmCardNo(p0: String?) {
            super.onConfirmCardNo(p0)
            Log.e("dd--", "onConfirmCardNo cardNo:$p0")
            mCardNo = p0!!
            mEMVOptV2?.importCardNoStatus(0)
        }

        override fun onRequestShowPinPad(p0: Int, p1: Int) {
            super.onRequestShowPinPad(p0, p1)
            Log.e("dd--", "onRequestShowPinPad pinType:$p0 remainTime:$p1")
            // 0 - online pin, 1 - offline pin
            mPinType = p0
            initPidPad()
        }

        override fun onCertVerify(p0: Int, p1: String?) {
            super.onCertVerify(p0, p1)
            Log.e("dd--", "onCertVerify certType:$p0 certInfo:$p1")
            mCertInfo = p1.toString()
            mEMVOptV2?.importCertStatus(p0)
        }

        override fun onOnlineProc() {
            super.onOnlineProc()
            Log.e("dd--", "onOnlineProc")
            try {
                if (mCardType != AidlConstants.CardType.MAGNETIC.value) {
                    val tlvData = getTlvData()
                    runOnUiThread {
                        result.value = if (tlvData.isNotEmpty()) {
                            tlvData
                        } else {
                            "No se encontraron datos TLV"
                        }
                    }
                }
                importOnlineProcessStatus(0)
            } catch (e: Exception) {
                e.printStackTrace()
                importOnlineProcessStatus(-1)
                // Podrías mostrar el error en txtResult también
                runOnUiThread {
                    result.value = "Error en el proceso online: ${e.message}"
                }
            }
        }

        override fun onTransResult(p0: Int, p1: String?) {
            super.onTransResult(p0, p1)
            //Code = 0 (Success)
            Log.e("dd--", "onTransResult code:$p0 desc:$p1")
        }
        
    }


    private fun initPidPad(){
        Log.e("dd--", "initPinPad")
        try {
            val pinPadConfig = PinPadConfigV2()
            pinPadConfig.pinPadType = 0
            pinPadConfig.pinType = mPinType!!
            pinPadConfig.isOrderNumKey = true
            val panBytes = mCardNo.substring(mCardNo.length - 13, mCardNo.length - 1)
                .toByteArray(StandardCharsets.US_ASCII)
            pinPadConfig.pan = panBytes
            pinPadConfig.timeout = 60 * 1000 // input password timeout
            pinPadConfig.pinKeyIndex = 12 // pik index (0-19)
            pinPadConfig.maxInput = 12
            pinPadConfig.minInput = 4
            pinPadConfig.keySystem = 0 // 0 - MkSk 1 - DuKpt
            pinPadConfig.algorithmType = 0 // 0 - 3DES 1 - SM4
            mPinPadOptV2?.initPinPad(pinPadConfig, mPinPadCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private val mPinPadCallback = object: PinPadCallback(){

        override fun onConfirm(p0: Int, p1: ByteArray?) {
            super.onConfirm(p0, p1)
            if (p1 != null) {
                val hexStr = ByteUtil.bytes2HexStr(p1)
                Log.e("dd--", "onConfirm pin block:$hexStr")
                importPinInputStatus(0)
            }else{
                importPinInputStatus(2)
            }
        }

        override fun onCancel() {
            super.onCancel()
            Log.e("dd--", "onCancel")
            importPinInputStatus(1)
        }

        override fun onError(p0: Int) {
            super.onError(p0)
            Log.e("dd--", "onError: ${AidlErrorCode.valueOf(p0).msg}")
            importPinInputStatus(3)
        }

    }

    private fun importPinInputStatus(inputResult: Int) {
        Log.e("dd--", "importPinInputStatus:$inputResult")
        try {
            mEMVOptV2?.importPinInputStatus(mPinType!!, inputResult)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTlvData(): String {
        val tlvData = StringBuilder()
        try {
            val tagList = arrayOf(
                "DF02", "5F34", "9F06", "FF30", "FF31", "95", "9B", "9F36", "9F26",
                "9F27", "DF31", "5A", "57", "5F24", "9F1A", "9F03", "9F33", "9F10", "9F37", "9C",
                "9A", "9F02", "5F2A", "5F36", "82", "9F34", "9F35", "9F1E", "84", "4F", "9F09", "9F41",
                "9F63", "5F20", "9F12", "50"
            )
            val payPassTags = arrayOf(
                "DF811E", "DF812C", "DF8118", "DF8119", "DF811F", "DF8117",
                "DF8124", "DF8125", "9F6D", "DF811B", "9F53", "DF810C",
                "9F1D", "DF8130", "DF812D", "DF811C", "DF811D", "9F7C"
            )
            val outData = ByteArray(2048)
            val map: MutableMap<String, TLV> = HashMap()
            var len = mEMVOptV2?.getTlvList(TLVOpCode.OP_NORMAL, tagList, outData)
            if (len != null && len > 0) {
                val hexStr = ByteUtil.bytes2HexStr(outData.copyOf(len))
                map.putAll(TLVUtil.hexStrToTLVMap(hexStr))
            }
            len = mEMVOptV2?.getTlvList(TLVOpCode.OP_PAYPASS, payPassTags, outData)
            if (len != null && len > 0) {
                val hexStr = ByteUtil.bytes2HexStr(outData.copyOf(len))
                map.putAll(TLVUtil.hexStrToTLVMap(hexStr))
            }
            val set: Set<String> = map.keys
            set.forEach {
                val tlv = map[it]
                tlvData.append(if (tlv != null) {
                    "$it : ${tlv.value} \n"
                } else {
                    "$it : \n"
                })
            }
            Log.e("dd--", "TLV: $tlvData")
        } catch (e: Exception) {
            return "Error al obtener los datos TLV: ${e.message}"
        }
        return tlvData.toString()
    }

    private fun importOnlineProcessStatus(status: Int) {
        Log.e("dd--", "importOnlineProcessStatus status:$status")
        try {
            val tags = arrayOf("71", "72", "91", "8A", "89")
            val values = arrayOf("", "", "", "", "")
            val out = ByteArray(1024)
            val len = mEMVOptV2?.importOnlineProcStatus(status, tags, values, out)
            if (len != null) {
                if (len < 0) {
                    Log.e("dd--", "importOnlineProcessStatus error,code:$len")
                } else {
                    val bytes = out.copyOf(len)
                    val hexStr = ByteUtil.bytes2HexStr(bytes)
                    Log.e("dd--", "importOnlineProcessStatus outData:$hexStr")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mReadCardOptV2?.cancelCheckCard()
    }

}
