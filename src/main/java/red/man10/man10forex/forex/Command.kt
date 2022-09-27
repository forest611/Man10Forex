package red.man10.man10forex.forex

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10forex.Man10Forex.Companion.FOREX_USER
import red.man10.man10forex.Man10Forex.Companion.OP
import red.man10.man10forex.Man10Forex.Companion.bank
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.forex.Forex.allProfit
import red.man10.man10forex.forex.Forex.asyncGetUserPositions
import red.man10.man10forex.forex.Forex.lossCutPercent
import red.man10.man10forex.forex.Forex.margin
import red.man10.man10forex.forex.Forex.marginRequirement
import red.man10.man10forex.forex.Forex.maxLot
import red.man10.man10forex.forex.Forex.minLot
import red.man10.man10forex.forex.Forex.prefix
import red.man10.man10forex.forex.Forex.profit
import red.man10.man10forex.forex.Forex.setSL
import red.man10.man10forex.forex.Forex.setTP
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import red.man10.man10forex.util.Utility
import red.man10.man10forex.util.Utility.moneyFormat
import red.man10.man10forex.util.Utility.priceFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

object Command :CommandExecutor{

    private val showBalanceQueue = LinkedBlockingQueue<Player>()
    private val sdf = SimpleDateFormat("MM/dd HH:mm")

    init {
        Thread{ showBalanceThread() }.start()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="mfx")return false

        if (args.isNullOrEmpty()){
            if (sender!is Player)return false

            if (!sender.hasPermission(FOREX_USER)){ return true }
            showBalanceQueue.add(sender)
            return true
        }

        when(args[0]){

            "help" ->{

                sender.sendMessage("${prefix}/mfx　・・・ mfxのメニューを開きます")
                sender.sendMessage("${prefix}/mfx buy <ロット数>・・・ 買いポジションを持ちます")
                sender.sendMessage("${prefix}/mfx sell <ロット数>・・・ 売りポジションを持ちます")
                sender.sendMessage("${prefix}/mfx tp <価格>　・・・ 利確ラインを設定します")
                sender.sendMessage("${prefix}/mfx sl <価格>・・・ 損切りラインを設定します")
                sender.sendMessage("${prefix}/mfx exit ・・・ ボタンでのみ有効")
                sender.sendMessage("${prefix}/mfx d <金額/all> ・・・ mfx口座に入金をします")
                sender.sendMessage("${prefix}/mfx w <金額/all> ・・・ mfx口座から出金します")

                if (sender.hasPermission(OP)){
                    sender.sendMessage("${prefix}§c§l/mfx bal <mcid> ・・・ 指定ユーザーの口座をみます")
                    sender.sendMessage("${prefix}§c§l/mfx reload ・・・ Configなどを読み直します")
                    sender.sendMessage("${prefix}§c§l/mfx status <Status> true/false ・・・ ステータスのON/OFF")
                    sender.sendMessage("${prefix}§c§l/mfx exitop ・・・ ボタンでのみ有効")
                }

            }

            "entry" ->{

                if (sender!is Player)return false

                if (!sender.hasPermission(FOREX_USER)){ return true }

                if (!Price.isActiveTime()){
                    sender.sendMessage("${prefix}現在取引時間外です")
                    return true
                }

                if (Price.error){
                    sender.sendMessage("${prefix}価格取得よりエントリー失敗！しばらく続く場合、サーバーにレポートを送ってください(${sdf.format(Date())})。")
                    return true
                }

                if (!Forex.MarketStatus.entry){
                    sender.sendMessage("${prefix}現在エントリーできません")
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

                Forex.entry(sender,lots,isBuy)

                return true
            }

            "buy" ->{
                if (sender!is Player)return false

                if (args.size!=2){
                    sender.sendMessage("${prefix}/mfx buy <ロット数>")
                    return false
                }
                sender.performCommand("mfx entry b ${args[1]}")
            }

            "sell" ->{
                if (sender!is Player)return false

                if (args.size!=2){
                    sender.sendMessage("${prefix}/mfx sell <ロット数>")
                    return false
                }
                sender.performCommand("mfx entry s ${args[1]}")
            }

            "tp" ->{
                if (sender!is Player)return false

                if (!Price.isActiveTime()){
                    sender.sendMessage("${prefix}現在取引時間外です")
                    return true
                }

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
                    setTP(sender,posId,tp)
                }catch (e:Exception){
                    sender.sendMessage("${prefix}入力に問題があります")
                }
            }

            "sl" ->{
                if (sender!is Player)return false

                if (!Price.isActiveTime()){
                    sender.sendMessage("${prefix}現在取引時間外です")
                    return true
                }

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
                    setSL(sender,posId,sl)

                }catch (e:Exception){
                    sender.sendMessage("${prefix}入力に問題があります")
                }
            }

