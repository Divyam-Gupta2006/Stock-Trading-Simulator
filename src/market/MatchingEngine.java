package market;

import model.Order;
import model.OrderSide;
import model.Trade;
import trader.Trader;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MatchingEngine {
    private final Market market;
    private final List<Trade> trades = new ArrayList<>();

    public MatchingEngine(Market market) {
        this.market = market;
    }

    public List<Trade> getAndClearTrades() {
        List<Trade> copy = new ArrayList<>(trades);
        trades.clear();
        return copy;
    }

    /**
     * Enter order into the book for ticker and attempt matches immediately.
     */
    public void submitOrder(Order o, Map<String, Trader> traderRegistry) {
        OrderBook ob = market.getOrderBook(o.ticker);
        if (ob == null) {
            System.err.println("No such ticker: " + o.ticker);
            return;
        }

        // MARKET order matches against top of opposite book at their price.
        if (o.type == model.OrderType.MARKET) {
            matchMarketOrder(o, ob, traderRegistry);
            if (!o.isFilled()) {
                // leftover of market order is discarded (or could be queued) — we will discard.
                System.out.println("Market order not fully filled, leftover cancelled: " + o);
            }
            return;
        }

        // LIMIT order: try to match, otherwise add to book
        matchLimitOrder(o, ob, traderRegistry);
        if (!o.isFilled()) {
            ob.addOrder(o); // add remaining as resting order
        }
    }

    private void matchMarketOrder(Order o, OrderBook ob, Map<String, Trader> traderRegistry) {
        if (o.side == OrderSide.BUY) {
            while (!o.isFilled()) {
                var askOpt = ob.topAsk();
                if (askOpt.isEmpty()) break;
                Order ask = askOpt.get();
                executeMatch(o, ask, ob, traderRegistry);
            }
        } else {
            while (!o.isFilled()) {
                var bidOpt = ob.topBid();
                if (bidOpt.isEmpty()) break;
                Order bid = bidOpt.get();
                executeMatch(bid, o, ob, traderRegistry);
            }
        }
    }

    private void matchLimitOrder(Order o, OrderBook ob, Map<String, Trader> traderRegistry) {
        if (o.side == OrderSide.BUY) {
            while (!o.isFilled()) {
                var askOpt = ob.topAsk();
                if (askOpt.isEmpty()) break;
                Order ask = askOpt.get();
                // match if ask.price <= buy limit price
                if (ask.price.compareTo(o.price) <= 0) {
                    executeMatch(o, ask, ob, traderRegistry);
                } else break;
            }
        } else {
            while (!o.isFilled()) {
                var bidOpt = ob.topBid();
                if (bidOpt.isEmpty()) break;
                Order bid = bidOpt.get();
                if (bid.price.compareTo(o.price) >= 0) {
                    executeMatch(bid, o, ob, traderRegistry);
                } else break;
            }
        }
    }

    private void executeMatch(Order buy, Order sell, OrderBook ob, Map<String, Trader> traderRegistry) {
        int q = Math.min(buy.getRemainingQuantity(), sell.getRemainingQuantity());
        BigDecimal tradePrice;

        // Price selection rule: price of the resting order (earlier) - common simple rule.
        // Determine which order was resting - compare timestamps
        if (buy.timestamp.isBefore(sell.timestamp)) {
            tradePrice = buy.price != null ? buy.price : sell.price;
        } else {
            tradePrice = sell.price != null ? sell.price : buy.price;
        }
        // If either is market (price null), use the other's price
        if (tradePrice == null) tradePrice = buy.price != null ? buy.price : sell.price;

        // Create trade
        Trade t = new Trade(buy.id, sell.id, buy.ticker, tradePrice, q);
        trades.add(t);
        System.out.println("TRADE: " + t);

        // Update orders
        buy.reduceRemaining(q);
        sell.reduceRemaining(q);
        if (buy.isFilled()) ob.getBids().remove(buy);
        if (sell.isFilled()) ob.getAsks().remove(sell);
        // Update traders' portfolios & cash
        Trader buyer = traderRegistry.get(buy.traderId);
        Trader seller = traderRegistry.get(sell.traderId);
        BigDecimal total = tradePrice.multiply(BigDecimal.valueOf(q));
        if (buyer != null) {
            buyer.portfolio.updatePosition(buy.ticker, q, tradePrice);
            buyer.portfolio.changeCash(total.negate());
        }
        if (seller != null) {
            seller.portfolio.updatePosition(sell.ticker, -q, tradePrice);
            seller.portfolio.changeCash(total);
        }
    }
}
