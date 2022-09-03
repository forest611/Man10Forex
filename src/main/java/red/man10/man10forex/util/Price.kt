package red.man10.man10forex.util

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10forex.forex.Forex.spread
import kotlin.math.roundToInt

object Price : CommandExecutor{

    private const val url = "http://taro:824/api/price"


    //価格データ取得
    private fun priceData():PriceData?{

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

        return priceData
    }

    //仲直取得
    fun price():Double{
        val price : Double
        val data = priceData()?:return -1.0
        price = (data.bid+data.ask)/2.0
        return (price * 1000.0).roundToInt().toDouble()/1000.0
    }

    fun bid():Double{
        return price()-spread/2.0
    }

    fun ask():Double{
        return price()+ spread/2.0
    }

    data class PriceData(
        val id : String,
        val symbol : String,
        val source : String,
        val bid : Double,
        val ask : Double,
        val time : String
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="mprice")return false

        if (args.isNullOrEmpty()){
            return true
        }

        when(args[0]){
            "price" ->{
                Thread{ sender.sendMessage("§d§l現在価格....§f§l${String.format("%,.3f",price())}") }.start()
            }

            "bid" ->{
                Thread{ sender.sendMessage("§d§l現在買値....§c§l${String.format("%,.3f", bid())}") }.start()
            }

            "ask" ->{
                Thread{ sender.sendMessage("§d§l現在売値....§b§l${String.format("%,.3f", ask())}") }.start()
            }

        }

        return false
    }

}