            "exit" ->{
                if (sender!is Player)return false

                if (!Price.isActiveTime()){
                    sender.sendMessage("${prefix}現在取引時間外です")
                    return true
                }

                if (Price.error){
                    sender.sendMessage("${prefix}価格取得によりイグジット失敗！しばらく続く場合、サーバーにレポートを送ってください(${sdf.format(Date())})。")
                    return true
                }

                if (!Forex.MarketStatus.exit){
                    sender.sendMessage("${prefix}現在手動決済はできません")
                    return true
                }

                if (args.size!=2){
                    sender.sendMessage("${prefix}決済失敗！、正しく決済できていない可能性があります！")
                    return true
                }

                val posId = UUID.fromString(args[1])

                Forex.exit(sender.uniqueId,posId,false)

                return true
            }

            "d" ->{

                if (sender!is Player)return false

                if (!Forex.MarketStatus.deposit){
                    sender.sendMessage("${prefix}現在FX口座への入金はできません")
                    return true
                }

                val uuid = sender.uniqueId
                val amount = if (args[1] == "all") bank.getBalance(uuid) else args[1].toDoubleOrNull()

                if (amount == null || amount < 1.0 ){
                    sender.sendMessage("${prefix}数字で入力してください")
                    return true
                }

                Thread{

                    if (bank.withdraw(uuid,amount,"Bank->FX","銀行からFX口座へ")){
                        if (ForexBank.deposit(uuid,amount,"FromBank","銀行から入金")){
                            sender.sendMessage("${prefix}銀行からFX口座に入金されました")
                        }
                        return@Thread
                    }
                    sender.sendMessage("${prefix}銀行の残高が足りません")

                }.start()

                return true
            }

            "w" ->{

                if (sender!is Player)return false

                if (!Forex.MarketStatus.withdraw){
                    sender.sendMessage("${prefix}現在FX口座から出金はできません")
                    return true
                }

                val uuid = sender.uniqueId
                val amount = if (args[1] == "all") ForexBank.getBalance(uuid) else args[1].toDoubleOrNull()

                if (amount == null || amount < 1.0 ){
                    sender.sendMessage("${prefix}数字で1以上を入力してください")
                    return true
                }

                Thread{

                    val sql = MySQLManager(plugin,"WithdrawForex")

                    if (asyncGetUserPositions(uuid,sql).isNotEmpty()){
                        sender.sendMessage("${prefix}ポジションを持っているときは、出金できません！")
                        return@Thread
                    }

                    if (ForexBank.withdraw(uuid,amount,"ToBank","銀行へ出金")){
                        bank.deposit(uuid,amount, "FX->Bank","FX口座から銀行へ")
                        return@Thread
                    }
                    sender.sendMessage("${prefix}FX口座の残高が足りません")

                }.start()

                return true
            }

            "bal" ->{
                if (!sender.hasPermission(OP)){ return true }

                val mcid = args[1]

                Thread{
                    val sql = MySQLManager(plugin,"ShowBalanceOP")
                    showBalanceOP(sender,sql,mcid)
                }.start()

            }

            "reload" ->{

                if (!sender.hasPermission(OP)){ return true }
                Forex.loadConfig()
                Forex.runThread()
                sender.sendMessage("Reload")
            }

