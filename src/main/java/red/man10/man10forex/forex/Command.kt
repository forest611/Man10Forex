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
import red.man10.man10forex.forex.Command.Func
import red.man10.man10forex.forex.Forex.allProfit
import red.man10.man10forex.forex.Forex.asyncGetUserPositions
import red.man10.man10forex.forex.Forex.entry
import red.man10.man10forex.forex.Forex.getMaxLots
import red.man10.man10forex.forex.Forex.lossCutPercent
import red.man10.man10forex.forex.Forex.margin
import red.man10.man10forex.forex.Forex.marginPercent
import red.man10.man10forex.forex.Forex.prefix
import red.man10.man10forex.forex.Forex.profit
import red.man10.man10forex.forex.Forex.setSL
import red.man10.man10forex.forex.Forex.setTP
import red.man10.man10forex.forex.Forex.symbols
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price
import red.man10.man10forex.util.Utility
import red.man10.man10forex.util.Utility.format
import red.man10.man10forex.util.Utility.moneyFormat
import red.man10.man10forex.util.Utility.priceFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

object Command :CommandExecutor{

    private val showBalanceQueue = LinkedBlockingQueue<Func>()
    private val sdf = SimpleDateFormat("MM/dd HH:mm")

    init {
        Thread{ showQueue() }.start()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label!="mfx")return false

        if (args.isNullOrEmpty()){
            if (sender!is Player)return false

            if (!sender.hasPermission(FOREX_USER)){ return true }
            showBalanceQueue.add(Func { showBalance(sender,it) })
            return true
        }

