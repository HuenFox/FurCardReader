package tw.frzfox.furcardreader

import android.nfc.Tag
import android.nfc.tech.NfcA
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import tw.frzfox.furcardreader.data.Card
import tw.frzfox.furcardreader.data.Post
import tw.frzfox.furcardreader.retrofit.RetrofitClient
import java.io.IOException

class MainViewModel : ViewModel() {
    private val TAG = MainViewModel::class.java.simpleName
    var cardData = MutableLiveData<Card>()
    var errorMsg = MutableLiveData<String>()

    // 將 cardData 改為 MutableLiveData<List<Card>>
    private val _cardDataList = MutableLiveData<List<Card>>(emptyList())
    val cardDataList: LiveData<List<Card>> = _cardDataList

    private val _latestCard = MutableLiveData<Card?>()
    val latestCard: LiveData<Card?> = _latestCard // 如果外部不需要觀察單個最新卡片，可以設為 private

    init {}

    fun parseNfcACard(tag: Tag?, nfcA: NfcA) {
        // 成功獲取 NfcA 技術，表示這張卡支援 NfcA
        try {
            // 連接 NfcA 標籤
            nfcA.connect()
            Log.d("NfcAExample", "NfcA connected")

            // 獲取 NfcA 特有的資訊
            val atqa = nfcA.atqa // ATQA/SENS_RES bytes
            val sak = nfcA.sak   // SAK/SEL_RES byte
            val tagId = tag?.id // 標籤的 UID
            val atqaHex = atqa.joinToString("") { "%02X".format(it) }
            val tagIdHex = tagId?.joinToString("") { "%02X".format(it) }

            val nfcInfo = StringBuilder()
            nfcInfo.append("NfcA Tag Detected:\n")
            nfcInfo.append("UID: $tagIdHex\n")
            nfcInfo.append("ATQA (SENS_RES): $atqaHex\n")
            nfcInfo.append("SAK (SEL_RES): ${"%02X".format(sak)}\n")
            nfcInfo.append("Max Transceive Length: ${nfcA.maxTransceiveLength} bytes\n")
            nfcInfo.append("Timeout: ${nfcA.timeout} ms\n")
            Log.d(TAG, nfcInfo.toString())
            cardData.postValue(
                Card(
                    "NfcA",
                    tagIdHex ?: "Empty",
                    atqaHex,
                    "%02X".format(sak),
                    nfcA.maxTransceiveLength,
                    nfcA.timeout,
                    ""
                )
            )
            Log.d(TAG, "Showing Tech List")
            tag?.techList?.forEach {
                Log.d(TAG, "It have : $it")
            }

            // --- 進行讀寫操作 (範例：發送一個簡單的命令) ---
            // 注意：NfcA 的直接命令通常比較底層，取決於具體的卡片類型 (如 MIFARE Ultralight, MIFARE Classic 等)
            // 如果是 MIFARE Classic，您需要使用 MifareClassic.get(tagFromIntent)
            // 如果是 MIFARE Ultralight，您需要使用 MifareUltralight.get(tagFromIntent)
            // 對於通用的 NfcA，transceive() 方法用於發送原始命令並接收回應

            // 例如，一個非常基礎的、可能對某些卡片無效的 SELECT 命令 (僅為演示)
            // byte[] selectCommand = new byte[] { (byte)0x93, (byte)0x20 }; // ISO14443-3 SELECT_CMD
            // byte[] response = nfcA.transceive(selectCommand);
            // Log.d("NfcAExample", "Response from transceive: " + response.joinToString("") { "%02X".format(it) });

            // 在這裡，您可以根據您的卡片規格執行更具體的讀取或寫入操作

        } catch (e: IOException) {
            Log.e(
                "NfcAExample",
                "IOException while connecting or transceiving: ${e.localizedMessage}",
                e
            )
            errorMsg.postValue("Error communicating with NfcA tag: ${e.localizedMessage}")
            cardData.postValue(Card("NfcA", "ERROR", "", "", 0, 0, ""))
        } finally {
            // 無論成功或失敗，都要記得關閉連接
            try {
                nfcA.close()
                Log.d("NfcAExample", "NfcA closed")
            } catch (e: IOException) {
                Log.e("NfcAExample", "IOException while closing: ${e.localizedMessage}", e)
            }
        }
    }

    suspend fun postReadCard(card: Card): Int {
        var result = 0
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
                // 成功！createdPost 包含了伺服器返回的 Post 物件 (可能包含伺服器生成的 ID)
                Log.d("ApiService", "Post created successfully: $createdPost")
                // 在這裡更新 UI 或執行其他操作
                card.connectResult = "[Success] " + response.code().toString()
            } else {
                // API 呼叫成功，但伺服器返回了錯誤狀態碼 (例如 400, 404, 500)
                val errorBody = response.errorBody()?.string() // 獲取錯誤回應的內容
                Log.e(
                    "ApiService",
                    "Error creating post: ${response.code()} - ${response.message()}. Error body: $errorBody"
                )
                // 處理錯誤，例如顯示錯誤訊息給使用者
                card.connectResult = errorBody ?: response.message() ?: "Response unsuccessful"
            }
            result = response.code()
        } catch (e: Exception) {
            // 網路錯誤或其他異常 (例如 JSON 解析錯誤)
            Log.e("ApiService", "Exception when creating post: ${e.message}", e)
            card.connectResult = e.message ?: "Unknown error"
        } finally {
            addCard(card)
        }
        return result
    }

    suspend fun getReadCard(card: Card): Int {
        var resultCode = 0
        try {
            // 呼叫 ApiService 中的 GET 方法
            val response = RetrofitClient.instance.getReadCard()

            resultCode = response.code()

            if (response.isSuccessful) {
                val responseBodyString: String? = response.body() // response.body() 現在是
                // 成功！cardReadResponse 包含了伺服器返回的數據
                Log.d(TAG, "GET request successful: $responseBodyString")
                card.connectResult = ("[Success] $responseBodyString") ?: ""
            } else {
                // API 呼叫成功，但伺服器返回了錯誤狀態碼 (例如 400, 404, 500)
                val errorBody = response.errorBody()?.string()
                Log.e(
                    TAG,
                    "Error in GET request: ${response.code()} - ${response.message()}. Error body: $errorBody"
                )
                errorMsg.postValue("Error from server (GET): ${response.code()} - ${errorBody ?: response.message()}")
                card.connectResult = errorBody ?: response.message()
            }
        } catch (e: Exception) {
            // 網路錯誤或其他異常 (例如 JSON 解析錯誤)
            Log.e(TAG, "Exception during GET request: ${e.message}", e)
            errorMsg.postValue("Network or other error (GET): ${e.message}")
            resultCode = -1 // 或其他表示客戶端錯誤的代碼
            card.connectResult = e.message ?: "Unknown error"
        } finally {
            addCard(card)
        }
        return resultCode
    }

    private fun addCard(card: Card) {
        val currentList = _cardDataList.value.orEmpty().toMutableList()
        currentList.add(0, card) //新的插入在第一個
        _cardDataList.value = currentList
    }

}
