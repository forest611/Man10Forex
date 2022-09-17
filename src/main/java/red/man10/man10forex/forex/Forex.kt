package red.man10.man10forex.forex

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.forex.Forex.Job
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import red.man10.man10forex.util.Utility
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.floor

object Forex {

    const val prefix = "§f§l[§c§lM§a§lForex§f§l]"

    private var leverage = 100
    var minLot : Double = 0.01
    var maxLot : Double = 1000.0
    var lossCutPercent : Double = 20.0
    var unitSize : Int = 100000
    var spread : Double = 0.02  //スプレッド(Price)

    private val jobQueue = ArrayBlockingQueue<Job>(32768,true)
    private var positionThread = Thread{ positionThread() }
    private var queueThread = Thread{ queueThread() }


    fun loadConfig(){
        plugin.reloadConfig()

        leverage = plugin.config.getInt("Leverage")
        minLot = plugin.config.getDouble("MinLot")
        maxLot = plugin.config.getDouble("MaxLot")
        lossCutPercent = plugin.config.getDouble("LossCutPercent")
        spread = pipsToPrice(plugin.config.getDouble("SpreadPips"))
        unitSize = plugin.config.getInt("UnitSize")
        Price.url = plugin.config.getString("PriceURL","http://taro:824/api/price")?:"http://taro:824/api/price"

    }

    private fun asyncExit(uuid: UUID, pos:UUID, isLossCut:Boolean, sql: MySQLManager, exitPrice: Double? = null){

        val list = asyncGetUserPositions(uuid, sql)
        var position: Position? = null

        list.forEach {
            if (it.positionID == pos){
                position = it
                return@forEach
            }
        }

        if (position==null)return

        val price = exitPrice ?: if (position!!.buy) Price.bid() else Price.ask()
        val profit = profit(position!!)

        if (profit>0){
            ForexBank.deposit(uuid,profit, "ForexProfit","FX利益")
        }

        if (profit<0){
            val msg = if (isLossCut) "強制ロスカット" else "FXの損失"

            if (!ForexBank.withdraw(uuid,-profit, "ForexLoss",msg)){
                //ゼロカット
                val bal = ForexBank.getBalance(uuid)
                ForexBank.withdraw(uuid,bal,"ZeroCUT","ゼロカット")
            }
        }

        sql.execute("UPDATE position_table SET `exit` = 1, exit_price = ${price}, profit = ${profit}, exit_date = now() WHERE position_id = '${position!!.positionID}';")

        //オンラインだったらメッセージを送る
        val p = Bukkit.getOfflinePlayer(uuid).player?:return
        p.sendMessage("${prefix}ポジションを決済しました！(損益:${Utility.moneyFormat(profit)})")

    }

    fun asyncGetUserPositions(uuid:UUID, sql:MySQLManager): MutableList<Position> {

        val list = mutableListOf<Position>()

        val rs = sql.query("select * from position_table where uuid='${uuid}' and `exit`=0;")

        if (rs == null){
            Bukkit.getLogger().info("ERROR: Cant get Positions")
            return  list
        }

        while (rs.next()){

            val p = Position(
                UUID.fromString(rs.getString("position_id")),
                UUID.fromString(rs.getString("uuid")),
                rs.getDouble("lots"),
                rs.getDouble("entry_price"),
                rs.getInt("buy")==1,
                rs.getInt("sell")==1,
                rs.getDouble("sl_price"),
                rs.getDouble("tp_price")
            )

            list.add(p)
        }

        rs.close()
        sql.close()

        return list
    }

    fun entry(p:Player,lots: Double,isBuy:Boolean){

        val job = Job {sql->

            val id = UUID.randomUUID()
            val price = if (isBuy) Price.ask() else Price.bid()

            val maxLots = maxLots(p.uniqueId,price,sql)

            //ロット数が証拠金より多い場合
            if (lots> maxLots){
                if (lots< minLot){
                    p.sendMessage("${prefix}残高があまりにも少ないため、エントリーができません")
                    return@Job
                }

                p.sendMessage("${prefix}あなたがエントリーできる最大ロット数は${Utility.format(maxLots,2)}までです！")
                return@Job
            }

            //新規ポジションを定義
            val position = Position(id,p.uniqueId,lots,price,isBuy,!isBuy,0.0,0.0)

            sql.execute("INSERT INTO position_table (position_id, player, uuid, lots, buy, sell, `exit`, entry_price, exit_price, profit, entry_date, exit_date) " +
                    "VALUES ('" +
                    "${position.positionID}', " +
                    "'${p.name}', " +
                    "'${p.uniqueId}', " +
                    "${position.lots}, " +
                    "${if (position.buy) 1 else 0}, " +
                    "${if (position.sell) 1 else 0}, " +
                    "DEFAULT, " +
                    "${position.entryPrice}, " +
                    "null, " +
                    "null, " +
                    "DEFAULT, " +
                    "null);")

            p.sendMessage("${prefix}エントリーを受け付けました！価格:§d§l${Utility.priceFormat(price)}")
        }

        jobQueue.add(job)
    }