        when(args[0]){

            "help" ->{

                sender.sendMessage("${prefix}/mfx ... 現在の口座、ポジション情報をみます")
                sender.sendMessage("${prefix}/mfx board ... 売買メニューを開きます")
                sender.sendMessage("${prefix}/mfx buy <ロット数> ... 買いポジションを持ちます")
                sender.sendMessage("${prefix}/mfx sell <ロット数> ... 売りポジションを持ちます")
                sender.sendMessage("${prefix}/mfx tp <価格> ... 利確ラインを設定します")
                sender.sendMessage("${prefix}/mfx sl <価格> ... 損切りラインを設定します")
                sender.sendMessage("${prefix}/mfx exit ... ボタンでのみ有効")
                sender.sendMessage("${prefix}/mfx d <金額/all> ... mfx口座に入金をします")
                sender.sendMessage("${prefix}/mfx w <金額/all> ... mfx口座から出金します")

                if (sender.hasPermission(OP)){
                    sender.sendMessage("${prefix}§c§l/mfx bal <mcid> ... 指定ユーザーの口座をみます")
                    sender.sendMessage("${prefix}§c§l/mfx reload ... Configなどを読み直します")
                    sender.sendMessage("${prefix}§c§l/mfx status <Status> true/false ... ステータスのON/OFF")
                    sender.sendMessage("${prefix}§c§l/mfx exitop ... ボタンでのみ有効")
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

                if (args.size!=4){
                    sender.sendMessage("${prefix}/mfx entry <b/s> <銘柄> <ロット数> (買う場合はb 売る場合はs)")
                    return false
                }

                if (args[1] != "b" && args[1] != "s"){
                    sender.sendMessage("${prefix}買う場合は<b> 売る場合は<s>を入力してください")
                    return true
                }

                val isBuy = args[1] == "b"
                val symbol = args[2]
                val lots = args[3].toDoubleOrNull()

//                if (!Price.symbolList().contains(symbol)){
//                    sender.sendMessage("${prefix}存在しない銘柄です")
//                    return true
//                }

                val data = Forex.symbols[symbol]!!

                if (lots == null){
                    sender.sendMessage("${prefix}ロット数を数字で入力してください！")
                    return true
                }

                if (lots< data.minLot || lots> data.maxLot){
                    sender.sendMessage("${prefix}最小ロット:${data.minLot} 最大ロット:${data.maxLot}")
                    return true
                }

                entry(sender,lots,isBuy,symbol)

                return true
            }

            "board" ->{
                if (sender !is Player)return true
                showBalanceQueue.add(Func { showPriceBoard(sender,it) })
            }

            "history" ->{
                if (sender !is Player)return true

                val page = args[1].toIntOrNull()?:0

                showBalanceQueue.add(Func { showHistory(sender,it,page) })

            }

            "buy" ->{
                if (sender!is Player)return false

                if (args.size!=3){
                    sender.sendMessage("${prefix}/mfx buy <銘柄> <ロット数>")
                    return false
                }
                sender.performCommand("mfx entry b ${args[1]} ${args[2]}")
            }

            "sell" ->{
                if (sender!is Player)return false

                if (args.size!=3){
                    sender.sendMessage("${prefix}/mfx sell <銘柄> <ロット数>")
                    return false
                }
                sender.performCommand("mfx entry s ${args[1]} ${args[2]}")
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
                Forex.reload()
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

    fun interface Func{
        fun function(sql: MySQLManager)
    }

    private fun showQueue(){

        val sql = MySQLManager(plugin,"ShowBalanceForex")

        while (true){
            try {
                val func = showBalanceQueue.take()
                func.function(sql)

            }catch (e:java.lang.Exception){
                for (trace in e.stackTrace){
                    Bukkit.getLogger().info("§e§l${trace}")
                }
            }
        }
    }

    private fun showBalance(p:Player,sql:MySQLManager){

        val uuid = p.uniqueId
        val list = asyncGetUserPositions(uuid,sql)

        val balance = ForexBank.getBalance(uuid)
        val margin = margin(uuid,list)
        val percent = marginPercent(uuid, list)?:0.0
        val allProfit = allProfit(list)

        val profitColor = if (allProfit<0) "§4§l" else if (allProfit>0) "§b§l" else "§f§l"
        val percentColor =  if (percent==0.0) "§f§l" else if (percent< lossCutPercent*1.5) "§4§l" else if (percent< lossCutPercent*2.0) "§6§l"  else "§f§l"

        val percentMsg = text("${prefix}${percentColor}維持率:${format(percent,3)}%")
            .hoverEvent(HoverEvent.showText(text("§c§l維持率が20.0%を下回ると、ポジションが強制的に決済されます")))

        val balanceMsg = text("${prefix}残高:${moneyFormat(balance)}")
            .hoverEvent(HoverEvent.showText(text("§f§nFXの口座は、銀行口座と別のものを使用します")))

        val depositButton = text("               §a§n${isAllowed(Forex.MarketStatus.deposit)}[入金]")
            .clickEvent(ClickEvent.suggestCommand("/mfx d "))
            .hoverEvent(HoverEvent.showText(text("§f銀行のお金を、FX口座に入金します")))

        val withdrawButton = text("  §c§n${isAllowed(Forex.MarketStatus.withdraw)}[出金]")
            .clickEvent(ClickEvent.suggestCommand("/mfx w "))
            .hoverEvent(HoverEvent.showText(text("§fFX口座から、銀行に出金します\n§c§lポジションを持っているときは、出金できません")))

        val historyButton = text("               §e§l§n[履歴を見る]").clickEvent(ClickEvent.runCommand("/mfx history 0"))

        val title = "${prefix}§e§l=============[Man10Trader(MT10)]============="

        p.sendMessage(title)
        p.sendMessage(balanceMsg.append(depositButton).append(withdrawButton))
        p.sendMessage("${prefix}有効金額:${moneyFormat(margin)}")
        p.sendMessage("${prefix}${profitColor}評価額:${moneyFormat(allProfit)}")
        p.sendMessage(percentMsg.append(historyButton))
        p.sendMessage("${prefix}===============保有ポジション===============")

        if (Price.error){ p.sendMessage("${prefix}§c§l現在価格取得ができないため、エントリーなどができません") }
        if (!Price.isActiveTime()){
            p.sendMessage("${prefix}現在取引時間外です")
            return
        }

        val prefix = text(prefix)


        list.forEach {

            val profit = profit(it)

            val symbolText = "§e§l${it.symbol} "
            val lots = (if (it.buy) "§a§l買" else "§c§l売") + " ${it.lots}ロット "
            val openPrice = "O:${priceFormat(it.entryPrice)} "
            val profitText = if (profit>0.0) "§b§l${moneyFormat(profit)}円" else if(profit<0.0) "§4§l${moneyFormat(profit)}円" else "§f§l${moneyFormat(profit)}円"
            val diff = " (${format(Forex.diffPips(it),2)}Pips)"

            val positionDataText = "§7§lポジション情報\n" +
                    "${if (it.buy) "§a§l買" else "§c§l売"}ポジション\n" +
                    "§7銘柄:§l${it.symbol}\n" +
                    "§7ロット数:§l${it.lots}\n" +
                    "§7オープン価格:§l${priceFormat(it.entryPrice)}" +
                    "§7損益:${profit}${diff}"

            val positionData = text(symbolText+lots+openPrice+profitText+diff)
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

            p.sendMessage(prefix.append(positionData))
            p.sendMessage(prefix.append(exitButton).append(tpButton).append(slButton))

        }

        val boardButton = text("§e§l§n[エントリーする]")
            .clickEvent(ClickEvent.runCommand("/mfx board"))
            .hoverEvent(HoverEvent.showText(text("§f売買メニューを開きます")))

        p.sendMessage(prefix.append(boardButton))
    }

    private fun showPriceBoard(p:Player,sql: MySQLManager){
        p.sendMessage("${prefix}エントリーをする(価格は${sdf.format(Date())}時点のものです)")
        val list = asyncGetUserPositions(p.uniqueId,sql)

        val prefix = text(prefix)

        for (symbol in Forex.symbolList){

            val digits = symbols[symbol]?.pipsAmount.toString().length

            val symbolText = text("§e§l${symbol} ")
            val sellButton = text("§c§l§n${isAllowed(Forex.MarketStatus.entry)}[売(${format(Price.ask(symbol),digits)})]")
                .clickEvent(ClickEvent.suggestCommand("/mfx sell $symbol "))
                .hoverEvent(HoverEvent.showText(text("§c現在価格より下回ったら利益がでます\n§c/mfx sell $symbol <ロット数>(0.01〜1000)")))
            val maxLot = text(" §f§l最大${format(getMaxLots(p.uniqueId,Price.price(symbol),list,symbol),2)}ロット ")
            val buyButton = text("§a§l§n${isAllowed(Forex.MarketStatus.entry)}[買(${format(Price.bid(symbol),digits)})]")
                .clickEvent(ClickEvent.suggestCommand("/mfx buy $symbol "))
                .hoverEvent(HoverEvent.showText(text("§a現在価格より上回ったら利益がでます\n§a/mfx buy $symbol <ロット数>(0.01〜1000)")))

            p.sendMessage(prefix.append(symbolText).append(sellButton).append(maxLot).append(buyButton))
        }

    }

    private fun showHistory(p:Player, sql:MySQLManager, page:Int = 0){

        val rs = sql.query("select * from position_table where uuid='${p.uniqueId}' and `exit`=1 order by exit_date desc;")?:return

        val list = mutableListOf<PositionHistory>()

        while (rs.next()){

            val data = PositionHistory(
                rs.getString("entry_date"),
                rs.getString("exit_date"),
                rs.getString("symbol"),
                rs.getInt("buy") == 1,
                rs.getDouble("entry_price"),
                rs.getDouble("exit_price"),
                rs.getDouble("lots"),
                rs.getDouble("profit")
            )

            list.add(data)
        }

        val totalProfit = list.sumOf { it.profit }
        val hasPrevious = page>0
        var hasNext = true

        p.sendMessage("${prefix}§e§l=====トレードヒストリー=====")

        for (i in page*10 until page*10+10){
            if (list.size<=i){
                hasNext = false
                break
            }

            val data = list[i]

            val hover = text("${if (data.isBuy) "§b§l買" else "§c§l売"}\n" +
                    "§f§l銘柄:${data.symbol}\n" +
                    "§f§l${ format(data.lot,2)}ロット\n" +
                    "§f§l${format(data.entry,3)}→${format(data.exit)}\n" +
                    "§f§l損益:${if (data.profit>0) "§b§l" else if (data.profit<0) "§c§l" else ""} ${format(data.profit,0)}円\n" +
                    "§f§l決済時刻:${data.exitDate}")

            val msg = text("$prefix${if (data.isBuy) "§b§l買" else "§c§l売"} §f§l${ format(data.lot,2)}ロット ${data.symbol} " +
                    "${if (data.profit>0) "§b§l" else if (data.profit<0) "§c§l" else ""} ${format(data.profit,0)}円").hoverEvent(HoverEvent.showText(hover))

            p.sendMessage(msg)
        }

        p.sendMessage("${prefix}§a§lトータル損益:${if (totalProfit>=0) "§b§l" else "§c§l"} ${format(totalProfit,0)}円")

        var prefix = text(prefix)
        val previous = text("§f§l§n[前のページ]").clickEvent(ClickEvent.runCommand("/mfx history ${page-1}"))
        val next = text("§f§l§n[次のページ]").clickEvent(ClickEvent.runCommand("/mfx history ${page+1}"))

        if (hasPrevious){
            prefix = prefix.append(previous)
        }
        if (hasNext){
            prefix = prefix.append(next)
        }

        p.sendMessage(prefix)
    }

    private fun showBalanceOP(p:CommandSender,sql: MySQLManager,mcid:String){

        val uuid = Bank.getUUID(mcid)?:return
        val list = asyncGetUserPositions(uuid,sql)

        val balance = ForexBank.getBalance(uuid)
        val margin = margin(uuid,list)
        val percent = marginPercent(uuid, list)?:0.0
        val allProfit = allProfit(list)

        val profitColor = if (allProfit<0) "§4§l" else if (allProfit>0) "§b§l" else "§f§l"
        val percentColor =  if (percent==0.0) "§f§l" else if (percent< lossCutPercent*1.5) "§4§l" else if (percent< lossCutPercent*2.0) "§6§l"  else "§f§l"

        val percentMsg = text("${prefix}${percentColor}維持率:${format(percent,3)}%")
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
            val diff = " (${format(Forex.diffPips(it),2)}Pips)"

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

    data class PositionHistory(
        var entryDate : String,
        var exitDate : String,
        var symbol : String,
        var isBuy : Boolean,
        var entry : Double,
        var exit : Double,
        var lot : Double,
        var profit : Double
    )
}