package red.man10.man10forex.highlow

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10forex.Man10Forex.Companion.BINARY_USER

object Command : CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (label!="mhl")return false

        if (args.isNullOrEmpty()){
            if (!sender.hasPermission(BINARY_USER))return false

            return true
        }


        return false
    }
}