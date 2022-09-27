package red.man10.man10forex.util

import com.google.gson.Gson
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10forex.forex.Forex
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Price : CommandExecutor{

    var url = "http://taro:824/api/price"

    private val currencyMap = ConcurrentHashMap<String,PriceData>()

    var finalDate = ""

    var error = true
    var dateCount = 0

    init {
        Thread{asyncGetPriceThread()}.start()
    }

    //取引時間かどうか
    fun isActiveTime():Boolean{

//        return true
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
    fun price(symbol: String): Double {
        return currencyMap[symbol]?.price?:-1.0
    }

    fun bid(symbol: String): Double {
        return currencyMap[symbol]?.bid?:-1.0
    }

    fun ask(symbol: String): Double {
        return currencyMap[symbol]?.ask?:-1.0
    }

    data class DeserializedData(
        val id : String,
        val symbol : String,
        val source : String,
        val bid : Double,
        val ask : Double,
        val time : String
    )

    data class PriceData(
        val symbol : String,
        val bid : Double,
        val ask : Double,
        val price : Double
    )

    private fun asyncGetPriceThread(){

        val client = OkHttpClient.Builder().cache(null).build()

        Main@while (true){

            Thread.sleep(100)

            if (!isActiveTime())continue@Main

            try {

                if (error){
                    client.connectionPool.evictAll()
                }

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                //レスポンスが返ってこなかったら止める
                if (body == null){
                    error = true
                    continue@Main
                }

                val jsonObj = Gson().fromJson(body,Array<DeserializedData>::class.java)

                if (jsonObj == null){
                    error = true
                    continue@Main
                }

                var checkedDate = false//時刻確認フラグ

                for (obj in jsonObj){
                    val symbol = obj.symbol

                    //前回と取得時刻が変わらなかった場合はエラー
                    if (finalDate == obj.time && !checkedDate){
                        dateCount++
                        if (dateCount>=20){ error = true }
                        continue@Main
                    }

                    checkedDate = true
                    dateCount = 0

                    finalDate = obj.time

                    val price = (obj.bid+obj.ask)/2.0
                    val ask = price+(Forex.spread/2.0)
                    val bid = price-(Forex.spread/2.0)

                    val data = PriceData(symbol,bid,ask,price)

                    currencyMap[symbol] = data
                }

                error = false
            }catch (e:java.lang.Exception){
                error = true
            }
        }

    }


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="zfx")return false

        if (args.isNullOrEmpty()){
            return true
        }

        if (args.size!=2){
            sender.sendMessage("/zfx price/bid/ask symbol")
            return true
        }

        val symbol = args[1]

        when(args[0]){

            "price" ->{

                if (symbol=="all"){
                    currencyMap.keys.forEach { sender.sendMessage("§d§l現在価格(${it})....§f§l${String.format("%,.3f",price(it))}") }
                }else{
                    sender.sendMessage("§d§l現在価格....§f§l${String.format("%,.3f",price(symbol))}")
                }
            }

            "bid" ->{
                if (symbol=="all"){
                    currencyMap.keys.forEach { sender.sendMessage("§c§l現在価格(Bid)(${it})....${String.format("%,.3f",bid(it))}") }
                }else{
                    sender.sendMessage("§c§l現在価格(Bid)....${String.format("%,.3f",bid(symbol))}")
                }
            }

            "ask" ->{
                if (symbol=="all"){
                    currencyMap.keys.forEach { sender.sendMessage("§b§l現在価格(Ask)(${it})....${String.format("%,.3f",ask(it))}") }
                }else{
                    sender.sendMessage("§b§l現在価格(Ask)....${String.format("%,.3f",ask(symbol))}")
                }
            }

        }

        return false
    }

}