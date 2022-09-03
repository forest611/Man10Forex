package red.man10.man10forex.forex

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import java.util.*

object Forex {

    const val prefix = "§f§l[§c§lM§a§lForex§f§l]"

    private var leverage = 100
    var minLot : Double = 0.01
    var maxLot : Double = 1000.0
    var lossCutPercent : Double = 20.0
    var unitSize : Int = 100000
    var spread : Double = 0.02  //スプレッド(Price)

    var isEnable = true

    private val mysql:MySQLManager = MySQLManager(plugin,"Man10Forex")

    fun loadConfig(){
        plugin.reloadConfig()

        leverage = plugin.config.getInt("Leverage")
        minLot = plugin.config.getDouble("MinLot")
        maxLot = plugin.config.getDouble("MaxLot")
        lossCutPercent = plugin.config.getDouble("LossCutPercent")
        spread = pipsToPrice(plugin.config.getDouble("SpreadPips"))
    }

    @Synchronized
    private fun closePosition(id: UUID,price: Double,profit:Double){
        mysql.execute("UPDATE position_table SET `exit` = 1, exit_price = ${price}, profit = ${profit}, exit_date = now() WHERE position_id = '${id}';")
    }

    private fun createPosition(position: Position){
        val p = Bukkit.getOfflinePlayer(position.uuid).name

        MySQLManager.mysqlQueue.add("INSERT INTO position_table (position_id, player, uuid, lots, buy, sell, `exit`, entry_price, exit_price, profit, entry_date, exit_date) " +
                "VALUES ('" +
                "${position.positionID}', " +
                "'${p}', " +
                "'${position.uuid}', " +
                "${position.lots}, " +
                "${if (position.buy) 1 else 0}, " +
                "${if (position.sell) 1 else 0}, " +
                "DEFAULT, " +
                "${position.entryPrice}, " +
                "null, " +
                "null, " +
                "DEFAULT, " +
                "null);")
    }


    fun setTP(p:Player,pos:UUID,tp: Double){

        val list = getUserPositions(p.uniqueId)
        var position : Position? = null

        list.forEach {
            if (it.positionID == pos){
                position = it
                return@forEach
            }
        }

        if (position == null)return

        if (position!!.buy){
            val bid = Price.bid()
            //tpが現在値より低い時は未設定に
            position!!.tp = if (bid>tp){ 0.0 } else tp
        }

        if (position!!.sell){
            val ask = Price.ask()
            position!!.tp = if (ask<tp){ 0.0 } else tp
        }

        if (position!!.tp == 0.0){
            p.sendMessage("${prefix}TPの価格に問題があります")
        }else{
            p.sendMessage("${prefix}設定完了")
        }

        MySQLManager.mysqlQueue.add("UPDATE position_table SET tp_price = ${position!!.tp} WHERE position_id ='${pos}'")
    }


    fun setSL(p:Player,pos:UUID,sl: Double){

        val list = getUserPositions(p.uniqueId)
        var position : Position? = null

        list.forEach {
            if (it.positionID == pos){
                position = it
                return@forEach
            }
        }

        if (position == null)return

        if (position!!.buy){
            val bid = Price.bid()
            //slが現在値より高い時は未設定に
            position!!.sl = if (bid<sl){ 0.0 } else sl
        }

        if (position!!.sell){
            val ask = Price.ask()
            position!!.tp = if (ask>sl){ 0.0 } else sl
        }

        if (position!!.sl == 0.0){
            p.sendMessage("${prefix}TPの価格に問題があります")
        }else{
            p.sendMessage("${prefix}設定完了")
        }


        MySQLManager.mysqlQueue.add("UPDATE position_table SET sl_price = ${position!!.sl} WHERE position_id ='${pos}'")
    }

    fun checkTouchTPSL(position: Position){

        if (position.buy){
            val bid = Price.bid()

            if (position.tp!= 0.0 && bid>position.tp){
                exit(position,false,position.tp)
            }

            if (position.sl!= 0.0 && bid<position.sl){
                exit(position,false,position.sl)
            }
        }

        if (position.sell){
            val ask = Price.ask()

            if (position.tp!= 0.0 && ask<position.tp){
                exit(position,false,position.tp)
            }

            if (position.sl!= 0.0 && ask>position.sl){
                exit(position,false,position.sl)
            }
        }

    }

