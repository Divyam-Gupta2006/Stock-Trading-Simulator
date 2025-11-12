package market;

import market.OrderBook;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Market {
    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<String, BigDecimal> lastPrices = new HashMap<>();

    public Market(Iterable<String> tickers, Map<String, BigDecimal> initialPrices) {
        for (String t : tickers) {
            books.put(t, new OrderBook(t));
            lastPrices.put(t, initialPrices.getOrDefault(t, new BigDecimal("10.00")));
        }
    }

    public OrderBook getOrderBook(String ticker) {
        return books.get(ticker);
    }

    public void setLastPrice(String ticker, BigDecimal price) {
        lastPrices.put(ticker, price);
    }

    public BigDecimal getLastPrice(String ticker) {
        return lastPrices.get(ticker);
    }
    

    public Map<String, BigDecimal> getPrices() {
        return lastPrices;
    }


}
