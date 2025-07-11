package tw.frzfox.furcardreader

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import tw.frzfox.furcardreader.databinding.ActivitySettingBinding
import tw.frzfox.furcardreader.retrofit.RetrofitClient.defaultUrl
import tw.frzfox.furcardreader.retrofit.RetrofitClient.urlKey

class SettingsActivity : AppCompatActivity()  {
    private lateinit var binding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences(getString(R.string.app_name) , MODE_PRIVATE)
            if(binding.apiEditText.text.toString().isEmpty()){
                sharedPreferences.edit().putString(urlKey, defaultUrl).apply()
            }else{
                if(isValidUrl(binding.apiEditText.text.toString())){
                    sharedPreferences.edit().putString(urlKey, binding.apiEditText.text.toString()).apply()
                }else{
                    Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

            }
            setResult(RESULT_OK, intent.putExtra("URL",  binding.apiEditText.text.toString()))
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
