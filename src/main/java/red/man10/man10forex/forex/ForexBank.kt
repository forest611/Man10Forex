package red.man10.man10forex.forex

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import red.man10.man10forex.Man10Forex.Companion.plugin
import red.man10.man10forex.forex.ForexBank.IntTransaction
import red.man10.man10forex.forex.ForexBank.PairTransaction
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.MySQLManager.Companion.mysqlQueue
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

object ForexBank :Listener{


    private lateinit var mysql : MySQLManager
    private var bankQueue = LinkedBlockingQueue<Pair<Any,ResultTransaction>>()
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    init {
        Bukkit.getLogger().info("StartFXBankQueue")
        Thread{bankQueue()}.start()
    }

    //////////////////////////////////
    //口座を持っているかどうか
    //////////////////////////////////
    private fun hasAccount(uuid: UUID):Boolean{

        val rs = mysql.query("SELECT balance FROM fx_bank WHERE uuid='$uuid'")?:return false

        if (rs.next()) {
            mysql.close()
            rs.close()
            return true
        }

        mysql.close()
        rs.close()

        return false

    }

    /////////////////////////////////////
    //新規口座作成 既に持っていたら作らない
    /////////////////////////////////////
    private fun createAccount(uuid: UUID):Boolean{

        if (hasAccount(uuid))return false

        val p = Bukkit.getOfflinePlayer(uuid)

        val ret  = mysql.execute("INSERT INTO fx_bank (player, uuid, balance) VALUES ('${p.name}', '$uuid', 0);")

        if (!ret)return false

        addLog(uuid,plugin.name,"CreateAccount","口座を作成",0.0,true)

        return true
    }

    /**
     * ログを生成
     *
     * @param plugin 処理を行ったプラグインの名前
     * @param note ログの内容 (max64)
     * @param amount 動いた金額
     */
    private fun addLog(uuid: UUID,plugin:String,note:String,displayNote: String,amount:Double,isDeposit:Boolean){

        val p = Bukkit.getOfflinePlayer(uuid)

        mysqlQueue.add("INSERT INTO bank_log (player, uuid, plugin_name, amount, server, note,display_note, deposit) " +
                "VALUES " +
                "('${p.name}', " +
                "'$uuid', " +
                "'${plugin}', " +
                "$amount, " +
                "'paper', " +
                "'$note', " +
                "'${displayNote}'," +
                "${if (isDeposit) 1 else 0});")

    }


    fun setBalance(uuid:UUID,amount: Double){

        val sql = MySQLManager(plugin,"Man10Bank")

        if (amount <0.0)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        val ret = sql.execute("update fx_bank set balance=$amount where uuid='$uuid';")

        if (!ret)return

        addLog(uuid,plugin.name, "SetBalanceByCommand","所持金を${String.format("%,.0f", amount)}にセット", amount,true)
    }

    /**
     * オフライン口座の残高を確認する
     *
     * @param uuid ユーザーのuuid*
     * @return 残高 存在しないユーザーだった場合、0.0が返される
     */
    private fun getBalanceQueue(uuid:UUID):Pair<Double,Int>{

        var bal = 0.0

        val rs = mysql.query("SELECT balance FROM fx_bank WHERE uuid='$uuid';")?:return Pair(bal,2)

        if (!rs.next()){
            mysql.close()
            rs.close()
            return Pair(bal,3)
        }

        bal = rs.getDouble("balance")

        rs.close()
        mysql.close()

        return Pair(bal,0)
    }

    /**
     * オフライン口座に入金する
     *
     * @param plugin 入金したプラグイン
     * @param note 入金の内容(64文字以内)
     * @param amount 入金額(マイナスだった場合、入金処理は行われない)
     *
     */
    private fun depositQueue(uuid: UUID, amount: Double, plugin: String, note:String,displayNote:String?):Int{

        if (!Forex.isEnable){ return 1 }

        val finalAmount = kotlin.math.floor(amount)

        val ret = mysql.execute("update fx_bank set balance=balance+$finalAmount where uuid='$uuid';")

        if (!ret){ return 2 }

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,true)

