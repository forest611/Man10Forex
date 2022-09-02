package red.man10.man10forex

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10forex.highlow.Command
import red.man10.man10forex.highlow.HighLowGame
import red.man10.man10forex.util.Price

class Man10Forex : JavaPlugin() {

    companion object{
        lateinit var plugin: JavaPlugin
        lateinit var bank: BankAPI

        const val OP = "mforex.op"
        const val BINARY_USER = "binary.user"
        const val FOREX_USER = "forex.user"
    }
    override fun onEnable() {
        // Plugin startup logic
        plugin = this
        bank = BankAPI(this)
        server.getPluginCommand("mhl")!!.setExecutor(Command)
        server.getPluginCommand("mprice")!!.setExecutor(Price)
        Thread{
            HighLowGame.highLowThread()
        }.start()
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}