package red.man10.man10forex.util

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10forex.forex.Forex.spread
import java.util.*

object Price : CommandExecutor{

    var url = "http://taro:824/api/price"

    private var bid : Double = -1.0
    private var ask : Double = -1.0
    private var price : Double = -1.0

    init {
        Thread{asyncGetPriceThread()}.start()
    }
    //価格データ取得
    private fun getPriceData():PriceData?{

        if (price!=-1.0 && !isActiveTime())return null

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
            response.close()
        }catch (e:java.lang.Exception){
//            Bukkit.getLogger().info(e.message)
        }

        return priceData
    }

    //取引時間かどうか
    fun isActiveTime():Boolean{
        val date = Calendar.getInstance()
        date.time = Date()

        val hour = if (isSummerTime()) 6 else 7
        //土日は閉場
        if (date.get(Calendar.DAY_OF_WEEK)==Calendar.SATURDAY && date.get(Calendar.HOUR_OF_DAY)>hour){ return false }
        if (date.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY){ return false }
        if (date.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY && date.get(Calendar.HOUR_OF_DAY)<hour){ return false }

        return true
    }

    private fun isSummerTime():Boolean{

        val date = Calendar.getInstance()
        date.time = Date()

        if (date.get(Calendar.MONTH) in 4..10)return true
        if (date.get(Calendar.MONTH) == 3 && date.get(Calendar.DAY_OF_MONTH)>=14)return true
        if (date.get(Calendar.MONTH) == 11 && date.get(Calendar.DAY_OF_MONTH)<=7)return true

        return false
    }

    //仲直取得
    fun price():Double{
        return price
    }

    fun bid():Double{
        return bid
    }

    fun ask():Double{
        return ask
    }

    data class PriceData(
        val id : String,
        val symbol : String,
        val source : String,
        val bid : Double,
        val ask : Double,
        val time : String
    )

    private fun asyncGetPriceThread(){

        while (true){
            val data = getPriceData()?:continue
            price = (data.bid+data.ask)/2.0
            bid = price-spread/2.0
            ask = price+ spread/2.0
            Thread.sleep(100)
        }

    }


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="zfx")return false

        if (args.isNullOrEmpty()){
            return true
        }

        when(args[0]){
            "price" ->{
                Thread{ sender.sendMessage("§d§l現在価格....§f§l${String.format("%,.3f",price())}") }.start()
            }

            "bid" ->{
                Thread{ sender.sendMessage("§d§l現在売値....§c§l${String.format("%,.3f", bid())}") }.start()
            }

            "ask" ->{
                Thread{ sender.sendMessage("§d§l現在買値....§b§l${String.format("%,.3f", ask())}") }.start()
            }

        }

        return false
    }

}