        return 0
    }

    /**
     * オフライン口座から出金する
     *
     * @param plugin 出金したプラグイン
     * @param note 出金の内容(64文字以内)
     * @param amount 出金額(マイナスだった場合、入金処理は行われない)
     *
     * @return　出金成功でtrue
     */
    private fun withdrawQueue(uuid: UUID, amount: Double, plugin: String, note:String,displayNote:String?):Int{

        val p = Bukkit.getOfflinePlayer(uuid)

        if (!Forex.isEnable){
            Bukkit.getLogger().warning("[出金エラー]Man10Bankが閉じています ユーザー:${p.name}")
            return 1
        }

        val finalAmount = kotlin.math.floor(amount)
        val balance = getBalanceQueue(uuid).first

        if (balance < finalAmount){ return 2 }

        val ret = mysql.execute("update fx_bank set balance=balance-${finalAmount} where uuid='$uuid';")

        if (!ret){ return 3 }

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,false)

        return 0
    }

    private fun changeName(player: Player){
        mysqlQueue.add("update fx_bank set player='${player.name}' where uuid='${player.uniqueId}';")
    }

    fun getBankLog(p:Player,page:Int): MutableList<BankLog> {

        val sql = MySQLManager(plugin,"Man10Bank")

        val rs = sql.query("select * from bank_log where uuid='${p.uniqueId}' order by id desc Limit 10 offset ${(page)*10};")?:return Collections.emptyList()

        val list = mutableListOf<BankLog>()

        while (rs.next()){

            val data = BankLog()

            data.isDeposit = rs.getInt("deposit") == 1
            data.amount = rs.getDouble("amount")
            data.note = rs.getString("display_note")?:rs.getString("note")!!
            data.dateFormat = simpleDateFormat.format(rs.getTimestamp("date"))

            list.add(data)
        }

        sql.close()
        rs.close()

        return list

    }

    fun interface ResultTransaction{ fun onTransactionResult(errorCode:Int, amount:Double, errorMessage: String) }
    fun interface IntTransaction{ fun transaction():Int }
    fun interface PairTransaction{ fun transaction():Pair<Double,Int> }

    private fun bankQueue(){

        mysql  = MySQLManager(plugin,"Man10OfflineBank")

        while (true){
            val bankTransaction = bankQueue.take()

            val transaction = bankTransaction.first

            var errorCode = 0
            var amount = 1.0
            var errorMessage = ""

            try {
                if (transaction is IntTransaction){ errorCode = transaction.transaction() }

                if (transaction is PairTransaction){
                    val ret = transaction.transaction()
                    amount = ret.first
                    errorCode = ret.second
                }
            }catch (e:Exception){
                errorMessage = e.message.toString()
                Bukkit.getLogger().info("Man10BankQueueエラー:${errorMessage}")
            }finally {
                bankTransaction.second.onTransactionResult(errorCode,amount,errorMessage)
            }

        }
    }

    private fun addTransactionQueue(transaction: Any, transactionCallBack: ResultTransaction):ResultTransaction{
        bankQueue.add(Pair(transaction,transactionCallBack))
        return transactionCallBack
    }

    private fun asyncDeposit(uuid: UUID, amount: Double, note:String, displayNote:String?, callback:ResultTransaction){

        val transaction = IntTransaction { return@IntTransaction depositQueue(uuid,amount,plugin.name,note,displayNote) }

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callback.onTransactionResult(_code,_amount,_message)
        }
    }

    private fun asyncWithdraw(uuid: UUID, amount: Double, note:String, displayNote:String?, callback: ResultTransaction){

        val transaction = IntTransaction { return@IntTransaction withdrawQueue(uuid,amount,plugin.name,note,displayNote) }

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callback.onTransactionResult(_code,_amount,_message)
        }
    }

    /**
     * 金額を取得する処理
     */
    private fun asyncGetBalance(uuid: UUID,callback: ResultTransaction){

        val transaction = PairTransaction { return@PairTransaction getBalanceQueue(uuid) }

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callback.onTransactionResult(_code,_amount,_message)
        }
    }

    /**
     * 同期で入金する処理
     */
    fun deposit(uuid: UUID, amount: Double,note:String,displayNote:String?): Boolean {

        var ret = Triple(-1,0.0,"")

        val lock = Lock()

        asyncDeposit(uuid,amount,note,displayNote) { _code: Int, _amount: Double, _message: String ->
            ret = Triple(_code,_amount,_message)
            lock.unlock()
        }

        lock.lock()
        return ret.first == 0
    }

    /**
     * 同期で出金する処理
     */
    fun withdraw(uuid: UUID, amount: Double, note:String,displayNote:String?): Boolean {

        var ret = Triple(-1,0.0,"")

        val lock = Lock()

        asyncWithdraw(uuid,amount,note,displayNote) { _code: Int, _amount: Double, _message: String ->
            ret = Triple(_code,_amount,_message)
            lock.unlock()
        }

        lock.lock()

        return ret.first == 0
    }

    /**
     * 金額を取得する処理
     */
    fun getBalance(uuid: UUID):Double{
        var amount = -1.0

        val lock = Lock()

        asyncGetBalance(uuid){ _, _amount, _ ->
            amount = _amount
            lock.unlock()
        }

        lock.lock()

        return amount

    }

    ////////////////////////////////
    //講座の作成処理など
    /////////////////////////////////
    fun initUserData(p:Player){
        val t = IntTransaction {
            createAccount(p.uniqueId)
            changeName(p)

            return@IntTransaction 0
        }

        addTransactionQueue(t) { _, _, _ -> }
    }

    @EventHandler
    fun login(e:PlayerJoinEvent){
        initUserData(e.player)
    }

    class BankLog{

        var isDeposit = true
        var amount = 0.0
        var note = ""
        var dateFormat = ""

    }

    class Lock{

        @Volatile
        private  var isLock = false

        fun lock(){
            synchronized(this){ isLock = true }
            while (isLock){ Thread.sleep(1) }
        }

        fun unlock(){
            synchronized(this){ isLock = false }
        }
    }

}
