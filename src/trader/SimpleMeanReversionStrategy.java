package trader;

import market.Market;
import model.Order;
import model.OrderSide;
import model.OrderType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class SimpleMeanReversionStrategy implements Strategy {
    private final String ticker;
    private BigDecimal lastSeen = null;

    public SimpleMeanReversionStrategy(String ticker) {
        this.ticker = ticker;
    }

    @Override
    public List<Order> generateOrders(Market market, Trader trader, long tick) {
        List<Order> orders = new ArrayList<>();
        BigDecimal price = market.getLastPrice(ticker);
        if (price == null) return orders;
        if (lastSeen == null) {
            lastSeen = price;
            return orders;
        }
        int qty = 10;
        int compare = price.compareTo(lastSeen);
        if (compare < 0) {
            // price dropped — buy limit at price - small offset
            BigDecimal limit = price.subtract(new BigDecimal("0.05"));
            if (limit.signum() > 0) {
                orders.add(new Order(trader.id, ticker, OrderSide.BUY, OrderType.LIMIT, limit, qty));
            }
        } else if (compare > 0) {
            // price rose — place sell limit at price + offset if has position
            if (trader.portfolio.getPositions().get(ticker) != null) {
                BigDecimal limit = price.add(new BigDecimal("0.05"));
                orders.add(new Order(trader.id, ticker, OrderSide.SELL, OrderType.LIMIT, limit, qty));
            }
        }
        lastSeen = price;
        return orders;
    }
}
