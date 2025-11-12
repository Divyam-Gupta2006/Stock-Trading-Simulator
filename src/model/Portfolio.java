package model;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Portfolio {
    private BigDecimal cash;
    private final Map<String, Position> positions = new HashMap<>();

    public Portfolio(BigDecimal initialCash) {
        this.cash = initialCash;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public Map<String, Position> getPositions() {
        return positions;
    }

    public void changeCash(BigDecimal delta) {
        this.cash = this.cash.add(delta);
    }

    public void updatePosition(String ticker, int quantityDelta, BigDecimal tradePrice) {
        Position pos = positions.get(ticker);
        if (pos == null) {
            if (quantityDelta == 0) return;
            pos = new Position(quantityDelta, tradePrice);
            positions.put(ticker, pos);
            return;
        }
        int oldQty = pos.quantity;
        BigDecimal oldAvg = pos.avgPrice;
        int newQty = oldQty + quantityDelta;

        if (oldQty == 0) {
            pos.avgPrice = tradePrice;
            pos.quantity = newQty;
            if (newQty == 0) positions.remove(ticker);
            return;
        }

        if ((oldQty > 0 && newQty > 0) || (oldQty < 0 && newQty < 0)) {
            // same direction: weighted average
            BigDecimal totalCostOld = oldAvg.multiply(BigDecimal.valueOf(Math.abs(oldQty)));
            BigDecimal totalCostNew = tradePrice.multiply(BigDecimal.valueOf(Math.abs(quantityDelta)));
            BigDecimal combined = totalCostOld.add(totalCostNew);
            int combinedQty = Math.abs(oldQty) + Math.abs(quantityDelta);
            pos.avgPrice = combined.divide(BigDecimal.valueOf(combinedQty), BigDecimal.ROUND_HALF_UP);
            pos.quantity = newQty;
        } else {
            // crossing zero or reducing position - keep avgPrice for remaining qty if any
            pos.quantity = newQty;
            if (newQty == 0) positions.remove(ticker);
        }
    }

    @Override
    public String toString() {
        return "Portfolio{" +
                "cash=" + cash.toPlainString() +
                ", positions=" + positions +
                '}';
    }
}
