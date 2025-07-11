package tw.frzfox.furcardreader.retrofit

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tw.frzfox.furcardreader.R

object RetrofitClient {

    private lateinit var appContext: Context
    private lateinit var BASE_URL : String
    lateinit var instance: ApiService
    val urlKey : String = "apiUrl"
    val defaultUrl : String = "https://jsonplaceholder.typicode.com/"
    // (可選) 建立 OkHttpClient 並加入日誌攔截器
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 設定日誌級別
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor) // (可選) 加入日誌攔截器
        .build()

//    val instance: ApiService by lazy {
//        val retrofit = Retrofit.Builder()
//            .baseUrl(BASE_URL)
//            .client(okHttpClient) // (可選) 設定自訂的 OkHttpClient
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//        retrofit.create(ApiService::class.java)
//    }



    fun initialize(context: Context) {
        appContext = context.applicationContext
        val sharedPreferences = appContext.getSharedPreferences(appContext.getString(R.string.app_name) , Context.MODE_PRIVATE)
        BASE_URL = sharedPreferences.getString(urlKey, defaultUrl) ?: defaultUrl

        instance = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    //用於更新BaseURL
    fun updateURL(){
        BASE_URL = appContext.getSharedPreferences(
            appContext.getString(R.string.app_name) , Context.MODE_PRIVATE).
            getString(urlKey, defaultUrl) ?: defaultUrl

        instance = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // (可選) 設定自訂的 OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }

    fun backToDefaultUrl(){
        BASE_URL = defaultUrl
        val sharedPreferences = appContext.getSharedPreferences(appContext.getString(R.string.app_name) , Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(urlKey, defaultUrl).apply()
    }
}