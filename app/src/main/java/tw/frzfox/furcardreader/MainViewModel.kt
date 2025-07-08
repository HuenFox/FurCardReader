package tw.frzfox.furcardreader

import android.nfc.Tag
import android.nfc.tech.NfcA
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.IOException

class MainViewModel : ViewModel() {
    private val TAG = MainViewModel::class.java.simpleName
    var cardData = MutableLiveData<Card>()
    var errorMsg = MutableLiveData<String>()

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
            cardData.postValue(Card("NfcA", tagIdHex ?: "Empty", atqaHex, "%02X".format(sak), nfcA.maxTransceiveLength, nfcA.timeout))
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
            cardData.postValue(Card("NfcA", "ERROR", "", "", 0, 0))
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
}
