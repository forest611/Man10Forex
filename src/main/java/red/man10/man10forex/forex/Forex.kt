package red.man10.man10forex.forex

import org.bukkit.Bukkit
import red.man10.man10bank.Bank
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import java.util.*

object Forex {

    private var leverage = 100
    private var minLot : Double = 0.01
    private var maxLot : Double = 1000.0

    private val mysql:MySQLManager = MySQLManager(plugin,"Man10Forex")

    fun loadConfig(){
        plugin.reloadConfig()

        leverage = plugin.config.getInt("Leverage")
        minLot = plugin.config.getDouble("MinLot")
        maxLot = plugin.config.getDouble("MaxLot")
    }
//
//    //idはポジションID
//    private fun getPosition(id:UUID):Position?{
//
//        return null
//    }

    private fun closePosition(id: UUID,price: Double,profit:Double){
        mysql.execute("UPDATE position_table SET `exit` = 1, exit_price = ${price}, profit = ${profit}, exit_date = now() WHERE position_id = '${id}';")
    }

    private fun updatePosition(position: Position){

    }

    private fun createPosition(position: Position){
        val p = Bukkit.getOfflinePlayer(position.uuid).name

        mysql.execute("INSERT INTO position_table (position_id, player, uuid, lots, buy, sell, `exit`, entry_price, exit_price, profit, entry_date, exit_date) " +
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


    fun entry(p:UUID,lots: Double,isBuy:Boolean):Boolean{

        val id = UUID.randomUUID()
        val price = if (isBuy) Price.ask() else Price.bid()

        //ロット数が証拠金より多い場合
        if (lots> maxLots(p,price)){
            return false
        }

        if (!bank.withdraw(p, lotsToMan10Money(lots,price),"ForexEntry","FXのエントリー")){
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
            bank.withdraw(position.uuid,-profit, "ForexLoss","FXの損失")
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

    fun maxLots(uuid: UUID,price: Double):Double{
        val bank = Bank.getBalance(uuid)
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