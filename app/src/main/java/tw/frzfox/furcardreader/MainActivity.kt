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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.frzfox.furcardreader.data.Card
import tw.frzfox.furcardreader.databinding.ActivityMainBinding
import tw.frzfox.furcardreader.retrofit.RetrofitClient
import tw.frzfox.furcardreader.retrofit.RetrofitClient.apiType
import tw.frzfox.furcardreader.ui.theme.FurCardReaderTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

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

    //判斷是否有進設定修改參數
    val requestSetting =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val url = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE)
                .getString(RetrofitClient.urlKey, RetrofitClient.defaultUrl)
                ?: RetrofitClient.defaultUrl

            if (result.resultCode == RESULT_OK) {
                RetrofitClient.updateURL()
                Toast.makeText(this, "設定完成", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val sharedPreferences = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE)

        viewModel.cardData.observe(this) {
            binding.tvCardID.text = it.cardUID
            binding.tvCardType.text = it.cardType
            binding.tvATQA.text = it.cardATQA

            it?.let { card ->
                // 使用 lifecycleScope 啟動一個協程
                lifecycleScope.launch {
                    try {
                        var resultCode = 0
                        when (sharedPreferences.getString(apiType, "POST")) {
                            "POST" -> resultCode = viewModel.postReadCard(card)
                            "GET" -> resultCode = viewModel.getReadCard(card)
                        }
                        if (resultCode in 200..300) {
                            showCompletionDialog(true, card)
                        } else {
                            showCompletionDialog(false, card)
                        }
                        // 如果 postReadCard 成功，你可以在這裡更新 UI 或顯示提示
                        // 例如：Toast.makeText(this@MainActivity, "Card data posted!", Toast.LENGTH_SHORT).show()
                        // 注意：如果 postReadCard 內部有 UI 更新，需要確保它在主執行緒執行 (例如使用 withContext(Dispatchers.Main))
                    } catch (e: Exception) {
                        // 處理協程中可能發生的錯誤
                        Log.e(TAG, "Error in postReadCard coroutine: ${e.message}", e)
                        Toast.makeText(
                            this@MainActivity,
                            "Error posting data: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.tvReadError.text = "Error posting card data: ${e.message}"
                    }
                }
            }
        }

        viewModel.errorMsg.observe(this) {
            binding.tvReadError.text = it
        }

        binding.fab.setOnClickListener { view ->
            requestSetting.launch(Intent(this, SettingsActivity::class.java))
        }

        binding.composeCardView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FurCardReaderTheme {
                    val cardList by viewModel.cardDataList.observeAsState(initial = emptyList())
                    CardListComposable(cards = cardList)
                }
            }
        }

        RetrofitClient.initialize(this)

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
                val message =
                    "Tag detected, but not an NfcA primary tag ,Supported: $supportedTechs"
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

    private fun showCompletionDialog(success: Boolean, card: Card) {
        lifecycleScope.launch(Dispatchers.Main) {
            // 檢查 Dialog 是否已在顯示，避免重複顯示
            if (supportFragmentManager.findFragmentByTag("completion_dialog") == null) {
                val dialogFragment = CompletionDialogFragment.newInstance(success, card)
                dialogFragment.show(supportFragmentManager, "completion_dialog")
            }
        }
    }

    @Composable
    fun CardListComposable(cards: List<Card>, modifier: Modifier = Modifier) {
        if (cards.isEmpty()) {
            // 當列表為空時，可以顯示一個提示訊息
            Text(
                text = "No cards read yet.\nTap an NFC card to see details.",
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(modifier = modifier) {
                items(cards) { card -> // items 擴展函數需要導入
                    CardInfoComposable(card = card)
//                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                }
            }
        }
    }

    // 單個卡片資訊的 Composable (之前我們討論過的)
    @Composable
    fun CardInfoComposable(card: Card, modifier: Modifier = Modifier) {
        androidx.compose.material3.Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card Info",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // 你可以在這裡加入一個圖示或其他元素
                }
                Text(
                    text = "Card UID = ${card.cardUID}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Card Type = ${card.cardType}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "ATQA = ${card.cardATQA}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Result = ${card.connectResult}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // 預覽 CardListComposable
    @Preview(showBackground = true, name = "Card List Preview")
    @Composable
    fun PreviewCardListComposable() {
        FurCardReaderTheme {
            val sampleCards = listOf(
                Card(
                    "UID: 11223344",
                    "Type: MIFARE Classic",
                    "ATQA: 0004",
                    "SAK: 00",
                    100,
                    1000,
                    "Connect Result"
                ),
            )
            CardListComposable(cards = sampleCards)
        }
    }

    @Preview(showBackground = true, name = "Empty Card List Preview")
    @Composable
    fun PreviewEmptyCardListComposable() {
        FurCardReaderTheme {
            CardListComposable(cards = emptyList())
        }
    }

}