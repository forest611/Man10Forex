package red.man10.man10forex.forex

import org.bukkit.Bukkit
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import java.util.*

object Forex {

    private var leverage = 100
    private var minLot : Double = 0.01
    private var maxLot : Double = 1000.0
    private var lossCutPercent : Double = 20.0

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
    private fun getUserPositions(uuid:UUID):List<Position>{

        val rs = mysql.query("select * from position_table where uuid='${uuid}'")?:return Collections.emptyList()

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


    fun entry(p:UUID,lots: Double,isBuy:Boolean):Boolean{

        val id = UUID.randomUUID()
        val price = if (isBuy) Price.ask() else Price.bid()

        //ロット数が証拠金より多い場合
        if (lots> maxLots(p,price)){
            return false
        }

        val position = Position(id,p,lots,price,isBuy,!isBuy,0.0,0.0)

        createPosition(position)

        return true
    }

    fun exit(position: Position){

        val price = if (position.buy) Price.ask() else Price.bid()
        val profit = profit(position)?:return

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

    fun profit(position:Position): Double? {

        if (position.buy){
            val bid = Price.bid()
            val entryMoney = lotsToMan10Money(position.lots,position.entryPrice)
            val nowMoney = lotsToMan10Money(position.lots,bid)
            return nowMoney-entryMoney
        }

        if (position.sell){
            val ask = Price.bid()
            val entryMoney = lotsToMan10Money(position.lots,position.entryPrice)
            val nowMoney = lotsToMan10Money(position.lots,ask)
            return entryMoney-nowMoney
        }

        return null
    }

    //全ポジの含み益
    fun allProfit(list:List<Position>):Double {

        var profit = 0.0
        list.forEach { profit+= profit(it)?:0.0 }

        return profit
    }

    //必要証拠金
    fun marginRequirement(list:List<Position>):Double{
        var margin = 0.0
        list.forEach { margin+= lotsToMan10Money(it.lots,it.entryPrice) }
        return margin
    }

    //ロスカットラインに入っているか
    fun isLossCutLine(uuid: UUID,list:List<Position>):Boolean{
        //有効証拠金
        val margin = bank.getBalance(uuid) + allProfit(list)
        //必要証拠金
        val require = marginRequirement(list)
        //証拠金維持率
        val percent = margin/require*100.0
        if (percent< lossCutPercent){
            return true
        }
        return false

    }

    fun maxLots(uuid: UUID,price: Double):Double{
        val bank = bank.getBalance(uuid)
        return bank / price * leverage / 1000
    }


    fun lotsToMan10Money(lots:Double,price: Double):Double{
        return price*lots*1000
    }

    fun man10MoneyToLots(money:Double,price: Double):Double{
        return money/price/1000
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

                val rs = threadDB.query("select uuid from position_table where exit=0 group by uuid;")?:continue

                while (rs.next()){

                    val uuid = UUID.fromString(rs.getString("uuid"))
                    var list = getUserPositions(uuid)
                    if (list.isEmpty())continue

                    //ポジション数を見て強制ロスカット
                    list.forEach {
                        if (!isLossCutLine(uuid,list))return@forEach
                        exit(it)
                        list = getUserPositions(uuid)
                    }

                }

                rs.close()
                mysql.close()

                Thread.sleep(1000)

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