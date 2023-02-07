package red.man10.man10forex.highlow

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex.Companion.HIGHLOW_USER
import red.man10.man10forex.Man10Forex.Companion.OP
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.forex.Forex
import red.man10.man10forex.highlow.HighLowGame.isEnableGame
import red.man10.man10forex.highlow.HighLowGame.loadConfig
import red.man10.man10forex.highlow.HighLowGame.maxPrice
import red.man10.man10forex.highlow.HighLowGame.maxSecond
import red.man10.man10forex.highlow.HighLowGame.minPrice
import red.man10.man10forex.highlow.HighLowGame.minSecond
import red.man10.man10forex.highlow.HighLowGame.prefix
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import java.util.*

object Command : CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="mhl")return false

        if (args.isNullOrEmpty()){

            if (sender !is Player)return false

            if (!Price.isActiveTime()){
                sender.sendMessage("${Forex.prefix}現在取引時間外です")
                return true
            }

            if (!sender.hasPermission(HIGHLOW_USER)){ return true }

            if (!isEnableGame){
                sender.sendMessage("${prefix}現在ハイ&ローの取引が停止しています")
                return true
            }

            Menu(sender).open()
            return true
        }

        when(args[0]){

            //賭ける
            "bet" ->{

                if (sender !is Player)return false

                if (!Price.isActiveTime()){
                    sender.sendMessage("${Forex.prefix}現在取引時間外です")
                    return true
                }

                if (Price.error){
                    sender.sendMessage("${Forex.prefix}価格取得に失敗！しばらく続く場合、サーバーにレポートを送ってください。")
                    return true
                }

                if (!sender.hasPermission(HIGHLOW_USER)){
                    sender.sendMessage("${Forex.prefix}権限がありません！")
                    return true
                }

                if (!isEnableGame){
                    sender.sendMessage("${prefix}現在ハイ&ローの取引が停止しています")
                    return true
                }

                if (args.size!=4){
                    sender.sendMessage("${prefix}/mhl bet <金額> <秒> <h/l>")
                    return true
                }

                if (args[3] != "h" && args[3] != "l"){
                    sender.sendMessage("${prefix}上を予想するなら<h>、下を予想するなら<l>を入力してください！")
                    return true
                }

                val amount = args[1].toDoubleOrNull()
                val sec = args[2].toIntOrNull()
                val isHigh = args[3] == "h"

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

                if (HighLowGame.checkNowEntry(sender)){
                    sender.sendMessage("${prefix}ハイローの多重エントリーはできません")
                    return true
                }

                Thread{
                    if (bank.withdraw(sender.uniqueId,amount,"HighLowEntry","ハイ&ローエントリ")){
                        sender.sendMessage("${prefix}ハイ&ローのエントリーをしました！")
                        HighLowGame.entry(sender,amount,sec,isHigh)
                        return@Thread
                    }else{
                        sender.sendMessage("${prefix}銀行のお金が足りません！")
                    }
                }.start()
            }

            "reload" ->{

                if (!sender.hasPermission(OP)){ return true }
                loadConfig()
                HighLowGame.closeAll()
                sender.sendMessage("${prefix}リロード完了")
            }

            "off" ->{
                if (!sender.hasPermission(OP)){ return true }
                isEnableGame = false
                sender.sendMessage("${prefix}ハイローをオフにしました")
                return true
            }

            "on" ->{
                if (!sender.hasPermission(OP)){ return true }
                isEnableGame = true
                sender.sendMessage("${prefix}ハイローをオンにしました")
            }

            "ranking" ->{

                Thread{
                    val mysql = MySQLManager(plugin,"HighLow")
                    val rs = mysql.query("select uuid,sum(payout-bet) from log group by player order by sum(payout-bet) desc limit 10;")?:return@Thread

                    val list = mutableListOf<Pair<OfflinePlayer,Double>>()

                    while (rs.next()){
                        val p = Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid")))
                        val result = rs.getDouble(2)
                        list.add(Pair(p,result))
                    }

                    sender.sendMessage("§e§k§lXX§f§lハイ&ロー利益ランキング§e§k§lXX")
                    list.forEach {
                        sender.sendMessage("§b§l${it.first.name}:§e§l${String.format("%,.0f", it.second)}円")
                    }

                }.start()
            }
        }

        return false
    }
}