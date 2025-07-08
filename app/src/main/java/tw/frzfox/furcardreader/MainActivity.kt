package tw.frzfox.furcardreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import tw.frzfox.furcardreader.databinding.ActivityMainBinding

//TODO 我還沒做完(2025.07.08)
//TODO 1.引retrofit來用
//TODO 2.增加自訂Restful目的地 (並先以Get/Post功能為主)
//TODO 3.提供使用者自訂傳送的body參數
//4. 還不確定會不會用到離線資料庫、Room或sqlite再考慮看看，要用再加

class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName
    private lateinit var viewModel: MainViewModel
    private lateinit var mTurnNfcDialog: AlertDialog
    private lateinit var mNfcAdapter: NfcAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var nfcAdapter: NfcAdapter
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private val techListsArray = arrayOf(
        arrayOf(NfcF::class.java.name),
        arrayOf(android.nfc.tech.NfcA::class.java.name),
        arrayOf(android.nfc.tech.NfcB::class.java.name),
        arrayOf(android.nfc.tech.NfcV::class.java.name),
        arrayOf(android.nfc.tech.NfcBarcode::class.java.name),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        viewModel.cardData.observe(this){
            binding.tvCardID.text = it.cardUID
            binding.tvCardType.text = it.cardType
            binding.tvATQA.text = it.cardATQA
        }

        viewModel.errorMsg.observe(this){
            binding.tvReadError.text = it
        }


        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported", Toast.LENGTH_SHORT).show()
        } else if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "Please turn on NFC", Toast.LENGTH_SHORT).show()
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndef.addDataType("text/plain")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("fail", e)
        }
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFiltersArray = arrayOf(ndef, tech)

    }

    override fun onResume() {
        super.onResume()
        // Enable NFC foreground dispatch to listen for tags
        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            intentFiltersArray,
            techListsArray
        )
    }

    override fun onPause() {
        // Disable NFC foreground dispatch
        if (this.isFinishing) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

            val nfcA = NfcA.get(tag)
            if (nfcA != null) {
                viewModel.parseNfcACard(tag, nfcA)
            } else {
                // 如果 NfcA.get(tag) 返回 null，表示這張卡不直接支援 NfcA 技術，
                // 或者它可能被其他更具體的技術 (如 IsoDep, MifareClassic) 所覆蓋。
                // 您可以檢查 tagFromIntent.techList 來查看標籤支援的所有技術
                val supportedTechs = tag?.techList?.joinToString(", ")
                val message = "Tag detected, but not an NfcA primary tag ,Supported: $supportedTechs"
                Log.w("NfcAExample", message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                binding.tvReadError.text = message

                // 在這裡，您可以嘗試獲取其他技術，例如：
                // val isoDep = IsoDep.get(tagFromIntent)
                // if (isoDep != null) { /* 處理 IsoDep */ }

            }

        } else {
            Toast.makeText(applicationContext, "NFC not supported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }


}