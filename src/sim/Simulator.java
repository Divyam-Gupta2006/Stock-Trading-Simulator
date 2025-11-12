package sim;

import market.Market;
import market.MarketDataFeed;
import market.MatchingEngine;
import model.Order;
import model.OrderSide;
import model.OrderType;
import model.Trade;
import trader.SimpleMeanReversionStrategy;
import trader.Strategy;
import trader.Trader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Simulator {
    private final Market market;
    private final MarketDataFeed feed;
    private final MatchingEngine engine;
    private final Map<String, Trader> traders = new HashMap<>();
    private final Map<String, Strategy> strategies = new HashMap<>();
    private final List<Trade> tradeLog = new ArrayList<>();
    private long tick = 0;

    public Simulator(Market market, MarketDataFeed feed) {
        this.market = market;
        this.feed = feed;
        this.engine = new MatchingEngine(market);
    }

    public void registerTrader(Trader t, Strategy s) {
        traders.put(t.id, t);
        if (s != null) strategies.put(t.id, s);
    }

    public void submitOrder(Order o) {
        engine.submitOrder(o, traders);
    }

    public void runForTicks(int n) {
        for (int i = 0; i < n; i++) {
            tick++;
            // update market prices
            for (String t : market.getOrderBook("FAKE") == null ? marketPricesList() : marketPricesList()) {
                feed.tickPrice(t);
                BigDecimal p = feed.getPrice(t);
                market.setLastPrice(t, p);
            }
            // run strategies
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                Trader tr = traders.get(entry.getKey());
                List<Order> orders = entry.getValue().generateOrders(market, tr, tick);
                for (Order o : orders) submitOrder(o);
            }
            // collect trades
            List<Trade> ts = engine.getAndClearTrades();
            tradeLog.addAll(ts);
            // Print summary every tick
            System.out.println("Tick " + tick + " prices:");
            for (var e : market.getPrices().entrySet()) {
                // round each price to 2 decimal places for neat output
                String formatted = e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
                System.out.println("  " + e.getKey() + " -> " + formatted);
            }

        }
    }

    private Set<String> marketPricesList() {
        // obtain tickers from market by peeking at keys of market's books map (reflection not allowed; keep a copy)
        // For simplicity, we assume feed and market have same tickers. We'll fetch from feed internal map via known method not available.
        // Instead pass tickers externally or keep list; for quick demo, we'll embed tickers in simulator.
        return new HashSet<>(List.of("AAPL", "GOOG", "MSFT"));
    }

    public List<Trade> getTradeLog() { return tradeLog; }

    public Map<String, Trader> getTraders() { return traders; }
    
    public MarketDataFeed getFeed() { return this.feed; }
}
