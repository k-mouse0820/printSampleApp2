package jp.daisen_solution.printsampleapp2

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.toshibatec.bcp.library.BCPControl
import jp.co.toshibatec.bcp.library.LongRef
import jp.co.toshibatec.bcp.library.StringRef
import jp.daisen_solution.printsampleapp2.databinding.ActivityMainBinding
import jp.daisen_solution.printsampleapp2.print.*
import jp.daisen_solution.printsampleapp2.utils.util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity(),

    BCPControl.LIBBcpControlCallBack {

    //Printer
    private lateinit var binding:ActivityMainBinding
    private var isPrinterConnected = false
    private var mBcpControl: BCPControl? = null
    private var mConnectionData: ConnectionData? = ConnectionData()
    private var mPrintData: PrintData? = PrintData()
    private var mPrintDialogDelegate: PrintDialogDelegate? = null
    private var systemPath: String = ""


    private lateinit var context: Context
    private lateinit var mActivity: Activity
    private lateinit var mOptionsMenu: Menu


    private val printerSelectResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val printerBluetoothDeviceExtra = it.data?.getStringExtra(Consts.bluetoothDeviceExtra)
            Log.v("DEBUG", printerBluetoothDeviceExtra!!)
            val printerBdAddress = printerBluetoothDeviceExtra!!.substring(
                printerBluetoothDeviceExtra!!.indexOf("(") + 1,
                printerBluetoothDeviceExtra!!.indexOf(")")
            )
            if (printerBdAddress == null || printerBdAddress.isEmpty()) {
                util.showAlertDialog(this, this.getString(R.string.bdAddrNotSet) )
                confirmationEndDialog(this)
            }

            /////////////////////////////////////////////////////////////////////////////////////
            // プリンタ初期設定
            /////////////////////////////////////////////////////////////////////////////////////
            mBcpControl = BCPControl(this)
            mPrintDialogDelegate = PrintDialogDelegate(this, mBcpControl!!, mPrintData)

            // systemPathを設定
            systemPath = Environment.getDataDirectory().path + "/data/" + this.packageName
            Log.i("set systemPath", systemPath)
            mBcpControl!!.systemPath = systemPath

            var continueFlag = true

            // プリンタ設定ファイル、ラベルフォーマットファイルのセット
            val newfile = File(systemPath)
            if (!newfile.exists()) {
                if (newfile.mkdirs()) {}
            }
            try {
                util.asset2file(applicationContext, "PrtList.ini", systemPath, "PrtList.ini")
                util.asset2file(applicationContext, "PRTEP2G.ini", systemPath, "PRTEP2G.ini")
                util.asset2file(applicationContext, "PRTEP4T.ini", systemPath, "PRTEP4T.ini")
                util.asset2file(applicationContext, "PRTEP2GQM.ini", systemPath, "PRTEP2GQM.ini")
                util.asset2file(applicationContext, "PRTEP4GQM.ini", systemPath, "PRTEP4GQM.ini")
                util.asset2file(applicationContext, "PRTEV4TT.ini", systemPath, "PRTEV4TT.ini")
                util.asset2file(applicationContext, "PRTEV4TG.ini", systemPath, "PRTEV4TG.ini")
                util.asset2file(applicationContext, "PRTLV4TT.ini", systemPath, "PRTLV4TT.ini")
                util.asset2file(applicationContext, "PRTLV4TG.ini", systemPath, "PRTLV4TG.ini")
                util.asset2file(applicationContext, "PRTFP2DG.ini", systemPath, "PRTFP2DG.ini")
                util.asset2file(applicationContext, "PRTFP3DG.ini", systemPath, "PRTFP3DG.ini")
                util.asset2file(applicationContext, "PRTBA400TG.ini", systemPath, "PRTBA400TG.ini")
                util.asset2file(applicationContext, "PRTBA400TT.ini", systemPath, "PRTBA400TT.ini")
                util.asset2file(applicationContext, "PRTBV400G.ini", systemPath, "PRTBV400G.ini")
                util.asset2file(applicationContext, "PRTBV400T.ini", systemPath, "PRTBV400T.ini")

                util.asset2file(applicationContext, "ErrMsg0.ini", systemPath, "ErrMsg0.ini")
                util.asset2file(applicationContext, "ErrMsg1.ini", systemPath, "ErrMsg1.ini")
                util.asset2file(applicationContext, "resource.xml", systemPath, "resource.xml")

                util.asset2file(applicationContext, "EP2G_scanToPrint.lfm", systemPath, "tempLabel.lfm")
                //util.asset2file(applicationContext, "B_LP2D_label.lfm", systemPath, "tempLabel.lfm")
            } catch (e: Exception) {
                util.showAlertDialog(this,
                    "Failed to copy ini and lfm files.")
                e.printStackTrace()
                continueFlag = false
                //return
            }

            if (continueFlag) {
                // 使用するプリンタの設定   B-LP2Dは「27」
                mBcpControl!!.usePrinter = 27
                //mBcpControl!!.usePrinter = 99
                Log.v("DEBUG", mBcpControl!!.usePrinter.toString())

                // 通信パラメータの設定
                mConnectionData!!.issueMode = Consts.AsynchronousMode   // 1:送信完了復帰  2:発行完了復帰
                mConnectionData!!.portSetting = "Bluetooth:$printerBdAddress"

                // 通信ポートのオープン　（非同期処理）
                var mOpenPortTask = OpenPortTask(this, mBcpControl, mConnectionData)

                binding.progressAction.visibility = View.VISIBLE
                binding.progressMessage.text = getString(R.string.msg_connectingPrinter)

                if (!mConnectionData!!.isOpen.get()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        var resultMessage = mOpenPortTask.openBluetoothPort()
                        binding.progressAction.visibility = View.GONE
                        if (resultMessage.equals(getString(R.string.msg_success))) {
                            Log.i("openPort", "isOpen = " + mConnectionData!!.isOpen.toString())
                            mOptionsMenu.findItem(R.id.printer).setIcon(R.drawable.baseline_print_24_white)
                            binding.printButton.isEnabled=true

                        } else {
                            mOptionsMenu.findItem(R.id.printer).setIcon(R.drawable.baseline_print_24)
                            util.showAlertDialog(context, resultMessage)
                        }
                    }
                } else {
                    Log.v("openPort", "Already opened - skip")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mActivity = this

        binding.printButton.isEnabled=false
        binding.printButton.setTextSize(120.0f)

        binding.printButton.setOnClickListener { printButtonClicked() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        mOptionsMenu = menu!!
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.printer -> {
                if(isPrinterConnected){
                    //bt.disconnect()
                    item.setIcon(R.drawable.baseline_print_24)
                } else {
                    val intent = Intent(applicationContext, SelectPrinterActivity::class.java)
                    printerSelectResultLauncher.launch(intent)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun printButtonClicked() {

        val enteredText = binding.enterFieldEditText.text.toString()

        mPrintData = PrintData()
        val result = LongRef(0)
        mPrintData!!.currentIssueMode = mConnectionData!!.issueMode
        mPrintData!!.printCount = 1  // 決め打ちで１枚とする

        val printItemList = HashMap<String?, String?>()

        // 入力欄データ（10桁）
        if (enteredText.length > 10) {
            printItemList[getString(R.string.nyuuryokuranData)] = enteredText.substring(0,9)
        } else {
            printItemList[getString(R.string.nyuuryokuranData)] = enteredText
        }

        // 納入日データ（12桁）
        val nounyubi = "nounyubi"
        if (nounyubi.length > 12) {
            printItemList[getString(R.string.nounyubiData)] = nounyubi.substring(0,11)
        } else {
            printItemList[getString(R.string.nounyubiData)] = nounyubi
        }

        // 施工データ（12桁）
        val sekou = "sekou"
        if (sekou.length > 12) {
            printItemList[getString(R.string.sekouData)] = sekou.substring(0,11)
        } else {
            printItemList[getString(R.string.sekouData)] = sekou
        }

        // メーカー名データ（12桁）
        val makermei = "makermei"
        if (makermei.length > 12) {
            printItemList[getString(R.string.makermeiData)] = makermei.substring(0,11)
        } else {
            printItemList[getString(R.string.makermeiData)] = makermei
        }

        // 品番データ（12桁）
        val hinban = "hinban"
        if (hinban.length > 12) {
            printItemList[getString(R.string.hinbanData)] = hinban.substring(0,11)
        } else {
            printItemList[getString(R.string.hinbanData)] = hinban
        }

        // 個数データ（3桁）
        val kosuu = "kosuu"
        if (kosuu.length > 3) {
            printItemList[getString(R.string.kosuuData)] = kosuu.substring(0,2)
        } else {
            printItemList[getString(R.string.kosuuData)] = kosuu
        }

        // サイズデータ（2桁）
        val size = "size"
        if (size.length > 2) {
            printItemList[getString(R.string.sizeData)] = size.substring(0,1)
        } else {
            printItemList[getString(R.string.sizeData)] = size
        }

        // QRCODE
        val qrcode = enteredText
        printItemList[getString(R.string.qrcodeData)] = qrcode

        // 印刷データをセット
        mPrintData!!.objectDataList = printItemList

        // lfmファイルをセット
        val filePathName =
            systemPath + "/tempLabel.lfm"
        mPrintData!!.lfmFileFullPath = filePathName

        // 印刷実行スレッドの起動
        var mPrintExecuteTask = PrintExecuteTask(this,mBcpControl, mPrintData)
        binding.progressAction.visibility = View.VISIBLE
        binding.progressMessage.text = getString(R.string.msg_executingPrint)

        CoroutineScope(Dispatchers.Main).launch {
            var resultMessage = mPrintExecuteTask.print()
            binding.progressAction.visibility = View.INVISIBLE
            when (resultMessage) {
                getString(R.string.msg_success) -> {
                    // mActivity.showDialog(PrintDialogDelegate.Companion.PRINT_COMPLETEMESSAGE_DIALOG)
                }
                getString(R.string.msg_RetryError) -> {
                    mActivity.showDialog(PrintDialogDelegate.Companion.RETRYERRORMESSAGE_DIALOG)
                }
                else -> {
                    mActivity.showDialog(PrintDialogDelegate.Companion.ERRORMESSAGE_DIALOG)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // プリンターからのメッセージ受信
    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun BcpControl_OnStatus(PrinterStatus: String?, Result: Long) {
        var strMessage = ""
        val message = StringRef("")
        strMessage = if (! mBcpControl!!.GetMessage(Result, message)) {
            String.format(getString(R.string.statusReception) + " %s : %s ", PrinterStatus, "failed to error message")
        } else {
            String.format(getString(R.string.statusReception) + " %s : %s ", PrinterStatus, message.getStringValue())
        }
        Log.i("onStatus", strMessage)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // プリンタのBluetoothポートをクローズするメソッド
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private fun closePrinterBluetoothPort() {
        Log.i("closePort","close port start")
        if (mConnectionData!!.isOpen.get()) {
            Log.i("closePort","close port start2")
            val Result = LongRef(0)
            if (! mBcpControl!!.ClosePort(Result)) {
                val Message = StringRef("")
                if (! mBcpControl!!.GetMessage(Result.longValue, Message)) {
                    Log.e("closePort",String.format(R.string.msg_PortCloseErrorcode.toString() + "= %08x", Result.longValue))
                    util.showAlertDialog(
                        this,
                        String.format(R.string.msg_PortCloseErrorcode.toString() + "= %08x", Result.longValue)
                    )
                } else {
                    Log.e("closePort",Message.getStringValue())
                    util.showAlertDialog(this, Message.getStringValue())
                }
            } else {
                Log.i("closePort",this.getString(R.string.msg_PortCloseSuccess))
                //util.showAlertDialog(this, this.getString(R.string.msg_PortCloseSuccess))
                mConnectionData!!.isOpen = AtomicBoolean(false)
                mOptionsMenu.findItem(R.id.printer).setIcon(R.drawable.baseline_print_24)
                binding.printButton.isEnabled=false
            }
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // 「前画面に戻る」ボタン押下時の処理
    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event!!.action == KeyEvent.ACTION_DOWN) {
            if (event!!.keyCode == KeyEvent.KEYCODE_BACK) {
                confirmationEndDialog(this)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
    private fun confirmationEndDialog(activity: Activity) {
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setMessage(R.string.confirmBack)
        alertBuilder.setCancelable(false)
        alertBuilder.setPositiveButton(R.string.msg_Ok) { _, _ ->
            this.closePrinterBluetoothPort()
            mBcpControl = null
            mConnectionData = null
            mPrintData = null
            mPrintDialogDelegate = null
            finish()
        }
        alertBuilder.setNegativeButton(R.string.msg_No) { _, _ ->
            // 何もしない
        }
        val alertDialog = alertBuilder.create()
        alertDialog.show()
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Dialog作成処理
    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun onPrepareDialog(id: Int, dialog: Dialog) {
        if (false == mPrintDialogDelegate!!.prepareDialog(id, dialog)) {
            super.onPrepareDialog(id, dialog)
        }
    }
    override fun onCreateDialog(id: Int): Dialog {
        var dialog: Dialog? = null
        dialog = mPrintDialogDelegate!!.createDialog(id)
        if (null == dialog) {
            dialog = super.onCreateDialog(id)
        }
        return dialog!!
    }


}