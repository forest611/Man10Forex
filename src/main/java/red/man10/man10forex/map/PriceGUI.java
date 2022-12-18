package red.man10.man10forex.map;

import red.man10.man10forex.forex.Forex;
import red.man10.man10forex.util.Price;
import red.man10.man10forex.util.Utility;

import java.awt.*;
import java.util.HashMap;
import java.util.List;

public class PriceGUI extends MappApp {
    private static final String appName = "PriceGUI";
    private static final int drawCycle = 10; //毎秒更新

    private static final HashMap<Integer,String > dataMap = new HashMap<>();

    public static void register() {

        MappRenderer.displayTouchEvent(appName, ((key, mapId, player, x, y) -> {

            List<String> symbols = Forex.INSTANCE.getSymbolList();

            String nowSymbol = dataMap.get(mapId);

            if (nowSymbol == null){
                dataMap.put(mapId,symbols.get(0));
                return true;
            }

            int index = symbols.indexOf(nowSymbol);

            String newSymbol;

            if (index+1>=symbols.size()){
                newSymbol = symbols.get(0);
            }else {
                newSymbol = symbols.get(index+1);
            }

            dataMap.put(mapId,newSymbol);

            return true;
        }));

        MappRenderer.draw(appName,drawCycle,(key, mapId, g) -> {


            String symbol = dataMap.get(mapId);

            if (symbol == null){
                symbol = "USDJPY";
            }

            g.setColor(Color.DARK_GRAY);
            g.fillRect(0,0,128,128);

            double ask = Price.INSTANCE.ask(symbol);
            double bid = Price.INSTANCE.bid(symbol);

            g.setFont(new Font("SansSerif",Font.BOLD,15));

            g.setColor(Color.YELLOW);
            g.drawString("銘柄:"+symbol,10,30);
            g.setColor(Color.RED);
            g.drawString("買値:"+Utility.INSTANCE.format(ask,3),10,60);
            g.setColor(Color.CYAN);
            g.drawString("売値:"+Utility.INSTANCE.format(bid,3),10,90);

            return true;
        });


    }
}