    fun exit(uuid: UUID,pos:UUID,isLossCut:Boolean,exitPrice: Double? = null){
        jobQueue.add(Job {asyncExit(uuid, pos, isLossCut, it, exitPrice) })
    }

    fun setTP(p:Player,pos:UUID,tp: Double){

        val job = Job {sql->
            val list = asyncGetUserPositions(p.uniqueId, sql)
            var position : Position? = null

            list.forEach {
                if (it.positionID == pos){
                    position = it
                    return@forEach
                }
            }

            if (position == null)return@Job

            if (position!!.buy){
                val bid = Price.bid()
                //tpが現在値より低い時は未設定に
                position!!.tp = if (bid>tp){ 0.0 } else tp

                if (position!!.tp == 0.0){
                    p.sendMessage("${prefix}TPは現在価格(${Utility.priceFormat(bid)})より高く設定してください")
                }else{
                    p.sendMessage("${prefix}設定完了")
                }
            }

            if (position!!.sell){
                val ask = Price.ask()
                position!!.tp = if (ask<tp){ 0.0 } else tp

                if (position!!.tp == 0.0){
                    p.sendMessage("${prefix}TPは現在価格(${Utility.priceFormat(ask)})より低く設定してください")
                }else{
                    p.sendMessage("${prefix}設定完了")
                }
            }

            sql.execute("UPDATE position_table SET tp_price = ${position!!.tp} WHERE position_id ='${pos}'")
        }

        jobQueue.add(job)
    }


    fun setSL(p:Player,pos:UUID,sl: Double){

        val job = Job {sql->
            val list = asyncGetUserPositions(p.uniqueId, sql)
            var position : Position? = null

            list.forEach {
                if (it.positionID == pos){
                    position = it
                    return@forEach
                }
            }

            if (position == null)return@Job

            if (position!!.buy){
                val bid = Price.bid()
                //slが現在値より高い時は未設定に
                position!!.sl = if (bid<sl){ 0.0 } else sl

                if (position!!.sl == 0.0){
                    p.sendMessage("${prefix}SLは現在価格(${Utility.priceFormat(bid)})より低く設定してください")
                }else{
                    p.sendMessage("${prefix}設定完了")
                }
            }

            if (position!!.sell){
                val ask = Price.ask()
                position!!.sl = if (ask>sl){ 0.0 } else sl

                if (position!!.sl == 0.0){
                    p.sendMessage("${prefix}SLは現在価格(${Utility.priceFormat(ask)})より高く設定してください")
                }else{
                    p.sendMessage("${prefix}設定完了")
                }
            }

            sql.execute("UPDATE position_table SET sl_price = ${position!!.sl} WHERE position_id ='${pos}'")
        }

        jobQueue.add(job)
    }

    fun profit(position:Position): Double {

        if (position.buy){
            val bid = Price.bid()
            val entryMoney = lotsToMan10Money(position.lots,position.entryPrice)
            val nowMoney = lotsToMan10Money(position.lots,bid)
            return nowMoney-entryMoney
        }

        if (position.sell){
            val ask = Price.ask()
            val entryMoney = lotsToMan10Money(position.lots,position.entryPrice)
            val nowMoney = lotsToMan10Money(position.lots,ask)
            return entryMoney-nowMoney
        }

        return 0.0
    }


    //ポジションの現在価格との差(Pips)
    fun diffPips(position: Position):Double{
        if (position.buy){
            val bid = Price.bid()
            return priceToPips(bid-position.entryPrice)
        }

        if (position.sell){
            val ask = Price.ask()
            return priceToPips(position.entryPrice-ask)
        }

        return 0.0
    }

    //全ポジの含み益
    fun allProfit(list:List<Position>):Double {
        var profit = 0.0
        list.forEach { profit+= profit(it) }

        return profit
    }

    //必要証拠金
    fun marginRequirement(list:List<Position>):Double{
        var margin = 0.0
        val price = Price.price()
        list.forEach { margin+= it.lots* unitSize/ leverage*price }
        return margin
    }

    //有効証拠金
    fun margin(uuid: UUID,list: List<Position>):Double{
        val bal = ForexBank.getBalance(uuid)
        val ret = bal + allProfit(list)
        return if (ret<0.0) 0.0 else ret
    }

    //ロスカットラインに入っているか
    private fun checkLossCut(uuid: UUID){

        val job = Job{sql ->
            val p = Bukkit.getOfflinePlayer(uuid)

            var list = asyncGetUserPositions(uuid,sql)

            if (list.isEmpty())return@Job

            //有効証拠金
            var margin = margin(uuid,list)

            //必要証拠金
            var require = marginRequirement(list)

            //証拠金維持率
            var percent = if (require==0.0) return@Job else margin/require*100.0

            while (percent< lossCutPercent) {

                //損失の大きい順にExit
                val exitPos = list.minByOrNull { profit(it) } ?: return@Job

                asyncExit(uuid, exitPos.positionID, true, sql)

                p.player?.sendMessage("${prefix}§4§l損失が激しいため強制ロスカットを行いました！")
                Bukkit.getLogger().info("LOSS CUT ${percent}%")

                list = asyncGetUserPositions(uuid, sql)

                if (list.isEmpty()) return@Job

                margin = margin(uuid, list)
                require = marginRequirement(list)
                percent = if (require == 0.0) return@Job else margin / require * 100.0
            }

        }

        jobQueue.add(job)
    }

