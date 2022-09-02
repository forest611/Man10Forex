package red.man10.man10forex.highlow

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10forex.Man10Forex.Companion.BINARY_USER
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.highlow.HighLowGame.isEnableGame
import red.man10.man10forex.highlow.HighLowGame.loadConfig
import red.man10.man10forex.highlow.HighLowGame.maxPrice
import red.man10.man10forex.highlow.HighLowGame.maxSecond
import red.man10.man10forex.highlow.HighLowGame.minPrice
import red.man10.man10forex.highlow.HighLowGame.minSecond
import red.man10.man10forex.util.MySQLManager
import java.util.*

object Command : CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (label!="mhl")return false

        if (args.isNullOrEmpty()){
            if (!sender.hasPermission(BINARY_USER))return false

            return true
        }

        when(args[0]){

            //賭ける
            "bet" ->{

                if (sender !is Player)return false

                if (!sender.hasPermission("binary.user")){ return true }

                if (!isEnableGame){
                    sender.sendMessage("${prefix}現在ハイアンドローの取引が停止しています")
                    return true
                }

                if (args.size!=4){
                    sender.sendMessage("${prefix}/binary bet <金額> <秒> <high/low>")
                    return true
                }

                val amount = args[1].toDoubleOrNull()
                val sec = args[2].toIntOrNull()
                val isHigh = args[3] == "high"

                if (amount == null || sec == null){
                    sender.sendMessage("${prefix}入力エラー!")
                    return true
                }

                if (amount<minPrice){
                    sender.sendMessage("${prefix}${String.format("%,.0f",minPrice)}円以上にしてください！")
                    return true
                }

                if (amount>maxPrice){
                    sender.sendMessage("${prefix}${String.format("%,.0f",maxPrice)}円以下にしてください！")
                    return true
                }

                if (sec<minSecond){
                    sender.sendMessage("${prefix}${minSecond}秒以上にしてください！")
                    return true
                }

                if (sec>maxSecond){
                    sender.sendMessage("${prefix}${maxSecond}秒以内にしてください！")
                    return true
                }

                Thread{
                    if (bank.withdraw(sender.uniqueId,amount,"HighLowEntry","ハイアンドローエントリ")){
                        sender.sendMessage("${prefix}ハイアンドローのエントリーをしました！")
                        HighLowGame.entry(sender,amount,sec,isHigh)
                        return@Thread
                    }else{
                        sender.sendMessage("${prefix}銀行のお金が足りません！")
                    }
                }.start()

            }

            "reload" ->{

                if (!sender.hasPermission("binary.op")){ return true }
                loadConfig()
                HighLowGame.closeAll()

            }

            "off" ->{
                if (!sender.hasPermission("binary.op")){ return true }

                isEnableGame = false
            }

            "on" ->{
                if (!sender.hasPermission("binary.op")){ return true }

                isEnableGame = true

            }

            "ranking" ->{

                Thread{
                    val mysql = MySQLManager(plugin,"Binary")
                    val rs = mysql.query("select uuid,sum(payout-bet) from log group by player order by sum(payout-bet) desc limit 10;")?:return@Thread

                    val list = mutableListOf<Pair<OfflinePlayer,Double>>()

                    while (rs.next()){

                        val p = Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid")))
                        val result = rs.getDouble(2)
                        list.add(Pair(p,result))
                    }

                    sender.sendMessage("§e§k§lXX§f§lハイアンドロー利益ランキング§e§k§lXX")
                    list.forEach {
                        sender.sendMessage("§b§l${it.first.name}:§e§l${String.format("%,.0f", it.second)}円")
                    }

                }.start()

            }

        }

        return false
    }
}