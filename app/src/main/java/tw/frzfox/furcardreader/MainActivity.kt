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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import tw.frzfox.furcardreader.data.Card
import tw.frzfox.furcardreader.data.Post
import tw.frzfox.furcardreader.databinding.ActivityMainBinding
import tw.frzfox.furcardreader.retrofit.RetrofitClient
import java.time.LocalDateTime


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

        viewModel.cardData.observe(this) {
            binding.tvCardID.text = it.cardUID
            binding.tvCardType.text = it.cardType
            binding.tvATQA.text = it.cardATQA

            it?.let { card ->
                // 使用 lifecycleScope 啟動一個協程
                lifecycleScope.launch {
                    try {
                        postReadCard(card)
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

    suspend fun getData() {
        val post =
            RetrofitClient.instance.getPostByIdSuspend(1) // 假設 ApiService 有 getPostByIdSuspend
        Log.d("MainActivity", "Fetched Post (Coroutine): $post")
    }

    suspend fun postReadCard(card : Card) {
        try {
            val newPost = Post(
                userId = 1,
                id = 0,
                title = "Card Read",
                body = "card.cardType = ${card.cardType},card.cardUID = ${card.cardUID}, card.cardATQA = ${card.cardATQA}, card.maxTransLen = ${card.maxTransLen}, card.timeout = ${card.timeout}"
            ) // id 通常由伺服器生成，所以可以設為 0 或忽略
            val response = RetrofitClient.instance.createPost(newPost)

//            如果我要自訂Body的話
//            var paramObject :JSONObject
//            try {
//                paramObject = JSONObject().apply {
//                    put("cardId", card.cardUID)
//                    put("cardType", card.cardType)
//                }
//
//            } catch (e: JSONException) {
//                e.printStackTrace()
//                return
//            }
//
//            val response = RetrofitClient.instance.createPost(paramObject.toString())
            if (response.isSuccessful) {
                val createdPost = response.body()
                showCompletionDialog(card)
                // 成功！createdPost 包含了伺服器返回的 Post 物件 (可能包含伺服器生成的 ID)
                Log.d("ApiService", "Post created successfully: $createdPost")
                // 在這裡更新 UI 或執行其他操作
            } else {
                // API 呼叫成功，但伺服器返回了錯誤狀態碼 (例如 400, 404, 500)
                val errorBody = response.errorBody()?.string() // 獲取錯誤回應的內容
                Log.e(
                    "ApiService",
                    "Error creating post: ${response.code()} - ${response.message()}. Error body: $errorBody"
                )
                // 處理錯誤，例如顯示錯誤訊息給使用者
            }
        } catch (e: Exception) {
            // 網路錯誤或其他異常 (例如 JSON 解析錯誤)
            Log.e("ApiService", "Exception when creating post: ${e.message}", e)
            // 處理異常
        }
    }

    private fun showCompletionDialog(card: Card) {
        // 檢查 Dialog 是否已在顯示，避免重複顯示
        if (supportFragmentManager.findFragmentByTag("completion_dialog") == null) {
            val dialogFragment = CompletionDialogFragment.newInstance(card)
            dialogFragment.show(supportFragmentManager, "completion_dialog")
        }
    }


}