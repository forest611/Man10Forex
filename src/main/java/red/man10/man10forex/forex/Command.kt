package red.man10.man10forex.forex

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEventSource
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10forex.Man10Forex.Companion.FOREX_USER
import red.man10.man10forex.Man10Forex.Companion.OP
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.forex.Forex.allProfit
import red.man10.man10forex.forex.Forex.getUserPositions
import red.man10.man10forex.forex.Forex.isEnable
import red.man10.man10forex.forex.Forex.lossCutPercent
import red.man10.man10forex.forex.Forex.margin
import red.man10.man10forex.forex.Forex.marginRequirement
import red.man10.man10forex.forex.Forex.maxLot
import red.man10.man10forex.forex.Forex.minLot
import red.man10.man10forex.forex.Forex.prefix
import red.man10.man10forex.forex.Forex.profit
import red.man10.man10forex.forex.Forex.setSL
import red.man10.man10forex.forex.Forex.setTP
import java.lang.Exception
import java.util.*

object Command :CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="mfx")return false
        if (sender!is Player)return false

        if (args.isNullOrEmpty()){

            if (!sender.hasPermission(FOREX_USER)){ return true }

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

                val percentMsg = Component.text("${prefix}${percentColor}維持率:${String.format("%,.3f", percent)}%")
                    .hoverEvent(HoverEvent.showText(Component.text("§c§l維持率が20.0%を下回ると、ポジションが強制的にイグジットされます")))

                sender.sendMessage("${prefix}§e§l=============[Man10Trader(MT10)]=============")
                sender.sendMessage("${prefix}銀行残高:${String.format("%,.0f", bank)}")
                sender.sendMessage("${prefix}有効金額:${String.format("%,.0f", margin)}")
                sender.sendMessage("${prefix}${profitColor}評価額:${String.format("%,.0f", allProfit)}")
                sender.sendMessage(percentMsg)
                sender.sendMessage("${prefix}===保有ポジション(クリックしてイグジット(利益確定))===")

                list.forEach {

                    val profit = profit(it)

                    val lots = (if (it.buy) "§a§lB" else "§c§lS") + " ${it.lots}ロット "
                    val openPrice = "O:${String.format("%,.3f", it.entryPrice)} "
                    val profitText = if (profit>0.0) "§b§l${String.format("%,.0f", profit)}円" else if(profit<0.0) "§4§l${String.format("%,.0f", profit)}円" else "§f§l${String.format("%,.0f", profit)}円"
                    val diff = " (${String.format("%,.1f", Forex.diffPips(it))}Pips)"

                    val positionDataText = "§7§lポジション情報\n" +
                            "${if (it.buy) "§a§l買" else "§c§l売"}ポジション\n" +
                            "§7ロット数:§l${it.lots}\n" +
                            "§7オープン価格:§l${String.format("%,.3f", it.entryPrice)}"

                    val msg = Component.text(prefix+lots+openPrice+profitText+diff)
                        .clickEvent(ClickEvent.runCommand("/mfx exit ${it.positionID}"))
                        .hoverEvent(HoverEvent.showText(Component.text(positionDataText)))

                    val showTP = " §a§n[TP${if (it.tp!=0.0) "(${String.format("%,.3f", it.tp)})" else ""}]"
                    val showSL = " §c§n[SL${if (it.sl!=0.0) "(${String.format("%,.3f", it.sl)})" else ""}]"

                    val compTP = Component.text(showTP)
                        .clickEvent(ClickEvent.suggestCommand("/mfx tp ${it.positionID} "))
                        .hoverEvent(HoverEvent.showText(Component.text("§a自動で利益を確定する価格を設定します")))
                    val compSL = Component.text(showSL)
                        .clickEvent(ClickEvent.suggestCommand("/mfx sl ${it.positionID} "))
                        .hoverEvent(HoverEvent.showText(Component.text("§c自動で損失を確定する価格を設定します")))

                    sender.sendMessage(msg.append(compTP).append(compSL))

                }

                val prefix = Component.text(prefix)
                val sellButton = Component.text("§c§l§n[売る]")
                    .clickEvent(ClickEvent.suggestCommand("/mfx sell "))
                    .hoverEvent(HoverEvent.showText(Component.text("§c現在価格より下回ったら利益がでます\n§c/mfx sell <ロット数>(0.01〜1000)")))
                val space = Component.text("    ")
                val buyButton = Component.text("§a§l§n[買う]")
                    .clickEvent(ClickEvent.suggestCommand("/mfx buy "))
                    .hoverEvent(HoverEvent.showText(Component.text("§a現在価格より上回ったら利益がでます\n§a/mfx buy <ロット数>(0.01〜1000)")))


                sender.sendMessage(prefix.append(sellButton).append(space).append(buyButton))

            }.start()

            return true
        }

        when(args[0]){

            "entry" ->{

                if (!sender.hasPermission(FOREX_USER)){ return true }

                if (!isEnable){
                    sender.sendMessage("${prefix}現在取引所がクローズしております")
                    return true
                }

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

            "tp" ->{

                if (args.size!=3){
                    sender.sendMessage("${prefix}入力に問題があります")
                    return true
                }

                try {

                    val posId = UUID.fromString(args[1])
                    val tp = args[2].toDoubleOrNull()

                    if (tp==null){
                        sender.sendMessage("${prefix}数字で入力してください！")
                        return true
                    }

                    Thread{ setTP(sender,posId,tp) }.start()

                }catch (e:Exception){
                    sender.sendMessage("${prefix}入力に問題があります")
                }
            }

            "sl" ->{

                if (args.size!=3){
                    sender.sendMessage("${prefix}入力に問題があります")
                    return true
                }

                try {
                    val posId = UUID.fromString(args[1])
                    val sl = args[2].toDoubleOrNull()

                    if (sl==null){
                        sender.sendMessage("${prefix}数字で入力してください！")
                        return true
                    }

                    Thread{ setSL(sender,posId,sl) }.start()

                }catch (e:Exception){
                    sender.sendMessage("${prefix}入力に問題があります")
                }
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

            "reload" ->{

                if (!sender.hasPermission(OP)){ return true }

                Forex.loadConfig()
            }

            "on" ->{
                if (!sender.hasPermission(OP)){ return true }
                isEnable = true
            }

            "off" ->{
                if (!sender.hasPermission(OP)){ return true }
                isEnable = false
            }
        }

        return true
    }
}