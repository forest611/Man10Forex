package red.man10.man10forex.util

object Utility {

    fun priceFormat(price:Double):String{
        return format(price,3)
    }

    fun moneyFormat(money:Double):String{
        return format(money,0)
    }

    fun format(amount: Double,digit:Int = 0):String{
        return String.format("%,.${digit}f", amount)
    }


}