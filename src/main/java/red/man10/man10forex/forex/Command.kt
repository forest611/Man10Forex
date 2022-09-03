package red.man10.man10forex.forex

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.forex.Forex.allProfit
import red.man10.man10forex.forex.Forex.getUserPositions
import red.man10.man10forex.forex.Forex.lossCutPercent
import red.man10.man10forex.forex.Forex.margin
import red.man10.man10forex.forex.Forex.marginRequirement
import red.man10.man10forex.forex.Forex.maxLot
import red.man10.man10forex.forex.Forex.minLot
import red.man10.man10forex.forex.Forex.prefix
import red.man10.man10forex.forex.Forex.profit
import java.util.*

object Command :CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="mfx")return false
        if (sender!is Player)return false

        if (args.isNullOrEmpty()){

            val uuid = sender.uniqueId

            Thread{

                val list = getUserPositions(uuid)

                val bank = bank.getBalance(uuid)
                val margin = margin(uuid,list)
                val require = marginRequirement(list)
                val percent = if (require==0.0) 0.0 else margin/require*100.0
                val allProfit = allProfit(list)

                val profitColor = if (allProfit<0) "§4§l" else if (allProfit>0) "§b§l" else "§f§l"
                val percentColor =  if (percent==0.0) "§f§l" else if (percent< lossCutPercent*1.5) "§4§l" else if (percent< lossCutPercent*2.0) "§6§l"  else "§f§l"

                sender.sendMessage("${prefix}=============[MT10]=============")
                sender.sendMessage("${prefix}残高:${String.format("%,.0f", bank)}")
                sender.sendMessage("${prefix}有効金額:${String.format("%,.0f", margin)}")
                sender.sendMessage("${prefix}${profitColor}評価額:${String.format("%,.0f", allProfit)}")
                sender.sendMessage("${prefix}${percentColor}証拠金維持率:${String.format("%,.3f", percent)}%")
                sender.sendMessage("${prefix}==保有ポジション(クリックしてイグジット(決済)できます)==")

                list.forEach {

                    val profit = profit(it)

                    val eColor = (if (it.buy) "§a§l買" else "§c§l売") + " ${it.lots}ロット "
                    val pColor = if (profit>0.0) "§b§l${String.format("%,.0f", profit)}" else if(profit<0.0) "§4§l${String.format("%,.0f", profit)}" else "§f§l${String.format("%,.0f", profit)}"
                    val start = " 開始値段:${String.format("%,.3f", it.entryPrice)} "

                    val diff = " (${String.format("%,.1f", Forex.diffPips(it))}Pips)"

                    val msg = Component.text(prefix+eColor+start+pColor+diff).
                    clickEvent(ClickEvent.runCommand("/mfx exit ${it.positionID}"))


                    sender.sendMessage(msg)

                }

                val prefix = Component.text(prefix)
                val sellButton = Component.text("§c§l[売る]    ").clickEvent(ClickEvent.suggestCommand("/mfx sell "))
                val buyButton = Component.text("§a§l[買う]").clickEvent(ClickEvent.suggestCommand("/mfx buy "))

                sender.sendMessage(prefix.append(sellButton).append(buyButton))

            }.start()
            return true

        }

        when(args[0]){

            "entry" ->{

                if (args.size!=3){
                    sender.sendMessage("${prefix}/mfx entry <b/s> <ロット数> (買う場合はb 売る場合はs)")
                    return false
                }

                if (args[1] != "b" && args[1] != "s"){
                    sender.sendMessage("${prefix}買う場合は<b> 売る場合は<s>を入力してください")
                    return true
                }

                val isBuy = args[1] == "b"

                val lots = args[2].toDoubleOrNull()

                if (lots == null){
                    sender.sendMessage("${prefix}ロット数を数字で入力してください！")
                    return true
                }

                if (lots< minLot || lots> maxLot){
                    sender.sendMessage("${prefix}最小ロット:${minLot} 最大ロット:${maxLot}")
                    return true
                }

                Thread{ Forex.entry(sender,lots,isBuy) }.start()
                return true
            }

            "buy" ->{
                if (args.size!=2){
                    sender.sendMessage("${prefix}/mfx buy <ロット数>")
                    return false
                }
                sender.performCommand("mfx entry b ${args[1]}")
            }

            "sell" ->{
                if (args.size!=2){
                    sender.sendMessage("${prefix}/mfx sell <ロット数>")
                    return false
                }
                sender.performCommand("mfx entry s ${args[1]}")
            }

            "exit" ->{
                if (args.size!=2){
                    sender.sendMessage("${prefix}イグジット失敗！、正しくイグジットできていない可能性があります！")
                    return true
                }

                val posId = UUID.fromString(args[1])

                Thread{ Forex.exit(sender,posId) }.start()
                return true

            }

        }


        return true
    }
}