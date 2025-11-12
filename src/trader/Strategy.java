package trader;

import market.Market;
import model.Order;

import java.util.List;

public interface Strategy {
    /**
     * Called each tick. Strategy may return 0..N orders to submit.
     */
    List<Order> generateOrders(Market market, Trader trader, long tick);
}
