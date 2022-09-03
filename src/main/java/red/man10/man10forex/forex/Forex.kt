package red.man10.man10forex.forex

import red.man10.man10bank.Bank
import red.man10.man10forex.Man10Forex
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.util.Price
import java.util.*

object Forex {

    var leverage = 100
    var minLot : Double = 0.01
    var maxLot : Double = 1000.0

    fun loadConfig(){
        plugin.reloadConfig()

        leverage = plugin.config.getInt("Leverage")
        minLot = plugin.config.getDouble("MinLot")
        maxLot = plugin.config.getDouble("MaxLot")
    }

    //idはポジションID
    private fun getPosition(id:UUID):Position?{

        return null
    }

    private fun removePosition(id: UUID){

    }

    private fun savePosition(position: Position){

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

        savePosition(position)

        return true
    }

    fun exit(position: Position){
        val profit = profit(position)?:return

        if (profit>0){
            bank.deposit(position.uuid,profit, "ForexProfit","FXの利益")
        }

        if (profit<0){
            bank.withdraw(position.uuid,-profit, "ForexLoss","FXの損失")
        }

        removePosition(position.positionID)
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