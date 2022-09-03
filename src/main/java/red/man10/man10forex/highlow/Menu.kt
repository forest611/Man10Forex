package red.man10.man10forex.highlow

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10forex.highlow.HighLowGame.minSecond
import red.man10.man10forex.util.MenuFramework

class Menu(p: Player) : MenuFramework(p,45,"${HighLowGame.prefix}§d§l上か下かを賭けよう！"){

    companion object{
        val userData = HashMap<Player,Boolean>()//trueでhigh
    }

    init {
        fill(Button(Material.WHITE_STAINED_GLASS_PANE))

        val highLowData : Boolean? = userData[p]

        val lowButton = Button(Material.RED_STAINED_GLASS_PANE)
        lowButton.title("§c§l下がると予想する！(価格が下がったら勝ち！)")
        lowButton.lore(mutableListOf("§bクリックして予想を決定！","§c§l価格が変わらなかった場合は負けとなります"))
        lowButton.setClickAction{ userData[p] = false;Menu(p).open() }

        if (highLowData!=null && highLowData == false){ lowButton.enchant(true) }

        arrayOf(10,11,12,19,20,21,28,29,30).forEach { setButton(lowButton,it) }

        val highButton = Button(Material.LIME_STAINED_GLASS_PANE)
        highButton.title("§a§l上がると予想する！(価格が上がったら勝ち！)")
        highButton.lore(mutableListOf("§bクリックして予想を決定！","§c§l価格が変わらなかった場合は負けとなります"))
        highButton.setClickAction{ userData[p] = true;Menu(p).open() }

        if (highLowData!=null && highLowData == true){ highButton.enchant(true) }

        arrayOf(14,15,16,23,24,25,32,33,34).forEach { setButton(highButton,it) }

        if (highLowData !=null){

            val betButton1 = Button(Material.IRON_INGOT)
            val bet1Price = 100.0
            betButton1.cmd(1)
            betButton1.title("§e${String.format("%,.0f", bet1Price)}円賭ける！")
            betButton1.lore(mutableListOf("§b§l銀行のお金が消費されます！",
                "§aクリックした約${minSecond}秒後の価格が予想通りなら",
                "§a${String.format("%,.0f", bet1Price*2.0)}円手に入れることができます"))
            betButton1.setClickAction{entry(p, bet1Price, minSecond)}
            setButton(betButton1,38)

            val betButton2 = Button(Material.GOLD_INGOT)
            val bet2Price = 1000.0
            betButton2.cmd(1)
            betButton2.title("§e${String.format("%,.0f", bet2Price)}円賭ける！")
            betButton2.lore(mutableListOf("§b§l銀行のお金が消費されます！",
                "§aクリックした約${minSecond}秒後の価格が予想通りなら",
                "§a${String.format("%,.0f", bet2Price*2.0)}円手に入れることができます"))
            betButton2.setClickAction{entry(p, bet2Price, minSecond)}
            setButton(betButton2,39)

            val betButton3 = Button(Material.DIAMOND)
            val bet3Price = 10000.0
            betButton3.cmd(1)
            betButton3.title("§e${String.format("%,.0f", bet3Price)}円賭ける！")
            betButton3.lore(mutableListOf("§b§l銀行のお金が消費されます！",
                "§aクリックした約${minSecond}秒後の価格が予想通りなら",
                "§a${String.format("%,.0f", bet3Price*2.0)}円手に入れることができます"))
            betButton3.setClickAction{entry(p, bet3Price, minSecond)}
            setButton(betButton3,40)

            val betButton4 = Button(Material.EMERALD)
            val bet4Price = 100000.0
            betButton4.cmd(1)
            betButton4.title("§e${String.format("%,.0f", bet4Price)}円賭ける！")
            betButton4.lore(mutableListOf("§b§l銀行のお金が消費されます！",
                "§aクリックした約${minSecond}秒後の価格が予想通りなら",
                "§a${String.format("%,.0f", bet4Price*2.0)}円手に入れることができます"))
            betButton4.setClickAction{entry(p, bet4Price, minSecond)}
            setButton(betButton4,41)

            val betButton5 = Button(Material.NETHERITE_INGOT)
            val bet5Price = 1000000.0
            betButton5.cmd(1)
            betButton5.title("§e${String.format("%,.0f", bet5Price)}円賭ける！")
            betButton5.lore(mutableListOf("§b§l銀行のお金が消費されます！",
                "§aクリックした約${minSecond}秒後の価格が予想通りなら",
                "§a${String.format("%,.0f", bet5Price*2.0)}円手に入れることができます"))
            betButton5.setClickAction{entry(p, bet5Price, minSecond)}
            setButton(betButton5,42)


        }

    }

    private fun entry(p:Player,price:Double,sec:Int){
        p.performCommand("mhl bet $price $sec ${getHighLow(p)}")
        userData.remove(p)
        p.closeInventory()
    }

    private fun getHighLow(p:Player):String{
        val b = userData[p]?:return ""
        if (b)return "h"
        return "l"
    }

}