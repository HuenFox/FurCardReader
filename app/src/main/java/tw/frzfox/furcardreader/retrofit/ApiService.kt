package tw.frzfox.furcardreader.retrofit

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import tw.frzfox.furcardreader.data.Post

interface ApiService {

    @GET("posts/{id}")
    fun getPostById(@Path("id") postId: Int): Call<Post> // Call<T> 用於非協程的同步/異步請求

//     如果你使用協程 (Coroutines)，可以這樣定義：
     @GET("posts/{id}")
     suspend fun getPostByIdSuspend(@Path("id") postId: Int): Post // 直接返回資料模型

    // @GET("posts")
    // suspend fun getAllPostsSuspend(): List<Post>

    @POST("posts") //
    suspend fun createPost(@Body postData: Post): Response<Post>

    @POST("posts") // 想要自訂Body的時候
    suspend fun createPost(@Body postBody: String): Response<Post>

    @GET("posts")
    suspend fun getReadCard(): Response<String>// 假設伺服器會返回一個 CardReadResponse 物件
}