    private fun checkTouchTPSL(uuid: UUID){

        val job = Job{sql ->
            val list = asyncGetUserPositions(uuid, sql)

            list.forEach {
                if (it.buy){
                    val bid = Price.bid()

                    if (it.tp!= 0.0 && bid>it.tp){
                        asyncExit(uuid,it.positionID,false,sql,it.tp)
                    }

                    if (it.sl!= 0.0 && bid<it.sl){
                        asyncExit(uuid,it.positionID,false,sql,it.sl)
                    }
                }

                if (it.sell){
                    val ask = Price.ask()

                    if (it.tp!= 0.0 && ask<it.tp){
                        asyncExit(uuid,it.positionID,false,sql,it.tp)
                    }

                    if (it.sl!= 0.0 && ask>it.sl){
                        asyncExit(uuid,it.positionID,false,sql,it.sl)
                    }
                }
            }
        }

        jobQueue.add(job)
    }


    //持てる最大ロットを取得(少数第三以下は切り捨て
    private fun maxLots(uuid: UUID, price: Double,sql: MySQLManager):Double{
        val margin = margin(uuid, asyncGetUserPositions(uuid,sql))
        return floor(margin * leverage  /(price* unitSize)*100)/100.0
    }

    private fun lotsToMan10Money(lots:Double, price: Double):Double{
        return floor(price*lots* unitSize)
    }

    //ドル円のみ対応
    private fun priceToPips(price: Double): Double {
        return price*100.0
    }

    private fun pipsToPrice(pips:Double):Double{
        return pips/100.0
    }


    //####################################管理スレッド##################################

    fun runThread(){

        if (positionThread.isAlive){
            positionThread.interrupt()
            positionThread = Thread{ positionThread() }
        }

        if (queueThread.isAlive){
            queueThread.interrupt()
            queueThread = Thread{ queueThread() }
        }

        positionThread.start()
        queueThread.start()
    }

    fun stopThread() {
        if (positionThread.isAlive){
            positionThread.interrupt()
        }

        if (queueThread.isAlive){
            queueThread.interrupt()
        }
    }

    //ロスカット処理などを行う
    private fun positionThread(){

        Bukkit.getLogger().info("StartPositionThread")
        val positionMysql = MySQLManager(plugin,"PositionThread")

        while (true){

            try {
                Thread.sleep(1000)
            }catch (e:InterruptedException){
                Bukkit.getLogger().info("Interrupt Position Thread")
                break
            }

            if (!Price.isActiveTime())continue

            val rs = positionMysql.query("select uuid from position_table where `exit`=0 group by uuid;")

            if (rs==null){
                Bukkit.getLogger().info("rs=null Error")
                continue
            }

            while (rs.next()){
                val uuid = UUID.fromString(rs.getString("uuid"))

                checkTouchTPSL(uuid)
                checkLossCut(uuid)
            }

            rs.close()
            positionMysql.close()
        }
    }

    //ポジションを管理するスレッド
    private fun queueThread(){

        Bukkit.getLogger().info("QueueThread")
        val queueMysql = MySQLManager(plugin,"Man10Forex")

        while (true){
            try {
                val job = jobQueue.take()
                job.job(queueMysql)
            }catch (e:InterruptedException){
                Bukkit.getLogger().info("Interrupt Job Thread")
                break
            } catch (e:Exception){
                Bukkit.getLogger().info(e.message)
            }
        }
    }

    fun showQueueStatus(p:Player){
        p.sendMessage("${prefix}QueueSize:${jobQueue.size}")
        p.sendMessage("${prefix}Alive:${queueThread.isAlive}")
        p.sendMessage("${prefix}State:${queueThread.state.name}")
        p.sendMessage("${prefix}ToString:${queueThread}")
        p.sendMessage("${prefix}StackTrace:${queueThread.stackTrace.size}")
        queueThread.stackTrace.forEach {
            p.sendMessage("${prefix}${it.methodName}(${it.lineNumber})")
        }

        ForexBank.showThreadStatus(p)
    }


    private fun interface Job{
        fun job(sql:MySQLManager)
    }


    data class Position(
        val positionID:UUID,
        val uuid: UUID,
        val lots:Double,
        val entryPrice:Double,
        val buy:Boolean,
        val sell:Boolean,
        var sl:Double,
        var tp:Double
    )

    object MarketStatus{
        var entry = true
        var exit = true
        var deposit = true
        var withdraw = true
    }

}