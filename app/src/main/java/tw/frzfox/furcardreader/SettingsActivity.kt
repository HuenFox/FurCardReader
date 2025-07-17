package tw.frzfox.furcardreader

import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import tw.frzfox.furcardreader.databinding.ActivitySettingBinding
import tw.frzfox.furcardreader.retrofit.RetrofitClient.apiType
import tw.frzfox.furcardreader.retrofit.RetrofitClient.defaultUrl
import tw.frzfox.furcardreader.retrofit.RetrofitClient.urlKey

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPreferences = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE)

        val apiTypeAdapter: ArrayAdapter<String> =
            ArrayAdapter(this, R.layout.spinner_item, arrayOf("POST", "GET"))
        binding.apiTypeSpinner
        binding.apiTypeSpinner.adapter = apiTypeAdapter

        //顯示原本的設定資料
        binding.apiTypeSpinner.setSelection(apiTypeAdapter.getPosition(sharedPreferences.getString(apiType, "POST")))
        binding.apiEditText.setText(sharedPreferences.getString(urlKey, defaultUrl))

        binding.saveButton.setOnClickListener {
            if (binding.apiEditText.text.toString().isEmpty()) {
                sharedPreferences.edit().putString(urlKey, defaultUrl).apply()
            } else {
                if (isValidUrl(binding.apiEditText.text.toString())) {
                    sharedPreferences.edit().putString(urlKey, binding.apiEditText.text.toString())
                        .apply()
                    sharedPreferences.edit()
                        .putString(apiType, binding.apiTypeSpinner.selectedItem.toString()).apply()
                } else {
                    Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

            }
            setResult(RESULT_OK, intent.putExtra("URL", binding.apiEditText.text.toString()))
            finish()
        }

    }

    fun isValidUrl(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) {
            return false
        }

        return Patterns.WEB_URL.matcher(urlString).matches()
    }

}
