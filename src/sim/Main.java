import market.Market;
import market.MarketDataFeed;
import sim.Simulator;
import market.OrderBook;

import model.Order;
import model.OrderSide;
import model.OrderType;
import model.Trade;
import model.Portfolio;

import trader.Trader;
import trader.SimpleMeanReversionStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interactive terminal menu for the stock trading simulator.
 * No package declaration so run with: java -cp out Main
 */
public class Main {

    public static void main(String[] args) {
        // --- Setup market, feed, simulator ---
        Map<String, BigDecimal> initial = new LinkedHashMap<>();
        initial.put("AAPL", new BigDecimal("170.00"));
        initial.put("GOOG", new BigDecimal("3000.00"));
        initial.put("MSFT", new BigDecimal("330.00"));

        Market market = new Market(initial.keySet(), initial);
        MarketDataFeed feed = new MarketDataFeed(initial);
        Simulator sim = new Simulator(market, feed);

        // --- Create traders ---
        Trader alice = new Trader("T1", "Alice", new Portfolio(new BigDecimal("100000")));
        Trader bob = new Trader("T2", "Bob", new Portfolio(new BigDecimal("100000")));

        // register traders. You can attach a strategy or null.
        sim.registerTrader(alice, new SimpleMeanReversionStrategy("AAPL"));
        sim.registerTrader(bob, null);

        // Some initial resting orders so market has liquidity for demo:
        Order bobSell = new Order(bob.id, "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("171.00"), 50);
        sim.submitOrder(bobSell);

        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            switch (line) {
                case "1" -> printPrices(market);
                case "2" -> {
                    System.out.print("Ticker: ");
                    String ticker = sc.nextLine().trim().toUpperCase();
                    printOrderBook(market, ticker);
                }
                case "3" -> placeLimitOrder(sc, sim, Arrays.asList(alice, bob), market);
                case "4" -> placeMarketOrder(sc, sim, Arrays.asList(alice, bob), market);
                case "5" -> {
                    System.out.print("Ticker of order to cancel: ");
                    String ticker = sc.nextLine().trim().toUpperCase();
                    System.out.print("Order id to cancel: ");
                    String idS = sc.nextLine().trim();
                    try {
                        long id = Long.parseLong(idS);
                        OrderBook ob = market.getOrderBook(ticker);
                        if (ob == null) {
                            System.out.println("Unknown ticker: " + ticker);
                        } else {
                            ob.cancelOrder(id);
                            System.out.println("Cancel request submitted for order id " + id);
                        }
                    } catch (NumberFormatException nfe) {
                        System.out.println("Invalid id.");
                    }
                }
                case "6" -> {
                    System.out.print("Number of ticks to run: ");
                    String nS = sc.nextLine().trim();
                    try {
                        int n = Integer.parseInt(nS);
                        sim.runForTicks(n);
                        System.out.println("Advanced " + n + " ticks.");
                    } catch (NumberFormatException nfe) {
                        System.out.println("Invalid number.");
                    }
                }
                case "7" -> {
                    System.out.println("=== Traders & portfolios ===");
                    sim.getTraders().values().forEach(t -> {
                        System.out.println(t + " -> " + t.portfolio);
                    });
                }
                case "8" -> {
                    System.out.println("=== Trades ===");
                    List<Trade> trades = sim.getTradeLog();
                    if (trades.isEmpty()) System.out.println("(no trades)");
                    else trades.forEach(System.out::println);
                }
                case "9" -> {
                    running = false;
                }
                default -> System.out.println("Unknown option. Type 1..9");
            }
        }

        sc.close();
        System.out.println("Exiting. Final portfolios:");
        sim.getTraders().values().forEach(t -> System.out.println(t + " -> " + t.portfolio));
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("=== Stock Sim Menu ===");
        System.out.println("1) View prices");
        System.out.println("2) View order book for ticker");
        System.out.println("3) Place LIMIT order");
        System.out.println("4) Place MARKET order");
        System.out.println("5) Cancel order by id");
        System.out.println("6) Advance simulation by N ticks");
        System.out.println("7) Show traders & portfolios");
        System.out.println("8) Show executed trades");
        System.out.println("9) Exit");
    }

