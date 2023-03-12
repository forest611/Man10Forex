package red.man10.man10forex.highlow

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price.price
import java.lang.Exception
import java.util.*

object HighLowGame {

    private val positionList = mutableListOf<Position>()
    const val prefix = "§f§l[§b§lハイ§d§l&§c§lロー§f§l]"
    private var closeRequest = false

    var minPrice = 50000.0
    var maxPrice = 500000.0
    var minSecond = 10
    var maxSecond = 60

    private var spread = 0.001

    private const val symbol = "USDJPY"

    var isEnableGame = true

    private var thread = Thread{ highLowThread() }

    init {
        startThread()
    }

    fun loadConfig(){
        Man10Forex.plugin.reloadConfig()

        minPrice = Man10Forex.plugin.config.getDouble("MinPrice")
        maxPrice = Man10Forex.plugin.config.getDouble("MaxPrice")
        minSecond = Man10Forex.plugin.config.getInt("MinSecond")
        maxSecond = Man10Forex.plugin.config.getInt("MaxSecond")
        spread = Man10Forex.plugin.config.getDouble("HighLowSpread")
    }

    //全ポジ閉じる
    fun closeAll(){
        closeRequest = true
    }

    //返金
    fun payback(data:Position){
        val p = Bukkit.getOfflinePlayer(data.uuid)
        bank.deposit(data.uuid,data.betAmount,"Payback","ハイ&ロー返金")
        p.player?.sendMessage("${prefix}払い戻し処理が行われました")

    }

    fun checkNowEntry(p:Player):Boolean{
        return positionList.any { it.uuid == p.uniqueId }
    }

    fun entry(p:Player,betAmount: Double,exitSecond: Int,isHigh: Boolean){
        val position = Position(p.uniqueId,betAmount,0.0,isHigh,exitSecond, Date(), Date())
        positionList.add(position)
    }

    private fun exit(position:Position){

        val p = Bukkit.getOfflinePlayer(position.uuid)
        var price = price(symbol)

        val isWin = if (position.isHigh){
            price>position.entryPrice
        } else {
            price < position.entryPrice
        }

        if (p.isOnline){

            val predict = if (position.isHigh) "§a§l上" else "§c§l下"
            val resultString = if (isWin)"§c§l勝ち！" else "§b§l負け！"

            val diff = price - position.entryPrice
            val format = (if (diff>0) "§a§l(↑" else if (diff<0) "§c§l(↓" else "§f§l(→") + String.format("%,.3f",diff) + ")"

            p.player!!.sendMessage("${prefix}${position.exitSecond}秒経過！")
            p.player!!.sendMessage("${prefix}現在...§d§l${String.format("%,.3f",price)} $format")
            p.player!!.sendMessage("${prefix}予想.......${predict}")
            Thread.sleep(1000)
            p.player!!.sendMessage("${prefix}判定判定....${resultString}")

        }

        if (isWin){
            bank.deposit(position.uuid,position.betAmount*2,"HighLowPayout","ハイ&ローオプション払い戻し")
            MySQLManager.mysqlQueue.add("INSERT INTO log (player, uuid, bet, payout, date) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', ${position.betAmount}, ${position.betAmount*2}, DEFAULT)")
            return
        }

        MySQLManager.mysqlQueue.add("INSERT INTO log (player, uuid, bet, payout, date) " +
                "VALUES ('${p.name}', '${p.uniqueId}', ${position.betAmount}, 0, DEFAULT)")

    }

    fun startThread(){

        if (thread.isAlive){
            thread.interrupt()
            thread = Thread{ highLowThread() }
        }

        thread.start()


    }

    private fun highLowThread(){

        while (true){

            try {

                //払い戻し処理
                if (closeRequest){
                    positionList.forEach {
                        payback(it)
                        positionList.remove(it)
                    }
                    closeRequest = false
                }

                //最新価格取得
                val price = price(symbol)

                positionList.forEach {

                    val p = Bukkit.getOfflinePlayer(it.uuid)
                    var finishFlag = false

                    //エントリー価格決定
                    if (it.entryPrice == 0.0){

                        it.entryPrice = when(it.isHigh){
                            true-> price+ spread
                            else -> price- spread
                        }
                    }

                    //Exit時間経過
                    if ((Date().time-it.entryTime.time)>1000*it.exitSecond){
                        Thread{ exit(it) }.start()
                        finishFlag = true
                        positionList.remove(it)
                    }

                    //1秒経過
                    if (!finishFlag && (Date().time-it.timer.time)>1000){

                        val diff = price - it.entryPrice

                        val isWin = if (price>it.entryPrice && it.isHigh){ true }else price<it.entryPrice && !it.isHigh

                        val format = (if (diff>0) "§a§l(↑" else if (diff<0) "§c§l(↓" else "§f§l(→") + String.format("%,.3f",diff) + ")"

                        val payoutFormat = "${if (isWin) "§b§l獲得予定:" else "§c§l獲得予定:"}${String.format("%,.0f",if (isWin) it.betAmount*2 else 0.0)}"

                        p.player?.sendMessage("${prefix}現在...§d§l${String.format("%,.3f",price)} $format $payoutFormat")

                        it.timer = Date()

                    }
                }

                Thread.sleep(200)

            }catch (e:InterruptedException){
                Bukkit.getLogger().info("ReloadHighLow")
                break
            } catch (e:Exception){
                Bukkit.getLogger().info(e.message)
            }

        }
    }


    data class Position(
        val uuid: UUID,
        val betAmount: Double,
        var entryPrice: Double,
        val isHigh:Boolean,
        val exitSecond:Int,
        val entryTime:Date,
        var timer:Date

    )
}