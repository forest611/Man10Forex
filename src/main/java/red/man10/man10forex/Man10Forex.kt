package red.man10.man10forex

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10forex.forex.Forex
import red.man10.man10forex.forex.ForexBank
import red.man10.man10forex.highlow.Command
import red.man10.man10forex.highlow.HighLowGame
import red.man10.man10forex.util.MenuFramework
import red.man10.man10forex.util.MySQLManager
import red.man10.man10forex.util.Price

class Man10Forex : JavaPlugin() {

    companion object{
        lateinit var plugin: JavaPlugin
        lateinit var bank: BankAPI

        const val OP = "mforex.op"
        const val HIGHLOW_USER = "binary.user"
        const val FOREX_USER = "forex.user"

        val positionThread = Thread{ Forex.positionThread() }
        val queueThread = Thread{ Forex.queueThread() }
    }
    override fun onEnable() {
        // Plugin startup logic
        plugin = this
        bank = BankAPI(this)

        saveDefaultConfig()

        HighLowGame.loadConfig()
        Forex.loadConfig()

        server.getPluginCommand("mhl")!!.setExecutor(Command)
        server.getPluginCommand("zfx")!!.setExecutor(Price)
        server.getPluginCommand("mfx")!!.setExecutor(red.man10.man10forex.forex.Command)

        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)
        server.pluginManager.registerEvents(ForexBank,this)
        MySQLManager.runAsyncMySQLQueue(plugin,"Man10Forex")

        positionThread.start()
        queueThread.start()

    }

    override fun onDisable() {
        // Plugin shutdown logic

    }
}