    private static void printPrices(Market market) {
        System.out.println("Tick prices:");
        Map<String, BigDecimal> prices;
        try {
            prices = market.getPrices();
        } catch (Exception e) {
            // fallback to asking per ticker
            prices = new LinkedHashMap<>();
            for (String t : List.of("AAPL", "GOOG", "MSFT")) {
                BigDecimal p = market.getLastPrice(t);
                if (p != null) prices.put(t, p);
            }
        }
        for (var e : prices.entrySet()) {
            String formatted = e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
            System.out.println("  " + e.getKey() + " -> " + formatted);
        }
    }

    private static void printOrderBook(Market market, String ticker) {
        OrderBook ob = market.getOrderBook(ticker);
        if (ob == null) {
            System.out.println("Unknown ticker: " + ticker);
            return;
        }
        System.out.println("=== Order Book: " + ticker + " ===");
        System.out.println("Top BIDS (price x qty) :");
        ob.getBids().stream().limit(5).forEach(o ->
            System.out.println("  " + (o.price != null ? o.price.setScale(2, RoundingMode.HALF_UP).toPlainString() : "MKT") + " x" + o.getRemainingQuantity() + " (id=" + o.id + ")")
        );
        System.out.println("Top ASKS (price x qty) :");
        ob.getAsks().stream().limit(5).forEach(o ->
            System.out.println("  " + (o.price != null ? o.price.setScale(2, RoundingMode.HALF_UP).toPlainString() : "MKT") + " x" + o.getRemainingQuantity() + " (id=" + o.id + ")")
        );
    }

    private static void placeLimitOrder(Scanner sc, Simulator sim, List<Trader> traders, Market market) {
        Trader t = chooseTrader(sc, traders);
        if (t == null) return;
        System.out.print("Side (BUY/SELL): ");
        String sideS = sc.nextLine().trim().toUpperCase();
        System.out.print("Ticker: ");
        String ticker = sc.nextLine().trim().toUpperCase();
        System.out.print("Price: ");
        String priceS = sc.nextLine().trim();
        System.out.print("Quantity: ");
        String qtyS = sc.nextLine().trim();
        try {
            OrderSide side = OrderSide.valueOf(sideS);
            BigDecimal price = new BigDecimal(priceS);
            int qty = Integer.parseInt(qtyS);
            Order o = new Order(t.id, ticker, side, OrderType.LIMIT, price, qty);
            sim.submitOrder(o);
            System.out.println("Submitted: " + o);
        } catch (Exception ex) {
            System.out.println("Invalid input: " + ex.getMessage());
        }
    }

    private static void placeMarketOrder(Scanner sc, Simulator sim, List<Trader> traders, Market market) {
        Trader t = chooseTrader(sc, traders);
        if (t == null) return;
        System.out.print("Side (BUY/SELL): ");
        String sideS = sc.nextLine().trim().toUpperCase();
        System.out.print("Ticker: ");
        String ticker = sc.nextLine().trim().toUpperCase();
        System.out.print("Quantity: ");
        String qtyS = sc.nextLine().trim();
        try {
            OrderSide side = OrderSide.valueOf(sideS);
            int qty = Integer.parseInt(qtyS);
            Order o = new Order(t.id, ticker, side, OrderType.MARKET, null, qty);
            sim.submitOrder(o);
            System.out.println("Submitted: " + o);
        } catch (Exception ex) {
            System.out.println("Invalid input: " + ex.getMessage());
        }
    }

    private static Trader chooseTrader(Scanner sc, List<Trader> traders) {
        System.out.println("Choose trader:");
        for (int i = 0; i < traders.size(); i++) {
            System.out.println((i+1) + ") " + traders.get(i).name + " (id=" + traders.get(i).id + ")");
        }
        System.out.print("> ");
        String sel = sc.nextLine().trim();
        try {
            int idx = Integer.parseInt(sel) - 1;
            if (idx < 0 || idx >= traders.size()) {
                System.out.println("Invalid selection.");
                return null;
            }
            return traders.get(idx);
        } catch (NumberFormatException nfe) {
            System.out.println("Invalid selection.");
            return null;
        }
    }
}
