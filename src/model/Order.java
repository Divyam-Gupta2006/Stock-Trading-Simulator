package model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class Order {
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    public final long id;
    public final String traderId;
    public final String ticker;
    public final OrderSide side;
    public final OrderType type;
    public final BigDecimal price; // null for market orders
    public final int originalQuantity;
    private int remainingQuantity;
    public final Instant timestamp;

    public Order(String traderId, String ticker, OrderSide side, OrderType type, BigDecimal price, int quantity) {
        this.id = ID_GEN.getAndIncrement();
        this.traderId = traderId;
        this.ticker = ticker;
        this.side = side;
        this.type = type;
        this.price = price;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = Instant.now();
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public void reduceRemaining(int q) {
        if (q < 0) throw new IllegalArgumentException("q < 0");
        if (q > remainingQuantity) throw new IllegalArgumentException("reduce > remaining");
        remainingQuantity -= q;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, trader=%s, %s %s %s x%d rem=%d}",
                id, traderId, side, type, ticker, originalQuantity, remainingQuantity);
    }
}