            "status" ->{
                if (!sender.hasPermission(OP)){ return true }

                if (args.size==3){

                    when(args[1]){

                        "entry" ->Forex.MarketStatus.entry = args[2].toBoolean()
                        "exit" ->Forex.MarketStatus.exit = args[2].toBoolean()
                        "deposit" ->Forex.MarketStatus.deposit = args[2].toBoolean()
                        "withdraw" ->Forex.MarketStatus.withdraw = args[2].toBoolean()
                        "tpsl" -> Forex.MarketStatus.tpsl = args[2].toBoolean()
                        "losscut" -> Forex.MarketStatus.lossCut = args[2].toBoolean()

                        "all" ->{
                            val bool = args[2].toBoolean()
                            Forex.MarketStatus.entry = bool
                            Forex.MarketStatus.exit = bool
                            Forex.MarketStatus.deposit = bool
                            Forex.MarketStatus.withdraw = bool
                            Forex.MarketStatus.tpsl = bool
                            Forex.MarketStatus.lossCut = bool
                        }

                        else ->{
                            sender.sendMessage("${prefix}all/entry/exit/deposit/withdraw/tpsl/losscut")
                            sender.sendMessage("${prefix}/mfx status <StatusType> true/false")
                        }
                    }

                }
                sender.sendMessage("${prefix}entry:${Forex.MarketStatus.entry}")
                sender.sendMessage("${prefix}exit:${Forex.MarketStatus.exit}")
                sender.sendMessage("${prefix}deposit:${Forex.MarketStatus.deposit}")
                sender.sendMessage("${prefix}withdraw:${Forex.MarketStatus.withdraw}")
                sender.sendMessage("${prefix}tpsl:${Forex.MarketStatus.tpsl}")
                sender.sendMessage("${prefix}losscut:${Forex.MarketStatus.lossCut}")

                Forex.setStatus()
                //Forex.showQueueStatus(sender)
            }

