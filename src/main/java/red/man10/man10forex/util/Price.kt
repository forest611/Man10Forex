package red.man10.man10forex.util

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.Bukkit
import kotlin.math.roundToInt

object Price {

    private const val url = "http://taro:824/api/price"
    var spread : Double = 0.01  //スプレッド(Price)

    //価格データ取得
    fun priceData():PriceData{

        var priceData : PriceData? = null

        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()?:""

            val jsonObj = Gson().fromJson(body,Array<PriceData>::class.java)

            jsonObj.forEach {
                if (it.symbol == "USDJPY"){
                    priceData = it
                }
            }

        }catch (e:java.lang.Exception){
            Bukkit.getLogger().info(e.message)
        }

        return priceData!!
    }

    //仲直取得
    fun price():Double{
        val price : Double
        val data = priceData()
        price = (data.bid+data.ask)/2.0
        return (price * 1000.0).roundToInt().toDouble()/1000.0
    }

    fun bid():Double{
        return price()
    }

    fun ask():Double{
        return price()+ spread
    }

    data class PriceData(
        val id : String,
        val symbol : String,
        val source : String,
        val bid : Double,
        val ask : Double,
        val time : String
    )

}