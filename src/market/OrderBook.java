package market;

import model.Order;
import model.OrderSide;

import java.math.BigDecimal;
import java.util.*;

public class OrderBook {
    // For bids: highest price first. For asks: lowest price first.
    private final PriorityQueue<Order> bids = new PriorityQueue<>((a, b) -> {
        int cmp = b.price.compareTo(a.price);
        if (cmp != 0) return cmp;
        return a.timestamp.compareTo(b.timestamp);
    });

    private final PriorityQueue<Order> asks = new PriorityQueue<>((a, b) -> {
        int cmp = a.price.compareTo(b.price);
        if (cmp != 0) return cmp;
        return a.timestamp.compareTo(b.timestamp);
    });

    private final Map<Long, Order> allOrders = new HashMap<>();
    private final String ticker;

    public OrderBook(String ticker) {
        this.ticker = ticker;
    }

    public void addOrder(Order o) {
        allOrders.put(o.id, o);
        if (o.type == null) throw new IllegalStateException("type null");
        if (o.type == null) return;
        if (o.type == null) return;
        if (o.side == OrderSide.BUY) {
            if (o.type == model.OrderType.MARKET) { // treat as very high bid
                bids.add(o);
            } else {
                bids.add(o);
            }
        } else {
            if (o.type == model.OrderType.MARKET) {
                asks.add(o);
            } else {
                asks.add(o);
            }
        }
    }

    public void cancelOrder(long orderId) {
        Order o = allOrders.remove(orderId);
        if (o != null) {
            if (o.side == OrderSide.BUY) bids.remove(o); else asks.remove(o);
        }
    }

    public PriorityQueue<Order> getBids() { return bids; }
    public PriorityQueue<Order> getAsks() { return asks; }

    public Optional<Order> topBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.peek());
    }

    public Optional<Order> topAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.peek());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OrderBook ").append(ticker).append("\nBIDS:\n");
        bids.stream().limit(5).forEach(o -> sb.append(o.price).append(" x").append(o.getRemainingQuantity()).append("\n"));
        sb.append("ASKS:\n");
        asks.stream().limit(5).forEach(o -> sb.append(o.price).append(" x").append(o.getRemainingQuantity()).append("\n"));
        return sb.toString();
    }
}
