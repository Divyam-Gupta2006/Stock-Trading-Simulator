package model;

import java.math.BigDecimal;

public class Position {
    public int quantity;
    public BigDecimal avgPrice;

    public Position(int quantity, BigDecimal avgPrice) {
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }

    @Override
    public String toString() {
        return String.format("Position{qty=%d, avg=%s}", quantity, avgPrice.toPlainString());
    }
}
