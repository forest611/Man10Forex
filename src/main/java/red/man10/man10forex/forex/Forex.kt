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

    private val mysql:MySQLManager = MySQLManager(plugin,"Man10Forex")

    fun loadConfig(){
        plugin.reloadConfig()

        leverage = plugin.config.getInt("Leverage")
        minLot = plugin.config.getDouble("MinLot")
        maxLot = plugin.config.getDouble("MaxLot")
        lossCutPercent = plugin.config.getDouble("LossCutPercent")
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
                0.0,
                0.0
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

    private fun exit(position: Position){

        val price = if (position.buy) Price.bid() else Price.ask()
        val profit = profit(position)

        if (profit>0){
            bank.deposit(position.uuid,profit, "ForexProfit","FXの利益")
        }

        if (profit<0){
            if (!bank.withdraw(position.uuid,-profit, "ForexLoss","FXの損失")){
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
                exit(it)
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
        //有効証拠金
        val margin = margin(uuid,list)
        //必要証拠金
        val require = marginRequirement(list)
        //証拠金維持率
        val percent = if (require==0.0) 0.0 else margin/require*100.0
        if (percent!=0.0 && percent< lossCutPercent){
            Bukkit.getLogger().info("LOSS CUT ${percent}%")
            return true
        }
        return false

    }

    fun maxLots(uuid: UUID,price: Double):Double{
        val margin = margin(uuid, getUserPositions(uuid))
        return margin * leverage  /(price* unitSize)
    }


    fun lotsToMan10Money(lots:Double,price: Double):Double{
        return price*lots* unitSize
    }

    fun man10MoneyToLots(money:Double,price: Double):Double{
        return money/price/ unitSize
    }

    //ドル円のみ対応
    fun priceToPips(price: Double): Double {
        return price*100.0
    }

    fun pipsToPrice(pips:Double):Double{
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

                    //ポジション数を見て強制ロスカット
                    for (p in list){
                        if (!isLossCutLine(uuid,list))break
                        exit(p)
                        list = getUserPositions(uuid)

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
        val sl:Double,
        val tp:Double
    )

}