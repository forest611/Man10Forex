package red.man10.man10forex.util

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex
import red.man10.man10forex.forex.Forex
import red.man10.man10forex.map.MappRenderer
import red.man10.man10forex.util.Utility.format
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object Price : CommandExecutor{

    var url = ""

    private val gson = Gson()

    private val symbolMap = ConcurrentHashMap<String,PriceData>()
    private val notifyPlayer = ConcurrentHashMap<String,MutableList<Player>>()

    private var lastGotDate = Date()
    private var dateStr = ""

    private var priceThread = Thread{asyncGetPriceThread()}

    var error = true
    var threadInterval = 100
    var errorSecond = 60 //n秒以上失敗したらエラー扱い

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
        return symbolMap[symbol]?.price?:-1.0
    }

    fun bid(symbol: String): Double {
        return symbolMap[symbol]?.bid?:-1.0
    }

    fun ask(symbol: String): Double {
        return symbolMap[symbol]?.ask?:-1.0
    }

    fun isCrossJPY(symbol: String):Boolean{
        return symbol.contains("JPY")
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

    fun startPriceThread(){

        if (priceThread.isAlive){
            priceThread.interrupt()
        }
        priceThread = Thread{asyncGetPriceThread()}
        priceThread.start()

    }
    private fun asyncGetPriceThread(){

        val client = OkHttpClient.Builder().cache(null).build()

        Main@while (true){

            try {
                Thread.sleep(threadInterval.toLong())

                if (error){
                    Bukkit.getLogger().info("ConnectError")
                    client.connectionPool.evictAll()
                }

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()?.replace("/","")

                //レスポンスが返ってこなかったら止める
                if (body == null){
                    error = true
                    continue@Main
                }

                val jsonObj = gson.fromJson(body,Array<DeserializedData>::class.java)

                if (jsonObj == null){
                    error = true
                    continue@Main
                }

                for (obj in jsonObj){
                    val symbol = obj.symbol

                    //最終取得の日付が現在と異なっていたら日付更新
                    if (symbol == Forex.symbolList[0] && dateStr != obj.time){
                        dateStr = obj.time
                        lastGotDate = Date()
                    }

                    //指定秒数超えたらエラー扱い
                    if (Date().time - lastGotDate.time> errorSecond*1000){
                        error = true
                        Bukkit.getLogger().warning("Oandaサーバーの不具合が起きている可能性があります")
                        continue@Main
                    }

                    val symbolSetting = Forex.symbols[symbol]?:continue

                    val price = (obj.bid+obj.ask)/2.0
                    val ask = price+(Forex.pipsToPrice(symbolSetting.spread,symbol)/2.0)
                    val bid = price-(Forex.pipsToPrice(symbolSetting.spread,symbol)/2.0)

                    val data = PriceData(symbol,bid,ask,price)

                    val lastData = symbolMap[symbol]

                    symbolMap[symbol] = data

                    if (lastData!=null && lastData.price!=price){
                        notifyChangePrice(symbol,lastData.price,price)
                    }
                }

                error = false
            }catch (e:InterruptedException){
                Bukkit.getLogger().info("PriceThreadInterrupt")
                break@Main
            } catch (e:java.lang.Exception){
                error = true
            }
        }

    }

    private fun notifyChangePrice(symbol: String,last:Double,now:Double){

        val color = if (now>last) "§b" else "§c"
        val lastString = format(last,3)
        val nowString = format(now,3)

        for (p in notifyPlayer[symbol]?:return){
            p.sendMessage("${color}${symbol} ${lastString}→${nowString}")
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

            "notify" ->{

                if (sender !is Player)return true

                val list = notifyPlayer[symbol]?: mutableListOf()

                if (list.contains(sender)){
                    list.remove(sender)
                    sender.sendMessage("§a価格変更通知をオフにしました")
                }else{
                    list.add(sender)
                    sender.sendMessage("§a価格変更通知をオンにしました")
                }
                notifyPlayer[symbol] = list
            }

            "price" ->{

                if (symbol=="all"){
                    symbolMap.keys.forEach { sender.sendMessage("§d§l現在価格(${it})....§f§l${String.format("%,.3f",price(it))}") }
                }else{
                    sender.sendMessage("§d§l現在価格....§f§l${String.format("%,.3f",price(symbol))}")
                }
            }

            "bid" ->{
                if (symbol=="all"){
                    symbolMap.keys.forEach { sender.sendMessage("§c§l現在価格(Bid)(${it})....${String.format("%,.3f",bid(it))}") }
                }else{
                    sender.sendMessage("§c§l現在価格(Bid)....${String.format("%,.3f",bid(symbol))}")
                }
            }

            "ask" ->{
                if (symbol=="all"){
                    symbolMap.keys.forEach { sender.sendMessage("§b§l現在価格(Ask)(${it})....${String.format("%,.3f",ask(it))}") }
                }else{
                    sender.sendMessage("§b§l現在価格(Ask)....${String.format("%,.3f",ask(symbol))}")
                }
            }

            "map" ->{
                if (sender.hasPermission(Man10Forex.OP)){
                    val map = MappRenderer.getMapItem(Man10Forex.plugin,"PriceGUI")
                    if (sender !is Player)return false
                    sender.inventory.addItem(map)
                }
            }

        }

        return false
    }

}