package model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class Trade {
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    public final long id;
    public final long buyOrderId;
    public final long sellOrderId;
    public final String ticker;
    public final BigDecimal price;
    public final int quantity;
    public final Instant timestamp;

    public Trade(long buyOrderId, long sellOrderId, String ticker, BigDecimal price, int quantity) {
        this.id = ID_GEN.getAndIncrement();
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.ticker = ticker;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, %s %d @ %s (%s<->%s)}", id, ticker, quantity, price.toPlainString(), buyOrderId, sellOrderId);
    }
}
