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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object Forex {

    const val prefix = "§f§l[§c§lM§a§lForex§f§l]"
    private val jobQueue = ArrayBlockingQueue<Job>(32768,true)
    private var positionThread = Thread{ positionThread() }
    private var queueThread = Thread{ queueThread() }

    var lossCutPercent : Double = 20.0
    var overallMaxLot : Double = 1000.0//全体での最大ロット

    var symbols = ConcurrentHashMap<String,Symbol>()
    var symbolList = mutableListOf<String>()


    ///////////////////////////////////////////
    //      シンボルデータ
    ///////////////////////////////////////////
    class Symbol(private val symbol:String){

        var leverage = 100
        var minLot : Double = 0.01
        var maxLot : Double = 1000.0
        var contractSize : Int = 100000
        var spread : Double = 2.0  //スプレッド(Pips)
        var pipsAmount : Int = 100 //Priceにいくつ書けるとPipsになるか
        var slippageAmount : Double = 0.0005 //ロット数x指定係数をスプレッドpipsとして上乗せ

        init {
            loadConfig()
        }

        //////////////////////
        //      シンボルのデータを読む
        private fun loadConfig(){
            plugin.reloadConfig()

            if (plugin.config.get(symbol) == null){
                plugin.config.set("${symbol}.Leverage",leverage)
                plugin.config.set("${symbol}.MinLot",minLot)
                plugin.config.set("${symbol}.MaxLot",maxLot)
                plugin.config.set("${symbol}.PipsAmount",pipsAmount)
                plugin.config.set("${symbol}.SpreadPips",spread)
                plugin.config.set("${symbol}.ContractSize",contractSize)
                plugin.config.set("${symbol}.SlippageAmount",slippageAmount)

                plugin.saveConfig()

                Bukkit.getLogger().info("${symbol}の初期化をしました")
                return
            }

            leverage = plugin.config.getInt("${symbol}.Leverage")
            minLot = plugin.config.getDouble("${symbol}.MinLot")
            maxLot = plugin.config.getDouble("${symbol}.MaxLot")
            pipsAmount = plugin.config.getInt("${symbol}.PipsAmount")
            spread = plugin.config.getDouble("${symbol}.SpreadPips")
            contractSize = plugin.config.getInt("${symbol}.ContractSize")
            slippageAmount = plugin.config.getDouble("${symbol}.SlippageAmount")

            Bukkit.getLogger().info("==========${symbol}==========")
            Bukkit.getLogger().info("Leverage:${leverage}")
            Bukkit.getLogger().info("MinLot:${minLot}")
            Bukkit.getLogger().info("MaxLot:${maxLot}")
            Bukkit.getLogger().info("PipsAmount:${pipsAmount}")
            Bukkit.getLogger().info("SpreadPips:${spread}")
            Bukkit.getLogger().info("ContractSize:${contractSize}")
            Bukkit.getLogger().info("SlippageAmount:${slippageAmount}")
        }
    }

    //static

    init {
        reload()
    }

    fun reload(){

        loadConfig()

        symbols.clear()

        Thread{
            Thread.sleep(1000)

            for (symbol in symbolList){
                symbols[symbol] = Symbol(symbol)
            }
            Price.startPriceThread()
            runThread()
        }.start()
    }

    /////////////////////////////////
    //      FX関連のデータを読む
    //////////////////////////////////
    fun loadConfig(){
        plugin.reloadConfig()

        symbolList = plugin.config.getStringList("SymbolList")//使う銘柄のリスト
        lossCutPercent = plugin.config.getDouble("LossCutPercent")
        overallMaxLot = plugin.config.getDouble("OverAllMaxLot")

        Price.url = plugin.config.getString("PriceURL")?:""
        Price.errorSecond = plugin.config.getInt("ErrorSecond")
        Price.threadInterval = plugin.config.getInt("ThreadInterval")

        MarketStatus.entry = plugin.config.getBoolean("Status.Entry")
        MarketStatus.exit = plugin.config.getBoolean("Status.Exit")
        MarketStatus.withdraw = plugin.config.getBoolean("Status.Withdraw")
        MarketStatus.deposit = plugin.config.getBoolean("Status.Deposit")
        MarketStatus.tpsl = plugin.config.getBoolean("Status.TPSL")
        MarketStatus.lossCut = plugin.config.getBoolean("Status.LossCut")
    }

    fun setStatus(){

        plugin.config.set("Status.Entry",MarketStatus.entry)
        plugin.config.set("Status.Exit",MarketStatus.exit)
        plugin.config.set("Status.Withdraw",MarketStatus.withdraw)
        plugin.config.set("Status.Deposit",MarketStatus.deposit)
        plugin.config.set("Status.TPSL",MarketStatus.tpsl)
        plugin.config.set("Status.LossCut",MarketStatus.lossCut)

        plugin.saveConfig()
    }

    fun entry(p:Player,lots: Double,isBuy:Boolean,symbol: String){

        val job = Job {sql->

            val id = UUID.randomUUID()
            var price = if (isBuy) Price.ask(symbol) else Price.bid(symbol)

            val data = symbols[symbol]

            if (data==null){
                p.sendMessage("${prefix}存在しない銘柄です")
                return@Job
            }

            val slippagePrice = lots*data.slippageAmount

            Bukkit.getLogger().info("ロット数:${lots} 発生スリッページPips:${slippagePrice*data.pipsAmount}")

            if (slippagePrice*data.pipsAmount>1){
                if (isBuy) price+=slippagePrice else price-=slippagePrice
            }

            val positions = asyncGetUserPositions(p.uniqueId,sql)
            val maxLots = getMaxLots(p.uniqueId,price,positions,symbol)


            //同時保有ロットの制限追加
            if ((positions.sumOf { it.lots } + lots) > overallMaxLot){
                p.sendMessage("${prefix}同時に保有できるロット数は${overallMaxLot}ロットまでです！")
                return@Job
            }

            //ロット数が証拠金より多い場合
            if (lots> maxLots){
                if (lots< data.minLot){
                    p.sendMessage("${prefix}残高があまりにも少ないため、エントリーができません")
                    return@Job
                }

                p.sendMessage("${prefix}あなたがエントリーできる最大ロット数は${Utility.format(maxLots,2)}までです！")
                return@Job
            }

            //新規ポジションを定義
            val position = Position(id,p.uniqueId,lots,symbol,price,isBuy,!isBuy,0.0,0.0)

            sql.execute("INSERT INTO position_table (position_id, player, uuid, lots, symbol, buy, sell, `exit`, entry_price, exit_price, profit, entry_date, exit_date) " +
                    "VALUES ('" +
                    "${position.positionID}', " +
                    "'${p.name}', " +
                    "'${p.uniqueId}', " +
                    "${position.lots}, " +
                    "'${symbol}', " +
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

    //ポジごとの含み損益を見る
    fun profit(position:Position,price: Double?=null): Double {

        val symbol = position.symbol

        if (position.buy) {
            val bid = price ?: Price.bid(symbol)
            val entryMoney = lotsToMan10Money(position.lots, position.entryPrice, symbol)
            val nowMoney = lotsToMan10Money(position.lots, bid, symbol)
            return  nowMoney - entryMoney
        }

        if (position.sell){
            val ask = price?:Price.ask(symbol)
            val entryMoney = lotsToMan10Money(position.lots,position.entryPrice,symbol)
            val nowMoney = lotsToMan10Money(position.lots,ask,symbol)
            return entryMoney-nowMoney
        }

        return 0.0
    }


    //ポジションの現在価格との差(Pips)
    fun diffPips(position: Position):Double{

        val symbol = position.symbol

        if (position.buy){
            val bid = Price.bid(symbol)
            return priceToPips(bid-position.entryPrice,symbol)
        }

        if (position.sell){
            val ask = Price.ask(symbol)
            return priceToPips(position.entryPrice-ask,symbol)
        }

        return 0.0
    }

    //持てる最大ロットを取得(少数第三以下は切り捨て
    fun getMaxLots(uuid: UUID, price: Double, list: MutableList<Position>, symbol: String):Double{

        val isCrossJPY = Price.isCrossJPY(symbol)
        val margin = if (isCrossJPY) margin(uuid, list) else margin(uuid, list)/Price.price("USDJPY")
        val data = symbols[symbol]!!
        return floor(margin * data.leverage  /(price* data.contractSize)*100)/100.0
    }

    private fun lotsToMan10Money(lots:Double, price: Double,symbol: String):Double{
        val isCrossJPY = Price.isCrossJPY(symbol)
        val data = symbols[symbol]!!
        val money = if (isCrossJPY) floor(price*lots* data.contractSize) else floor(price*lots* data.contractSize*Price.price("USDJPY"))
        return money
    }

    private fun priceToPips(price: Double,symbol: String): Double {
        val data = symbols[symbol]!!
        return price*data.pipsAmount
    }

    fun pipsToPrice(pips:Double,symbol: String):Double{
        val data = symbols[symbol]!!
        return pips/data.pipsAmount
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
                val bid = Price.bid(position!!.symbol)
                //tpが現在値より低い時は未設定に
                position!!.tp = if (bid>tp){ 0.0 } else tp

                if (position!!.tp == 0.0){
                    p.sendMessage("${prefix}TPは現在価格(${Utility.priceFormat(bid)})より高く設定してください")
                }else{
                    p.sendMessage("${prefix}設定完了")
                }
            }

            if (position!!.sell){
                val ask = Price.ask(position!!.symbol)
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
                val bid = Price.bid(position!!.symbol)
                //slが現在値より高い時は未設定に
                position!!.sl = if (bid<sl){ 0.0 } else sl

                if (position!!.sl == 0.0){
                    p.sendMessage("${prefix}SLは現在価格(${Utility.priceFormat(bid)})より低く設定してください")
                }else{
                    p.sendMessage("${prefix}設定完了")
                }
            }

            if (position!!.sell){
                val ask = Price.ask(position!!.symbol)
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

    fun exit(uuid: UUID,pos:UUID,isLossCut:Boolean,exitPrice: Double? = null){
        jobQueue.add(Job {asyncExit(uuid, pos, isLossCut, it, exitPrice) })
    }

    //現在価格で全部Exitする
    fun exitAll(uuid:UUID){

        jobQueue.add{sql ->
            val pos = asyncGetUserPositions(uuid,sql)

            pos.forEach { asyncExit(uuid,it.positionID,false,sql) }
        }
    }
    private fun asyncExit(uuid: UUID, pos:UUID, isLossCut:Boolean, sql: MySQLManager, exitPrice: Double? = null){

        //オンラインだったらメッセージを送る
        val p = Bukkit.getOfflinePlayer(uuid).player

        if (Price.error){
            p?.sendMessage("${prefix}§c§l決済失敗。価格取得ができないため決済ができません")
            return
        }

        val list = asyncGetUserPositions(uuid, sql)

        val position = list.firstOrNull { it.positionID == pos } ?: return

        val symbol = position.symbol

        val price = exitPrice ?: if (position.buy) Price.bid(symbol) else Price.ask(symbol)
        var profit = profit(position,price)

        if (profit>0){
            ForexBank.deposit(uuid,profit, "ForexProfit","FX利益")
        }

        if (profit<0){
            val msg = if (isLossCut) "強制ロスカット" else "FXの損失"

            if (!ForexBank.withdraw(uuid,-profit, "ForexLoss",msg)){
                //ゼロカット
                val bal = ForexBank.getBalance(uuid)
                //損益額をゼロカットした分に修正
                profit = bal*-1
                ForexBank.withdraw(uuid,bal,"ZeroCUT","ゼロカット")
            }
        }

        sql.execute("UPDATE position_table SET `exit` = 1, exit_price = ${price}, profit = ${profit}, exit_date = now() WHERE position_id = '${position.positionID}';")

        p?.sendMessage("${prefix}ポジションを決済しました！(損益:${Utility.moneyFormat(profit)})")
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
                rs.getString("symbol"),
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

    //全ポジの含み益
    fun allProfit(list:List<Position>):Double {
        var profit = 0.0
        list.forEach { profit +=  profit(it)}
        return profit
    }

    //必要証拠金
    private fun marginRequirement(list:List<Position>):Double{
        var margin = 0.0
        list.forEach {
            val contractSize = symbols[it.symbol]?.contractSize?:0
            val leverage = symbols[it.symbol]?.leverage?:0
            margin+= it.lots* contractSize/ leverage*it.entryPrice
        }
        return margin
    }

    //有効証拠金
    fun margin(uuid: UUID,list: List<Position>):Double{
        val bal = ForexBank.getBalance(uuid)
        val ret = bal + allProfit(list)
        return if (ret<0.0) 0.0 else ret
    }

    //証拠金維持率
    fun marginPercent(uuid: UUID,list: List<Position>):Double?{
        val margin = margin(uuid, list)
        val require = marginRequirement(list)
        return if (require==0.0) null else margin/require*100.0
    }

    //ロスカットラインに入っているか
    private fun checkLossCut(uuid: UUID){

        val job = Job{sql ->

            if (Price.error)return@Job

            val p = Bukkit.getOfflinePlayer(uuid)

            var list = asyncGetUserPositions(uuid,sql)

            if (list.isEmpty())return@Job

            //証拠金維持率
            var percent = marginPercent(uuid, list)?:return@Job

            while (percent< lossCutPercent) {

                //損失の大きい順にExit
                val exitPos = list.minByOrNull { profit(it) } ?: return@Job

                asyncExit(uuid, exitPos.positionID, true, sql)

                p.player?.sendMessage("${prefix}§4§l損失が激しいため強制ロスカットを行いました！")
                Bukkit.getLogger().info("LOSS CUT ${percent}%")

                list = asyncGetUserPositions(uuid, sql)

                if (list.isEmpty()) return@Job

                //証拠金維持率
                percent = marginPercent(uuid, list)?:return@Job
            }
        }

        jobQueue.add(job)
    }

    private fun checkTouchTPSL(uuid: UUID){

        val job = Job{sql ->

            if (Price.error)return@Job

            val list = asyncGetUserPositions(uuid, sql)

            list.forEach {
                if (it.buy){
                    val bid = Price.bid(it.symbol)

                    if (it.tp!= 0.0 && bid>it.tp){
                        asyncExit(uuid,it.positionID,false,sql,it.tp)
                    }

                    if (it.sl!= 0.0 && bid<it.sl){
                        asyncExit(uuid,it.positionID,false,sql,it.sl)
                    }
                }

                if (it.sell){
                    val ask = Price.ask(it.symbol)

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


    private fun interface Job{
        fun job(sql:MySQLManager)
    }


    data class Position(
        val positionID:UUID,
        val uuid: UUID,
        val lots:Double,
        val symbol: String,
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
        var tpsl = true
        var lossCut = true
    }

}