            "exitop" ->{//mfx exitop player id price
                if (!sender.hasPermission(OP)){ return true }

                val p =  Bank.getUUID(args[1])

                if (p ==null){
                    sender.sendMessage("プレイヤーがNull")
                    return true
                }

                val id = UUID.fromString(args[2])
                val price = if (args.size>=4){ args[3].toDoubleOrNull() } else null

                Forex.exit(p,id,false,price)

            }
        }

        return true
    }


    private fun showBalanceThread(){

        val sql = MySQLManager(plugin,"ShowBalanceForex")

        while (true){
            try {
                val p = showBalanceQueue.take()
                showBalance(p,sql)

            }catch (e:java.lang.Exception){
                Bukkit.getLogger().info(e.message)
            }
        }
    }

    private fun showBalance(p:Player,sql:MySQLManager){

        val uuid = p.uniqueId
        val list = asyncGetUserPositions(uuid,sql)

        val balance = ForexBank.getBalance(uuid)
        val margin = margin(uuid,list)
        val require = marginRequirement(list)
        val percent = if (require==0.0) 0.0 else margin/require*100.0
        val allProfit = allProfit(list)

        val profitColor = if (allProfit<0) "§4§l" else if (allProfit>0) "§b§l" else "§f§l"
        val percentColor =  if (percent==0.0) "§f§l" else if (percent< lossCutPercent*1.5) "§4§l" else if (percent< lossCutPercent*2.0) "§6§l"  else "§f§l"

        val percentMsg = text("${prefix}${percentColor}維持率:${Utility.format(percent,3)}%")
            .hoverEvent(HoverEvent.showText(text("§c§l維持率が20.0%を下回ると、ポジションが強制的に決済されます")))

        val balanceMsg = text("${prefix}残高:${moneyFormat(balance)}               ")
            .hoverEvent(HoverEvent.showText(text("§f§nFXの口座は、銀行口座と別のものを使用します")))

        val depositButton = text("§a§n${isAllowed(Forex.MarketStatus.deposit)}[入金]")
            .clickEvent(ClickEvent.suggestCommand("/mfx d "))
            .hoverEvent(HoverEvent.showText(text("§f銀行のお金を、FX口座に入金します")))

        val withdrawButton = text("  §c§n${isAllowed(Forex.MarketStatus.withdraw)}[出金]")
            .clickEvent(ClickEvent.suggestCommand("/mfx w "))
            .hoverEvent(HoverEvent.showText(text("§fFX口座から、銀行に出金します\n§c§lポジションを持っているときは、出金できません")))


        val title = "${prefix}§e§l=============[Man10Trader(MT10)]============="

        p.sendMessage(title)
        p.sendMessage(balanceMsg.append(depositButton).append(withdrawButton))
        p.sendMessage("${prefix}有効金額:${moneyFormat(margin)}")
        p.sendMessage("${prefix}${profitColor}評価額:${moneyFormat(allProfit)}")
        p.sendMessage(percentMsg)
        p.sendMessage("${prefix}===============保有ポジション===============")

        if (Price.error){ p.sendMessage("${prefix}§c§l現在価格取得ができないため、エントリーなどができません") }
        if (!Price.isActiveTime()){ p.sendMessage("${prefix}現在取引時間外です") }


        list.forEach {

            val profit = profit(it)

            val lots = (if (it.buy) "§a§l買" else "§c§l売") + " ${it.lots}ロット "
            val openPrice = "O:${priceFormat(it.entryPrice)} "
            val profitText = if (profit>0.0) "§b§l${moneyFormat(profit)}円" else if(profit<0.0) "§4§l${moneyFormat(profit)}円" else "§f§l${moneyFormat(profit)}円"
            val diff = " (${Utility.format(Forex.diffPips(it),2)}Pips)"

            val positionDataText = "§7§lポジション情報\n" +
                    "${if (it.buy) "§a§l買" else "§c§l売"}ポジション\n" +
                    "§7ロット数:§l${it.lots}\n" +
                    "§7オープン価格:§l${priceFormat(it.entryPrice)}" +
                    "§7損益:${profit}${diff}"

            val msg = text(prefix+lots+openPrice+profitText+diff)
                .hoverEvent(HoverEvent.showText(text(positionDataText)))

            val exitText = "§e§n${isAllowed(Forex.MarketStatus.exit)}[決済]"
            val tpText = " §a§n[TP${if (it.tp!=0.0) "(${priceFormat(it.tp)})" else ""}]"
            val slText = " §c§n[SL${if (it.sl!=0.0) "(${priceFormat(it.sl)})" else ""}]"

            val exitButton = text(exitText)
                .clickEvent(ClickEvent.runCommand("/mfx exit ${it.positionID}"))
                .hoverEvent(HoverEvent.showText(text("現在の価格で損益の確定を行います")))


            val tpButton = text(tpText)
                .clickEvent(ClickEvent.suggestCommand("/mfx tp ${it.positionID} "))
                .hoverEvent(HoverEvent.showText(text("§a自動で利益を確定する価格を設定します")))
            val slButton = text(slText)
                .clickEvent(ClickEvent.suggestCommand("/mfx sl ${it.positionID} "))
                .hoverEvent(HoverEvent.showText(text("§c自動で損失を確定する価格を設定します")))

            p.sendMessage(msg.append(exitButton).append(tpButton).append(slButton))

        }

        val prefix = text(prefix)
        val sellButton = text("§c§l§n${isAllowed(Forex.MarketStatus.entry)}[売る]")
            .clickEvent(ClickEvent.suggestCommand("/mfx sell "))
            .hoverEvent(HoverEvent.showText(text("§c現在価格より下回ったら利益がでます\n§c/mfx sell <ロット数>(0.01〜1000)\n§f例:1ロットを持った状態でレートが1円下降した場合->+10万円")))
        val space = text("    ")
        val buyButton = text("§a§l§n${isAllowed(Forex.MarketStatus.entry)}[買う]")
            .clickEvent(ClickEvent.suggestCommand("/mfx buy "))
            .hoverEvent(HoverEvent.showText(text("§a現在価格より上回ったら利益がでます\n§a/mfx buy <ロット数>(0.01〜1000)\n§f例:1ロットを持った状態でレートが1円上昇した場合->+10万円")))

        p.sendMessage(prefix.append(sellButton).append(space).append(buyButton))
    }

    private fun showBalanceOP(p:CommandSender,sql: MySQLManager,mcid:String){

        val uuid = Bank.getUUID(mcid)?:return
        val list = asyncGetUserPositions(uuid,sql)

        val balance = ForexBank.getBalance(uuid)
        val margin = margin(uuid,list)
        val require = marginRequirement(list)
        val percent = if (require==0.0) 0.0 else margin/require*100.0
        val allProfit = allProfit(list)

        val profitColor = if (allProfit<0) "§4§l" else if (allProfit>0) "§b§l" else "§f§l"
        val percentColor =  if (percent==0.0) "§f§l" else if (percent< lossCutPercent*1.5) "§4§l" else if (percent< lossCutPercent*2.0) "§6§l"  else "§f§l"

        val percentMsg = text("${prefix}${percentColor}維持率:${Utility.format(percent,3)}%")
            .hoverEvent(HoverEvent.showText(text("§c§l維持率が20.0%を下回ると、ポジションが強制的に決済されます")))

        val balanceMsg = text("${prefix}残高:${moneyFormat(balance)}               ")
            .hoverEvent(HoverEvent.showText(text("§f§nFXの口座は、銀行口座と別のものを使用します")))

        val title = "${prefix}§c§l=============[${mcid}'s Forex]============="

        p.sendMessage(title)
        p.sendMessage(balanceMsg)
        p.sendMessage("${prefix}有効金額:${moneyFormat(margin)}")
        p.sendMessage("${prefix}${profitColor}評価額:${moneyFormat(allProfit)}")
        p.sendMessage(percentMsg)
        p.sendMessage("${prefix}===============保有ポジション===============")

        list.forEach {

            val profit = profit(it)

            val lots = (if (it.buy) "§a§l買" else "§c§l売") + " ${it.lots}ロット "
            val openPrice = "O:${priceFormat(it.entryPrice)} "
            val profitText = if (profit>0.0) "§b§l${moneyFormat(profit)}円" else if(profit<0.0) "§4§l${moneyFormat(profit)}円" else "§f§l${moneyFormat(profit)}円"
            val diff = " (${Utility.format(Forex.diffPips(it),2)}Pips)"

            val positionDataText = "§7§lポジション情報\n" +
                    "${if (it.buy) "§a§l買" else "§c§l売"}ポジション\n" +
                    "§7ロット数:§l${it.lots}\n" +
                    "§7オープン価格:§l${priceFormat(it.entryPrice)}" +
                    "§7損益:${profit}${diff}"

            val msg = text(prefix+lots+openPrice+profitText+diff)
                .hoverEvent(HoverEvent.showText(text(positionDataText)))

            val exitText = "§e§n[決済]"
            val tpText = " §a§n[TP${if (it.tp!=0.0) "(${priceFormat(it.tp)})" else ""}]"
            val slText = " §c§n[SL${if (it.sl!=0.0) "(${priceFormat(it.sl)})" else ""}]"

            val exitButton = text(exitText)
                .clickEvent(ClickEvent.suggestCommand("/mfx exitop $mcid ${it.positionID}"))
                .hoverEvent(HoverEvent.showText(text("ユーザーのポジションをExitします\n/mfx exitop <player> <PositionID> <決済価格(未入力の場合現在価格)>")))


            val tpButton = text(tpText)
                .clickEvent(ClickEvent.suggestCommand("/mfx tp ${it.positionID} "))
                .hoverEvent(HoverEvent.showText(text("§a自動で利益を確定する価格を設定します")))
            val slButton = text(slText)
                .clickEvent(ClickEvent.suggestCommand("/mfx sl ${it.positionID} "))
                .hoverEvent(HoverEvent.showText(text("§c自動で損失を確定する価格を設定します")))

            p.sendMessage(msg.append(exitButton).append(tpButton).append(slButton))

        }
    }

    private fun isAllowed(boolean: Boolean):String{
        return if (boolean) "" else "§m"
    }
}