    @Synchronized
    fun getUserPositions(uuid:UUID):List<Position>{

        val rs = mysql.query("select * from position_table where uuid='${uuid}' and `exit`=0;")?:return Collections.emptyList()

        val list = mutableListOf<Position>()

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
        mysql.close()

        return list
    }


    fun entry(p:Player,lots: Double,isBuy:Boolean):Boolean{

        val id = UUID.randomUUID()
        val price = if (isBuy) Price.ask() else Price.bid()

        val maxLots = maxLots(p.uniqueId,price)
        //ロット数が証拠金より多い場合
        if (lots> maxLots){
            p.sendMessage("${prefix}あなたがエントリーできる最大ロット数は${String.format("%,.2f", maxLots)}までです！")
            return false
        }

        val position = Position(id,p.uniqueId,lots,price,isBuy,!isBuy,0.0,0.0)

        createPosition(position)

        p.sendMessage("${prefix}エントリーを受け付けました！価格:§d§l${String.format("%,.3f", price)}")

        return true
    }

    private fun exit(position: Position,isLossCut:Boolean,exitPrice: Double? = null){

        val price = exitPrice ?: if (position.buy) Price.bid() else Price.ask()
        val profit = profit(position)

        if (profit>0){
            val msg = if (isLossCut) "強制ロスカット" else "FX利益"

            bank.deposit(position.uuid,profit, "ForexProfit",msg)
        }

        if (profit<0){
            val msg = if (isLossCut) "強制ロスカット" else "FXの損失"

            if (!bank.withdraw(position.uuid,-profit, "ForexLoss",msg)){
                bank.withdraw(position.uuid, bank.getBalance(position.uuid),"ForexZeroCut","FXゼロカット")
            }
        }

        closePosition(position.positionID,price,profit)
    }

    //手動Exit
    fun exit(p:Player, pos: UUID){

        val list = getUserPositions(p.uniqueId)

        list.forEach {
            if (it.positionID == pos){
                exit(it,false)
                p.sendMessage("${prefix}ポジションをイグジットしました！(利益の確認は/ballog)")
                return@forEach
            }
        }
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


    //差額(Pips
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
        return bank.getBalance(uuid) + allProfit(list)
    }

    //ロスカットラインに入っているか
    fun isLossCutLine(uuid: UUID,list:List<Position>):Boolean{

        val p = Bukkit.getOfflinePlayer(uuid)

        //有効証拠金
        val margin = margin(uuid,list)
        //必要証拠金
        val require = marginRequirement(list)
        //証拠金維持率
        val percent = if (require==0.0) 0.0 else margin/require*100.0
        if (percent!=0.0 && percent< lossCutPercent){

            if (p.isOnline){
                p.player!!.sendMessage("${prefix}§4§l損失が激しいため強制ロスカットを行いました！")
            }

            Bukkit.getLogger().info("LOSS CUT ${percent}%")
            return true
        }
        return false

    }

    private fun maxLots(uuid: UUID, price: Double):Double{
        val margin = margin(uuid, getUserPositions(uuid))
        return margin * leverage  /(price* unitSize)
    }


    private fun lotsToMan10Money(lots:Double, price: Double):Double{
        return price*lots* unitSize
    }

    fun man10MoneyToLots(money:Double,price: Double):Double{
        return money/price/ unitSize
    }

    //ドル円のみ対応
    private fun priceToPips(price: Double): Double {
        return price*100.0
    }

    private fun pipsToPrice(pips:Double):Double{
        return pips/100.0
    }



    //ポジション管理スレッド、ロスカット処理などを行う
    fun positionThread(){

        val threadDB = MySQLManager(plugin,"positionThread")

        while (true){
            try {

                Thread.sleep(1000)

                val rs = threadDB.query("select uuid from position_table where `exit`=0 group by uuid;")?:continue

                while (rs.next()){

                    val uuid = UUID.fromString(rs.getString("uuid"))
                    var list = getUserPositions(uuid)
                    if (list.isEmpty())continue

                    //強制ロスカット、SL TPなど
                    for (p in list){

                        checkTouchTPSL(p)

                        if (isLossCutLine(uuid,list)){
                            exit(p,true)
                            list = getUserPositions(uuid)
                        }
                    }

                }

                rs.close()
                mysql.close()

            }catch (e:Exception){
                Bukkit.getLogger().info(e.message)
            }
        }
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

}