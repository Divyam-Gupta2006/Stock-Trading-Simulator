package gui;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;

import java.nio.file.Paths;
import java.nio.file.Path;

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

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.DefaultHighLowDataset;

/**
 * Stock Trading Simulator GUI
 * - Single user
 * - Mouse & button-based zooming (cursor-centered)
 * - Optional ONGC CSV import
 * - Auto-run + manual trading supported
 */
public class GuiApp {

    private final Simulator sim;
    private final Market market;
    private final MarketDataFeed feed;
    private final Trader user;

    private final JFrame frame = new JFrame("Stock Simulator — Candlestick View");
    private final DefaultTableModel orderBookModel = new DefaultTableModel(new Object[]{"Side","Price","Qty","OrderId"}, 0);
    private final DefaultTableModel tradesModel = new DefaultTableModel(new Object[]{"TradeId","Ticker","Price","Qty"}, 0);
    private final DefaultTableModel portfolioModel = new DefaultTableModel(new Object[]{"Trader","Cash","Positions"}, 0);

    // Controls
    private final JComboBox<String> tickerSelect = new JComboBox<>(new String[]{"AAPL","GOOG","MSFT","ONGC"});
    private final JButton zoomInBtn = new JButton("Zoom In");
    private final JButton zoomOutBtn = new JButton("Zoom Out");
    private final JButton resetZoomBtn = new JButton("Reset Zoom");
    private final int defaultWindow = 200;

    private final JComboBox<String> sideBox = new JComboBox<>(new String[]{"BUY","SELL"});
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"LIMIT","MARKET"});
    private final JTextField priceField = new JTextField("170.00", 8);
    private final JTextField qtyField = new JTextField("10", 6);
    private final JButton placeBtn = new JButton("Buy / Sell");
    private final JToggleButton autoToggle = new JToggleButton("Start Auto");
    private final JTextField intervalField = new JTextField("800", 6); // ms
    private javax.swing.Timer autoRunTimer;
    private javax.swing.Timer refreshTimer;

    private ChartPanel chartPanel;

    // UI colors
    private final Color ACCENT = new Color(66, 135, 245);
    private final Color BG = new Color(245, 247, 250);
    private final Color PANEL = Color.WHITE;
    private final Color TEXT_GRAY = new Color(70, 76, 87);
    private final Color TABLE_ALT = new Color(250, 251, 253);

    // last mouse point over chart (screen coords). null if mouse not over chart.
    private Point lastMousePoint = null;

    public GuiApp() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
        } catch (Exception ignored) {}

        UIManager.put("control", BG);
        UIManager.put("text", Color.DARK_GRAY);

        // Setup market & feed
        Map<String, BigDecimal> initial = new LinkedHashMap<>();
        initial.put("AAPL", new BigDecimal("170.00"));
        initial.put("GOOG", new BigDecimal("3000.00"));
        initial.put("MSFT", new BigDecimal("330.00"));
        initial.put("ONGC", new BigDecimal("250.00"));

        market = new Market(initial.keySet(), initial);
        feed = new MarketDataFeed(initial);
        sim = new Simulator(market, feed);

        try {
            Path csv = Paths.get("data", "ONGC.csv");
            feed.loadInvestingCsv("ONGC", csv);
            BigDecimal p = feed.getPrice("ONGC");
            if (p != null) market.setLastPrice("ONGC", p);
            System.out.println("Loaded ONGC history: " + csv.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Failed to load ONGC CSV (will use simulated prices): " + ex.getMessage());
        }

        // Single user trader
        user = new Trader("YOU", "You", new Portfolio(new BigDecimal("100000")));
        sim.registerTrader(user, null);

        // Add initial liquidity
        sim.submitOrder(new Order("LIQ1", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("171.00"), 200));
        sim.submitOrder(new Order("LIQ2", "ONGC", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("250.00"), 500));

        buildUI();
        refreshAll();

        // periodic refresh
        refreshTimer = new javax.swing.Timer(800, e -> refreshAll());
        refreshTimer.start();
    }

    /** ---------------------- UI CONSTRUCTION ------------------------ */
    private void buildUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200,780);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout(12,12));

        // Header
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("📊 Stock Simulator — You");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_GRAY);
        top.setBorder(new EmptyBorder(8,12,0,12));
        top.add(title, BorderLayout.WEST);
        frame.add(top, BorderLayout.NORTH);

        // Main layout
        JPanel center = new JPanel(new BorderLayout(12,12));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(0,12,12,12));

        // Chart
        JFreeChart base = ChartFactory.createCandlestickChart("Price (Candles)", "Time", "Price", null, false);
        styleChart(base);
        chartPanel = new ChartPanel(base);
        chartPanel.setPreferredSize(new Dimension(860,520));
        chartPanel.setBorder(new CompoundBorder(new LineBorder(new Color(220,220,225),1), new EmptyBorder(6,6,6,6)));
        chartPanel.setMouseWheelEnabled(false); // we handle wheel ourselves to center at cursor
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        center.add(chartPanel, BorderLayout.CENTER);

        // Add custom mouse listeners for cursor-centered wheel zoom + mouse tracking
        chartPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { lastMousePoint = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { lastMousePoint = e.getPoint(); }
        });
        chartPanel.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { lastMousePoint = null; }
            @Override public void mouseEntered(MouseEvent e) { lastMousePoint = e.getPoint(); }
        });
        chartPanel.addMouseWheelListener(e -> {
            // wheel rotation negative -> wheel up -> zoom in; positive -> zoom out
            double rot = e.getPreciseWheelRotation();
            double factor = (rot < 0) ? 0.85 : 1.2; // tweak: smaller factor = stronger zoom
            // perform cursor-centered zoom
            performZoomAtMouse(e.getPoint(), factor);
        });

        // Right control panel
        JPanel right = new JPanel(new BorderLayout(8,8));
        right.setPreferredSize(new Dimension(320,520));
        right.setBackground(PANEL);
        right.setBorder(new CompoundBorder(new EmptyBorder(12,12,12,12), new LineBorder(new Color(230,230,235))));
        center.add(right, BorderLayout.EAST);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        c.gridx=0; c.gridy=row; controls.add(new JLabel("Ticker"), c);
        c.gridx=1; c.gridy=row++; controls.add(tickerSelect, c);

        // Zoom controls
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        zoomPanel.setOpaque(false);
        zoomPanel.add(zoomInBtn);
        zoomPanel.add(zoomOutBtn);
        zoomPanel.add(resetZoomBtn);
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; controls.add(zoomPanel, c);
        row++; c.gridwidth = 1;

        c.gridx=0; c.gridy=row; controls.add(new JLabel("Side"), c);
        c.gridx=1; c.gridy=row++; controls.add(sideBox, c);

        c.gridx=0; c.gridy=row; controls.add(new JLabel("Type"), c);
        c.gridx=1; c.gridy=row++; controls.add(typeBox, c);

        c.gridx=0; c.gridy=row; controls.add(new JLabel("Price"), c);
        c.gridx=1; c.gridy=row++; controls.add(priceField, c);

        c.gridx=0; c.gridy=row; controls.add(new JLabel("Qty"), c);
        c.gridx=1; c.gridy=row++; controls.add(qtyField, c);

        placeBtn.setBackground(ACCENT);
        placeBtn.setForeground(Color.WHITE);
        placeBtn.setFocusPainted(false);
        c.gridx=0; c.gridy=row; c.gridwidth=2; controls.add(placeBtn, c);
        row++; c.gridwidth=1;

        JPanel autoRow = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        autoRow.setOpaque(false);
        autoRow.add(new JLabel("Auto-ms:")); autoRow.add(intervalField); autoRow.add(autoToggle);
        c.gridx=0; c.gridy=row; c.gridwidth=2; controls.add(autoRow, c);
        right.add(controls, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        JTable bookTable = new JTable(orderBookModel); styleTable(bookTable);
        tabs.addTab("Book", new JScrollPane(bookTable));
        JTable tradesTable = new JTable(tradesModel); styleTable(tradesTable);
        tabs.addTab("Trades", new JScrollPane(tradesTable));
        JTable portTable = new JTable(portfolioModel); styleTable(portTable);
        tabs.addTab("Portfolio", new JScrollPane(portTable));
        right.add(tabs, BorderLayout.CENTER);

        frame.add(center, BorderLayout.CENTER);

        JLabel hint = new JLabel("💡 Use mouse wheel or drag to zoom, click Zoom In/Out for quick steps. You can trade while auto-run is active.");
        hint.setBorder(new EmptyBorder(6,12,12,12));
        hint.setForeground(TEXT_GRAY);
        frame.add(hint, BorderLayout.SOUTH);

        // Actions
        placeBtn.addActionListener(e -> placeOrderFromForm());
        tickerSelect.addActionListener(e -> refreshAll());
        qtyField.addActionListener(e -> placeOrderFromForm());

        zoomInBtn.addActionListener(e -> {
            // if mouse over chart, zoom around it; else center
            Point p = lastMousePoint;
            if (p == null) p = new Point(chartPanel.getWidth()/2, chartPanel.getHeight()/2);
            performZoomAtMouse(p, 0.7);
        });
        zoomOutBtn.addActionListener(e -> {
            Point p = lastMousePoint;
            if (p == null) p = new Point(chartPanel.getWidth()/2, chartPanel.getHeight()/2);
            performZoomAtMouse(p, 1.4);
        });
        resetZoomBtn.addActionListener(e -> resetZoom());

        autoToggle.addActionListener(e -> toggleAuto());

        placeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { placeBtn.setBackground(ACCENT.darker()); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { placeBtn.setBackground(ACCENT); }
        });

        frame.setVisible(true);
    }

    /** ---------------------- BEHAVIOR ------------------------ */

    private void toggleAuto() {
        if (autoToggle.isSelected()) {
            int interval;
            try { interval = Integer.parseInt(intervalField.getText().trim()); if (interval < 50) interval = 50; }
            catch (NumberFormatException nfe) { interval = 800; intervalField.setText(String.valueOf(interval)); }
            autoRunTimer = new javax.swing.Timer(interval, ev -> { sim.runForTicks(1); refreshAll(); });
            autoRunTimer.start();
            autoToggle.setText("Stop Auto");
        } else {
            if (autoRunTimer != null) autoRunTimer.stop();
            autoToggle.setText("Start Auto");
        }
    }

    private void placeOrderFromForm() {
        try {
            String ticker = (String) tickerSelect.getSelectedItem();
            OrderSide side = OrderSide.valueOf((String) sideBox.getSelectedItem());
            OrderType type = OrderType.valueOf((String) typeBox.getSelectedItem());
            int qty = Integer.parseInt(qtyField.getText().trim());
            BigDecimal price = (type == OrderType.LIMIT) ? new BigDecimal(priceField.getText().trim()) : null;
            Order o = new Order(user.id, ticker, side, type, price, qty);
            sim.submitOrder(o);
            JOptionPane.showMessageDialog(frame, "Order submitted (id=" + o.id + ")", "Order", JOptionPane.INFORMATION_MESSAGE);
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshAll() {
        SwingUtilities.invokeLater(() -> {
            String ticker = (String) tickerSelect.getSelectedItem();
            try {
                DefaultHighLowDataset ds = feed.getHighLowDataset(ticker, defaultWindow);
                if (ds != null) {
                    JFreeChart chart = ChartFactory.createCandlestickChart("Price: " + ticker, "Time", "Price", (OHLCDataset) ds, false);
                    styleChart(chart);
                    chartPanel.setChart(chart);
                }
            } catch (Exception ignored) {}

            orderBookModel.setRowCount(0);
            OrderBook ob = market.getOrderBook(ticker);
            if (ob != null) {
                ob.getAsks().stream().limit(10).forEach(o -> orderBookModel.addRow(new Object[]{"ASK", o.price != null ? o.price.setScale(2, RoundingMode.HALF_UP).toPlainString() : "MKT", o.getRemainingQuantity(), o.id}));
                ob.getBids().stream().limit(10).forEach(o -> orderBookModel.addRow(new Object[]{"BID", o.price != null ? o.price.setScale(2, RoundingMode.HALF_UP).toPlainString() : "MKT", o.getRemainingQuantity(), o.id}));
            }

            tradesModel.setRowCount(0);
            for (Trade t : sim.getTradeLog())
                tradesModel.addRow(new Object[]{t.id, t.ticker, t.price.setScale(2, RoundingMode.HALF_UP).toPlainString(), t.quantity});

            portfolioModel.setRowCount(0);
            Portfolio p = user.portfolio;
            String cash = p.getCash().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String pos = p.getPositions().entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue().quantity)
                    .reduce((a,b)->a+","+b).orElse("-");
            portfolioModel.addRow(new Object[]{ user.name + " (" + user.id + ")", cash, pos });
        });
    }

    /** ---------------------- CURSOR-CENTERED ZOOM ------------------------ */

    // Compute data-space domain (x) value at a screen point
    private double screenToDomainValue(Point p) {
        try {
            ChartRenderingInfo info = chartPanel.getChartRenderingInfo();
            Rectangle2D plotArea = info.getPlotInfo().getDataArea();
            XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
            ValueAxis domain = plot.getDomainAxis();
            double java2DX = p.getX();
            double axisX = domain.java2DToValue(java2DX, plotArea, plot.getDomainAxisEdge());
            return axisX;
        } catch (Exception ex) {
            // fallback: center of domain
            XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
            ValueAxis domain = plot.getDomainAxis();
            return (domain.getLowerBound() + domain.getUpperBound()) / 2.0;
        }
    }

    // Compute data-space range (y/price) value at a screen point
    private double screenToRangeValue(Point p) {
        try {
            ChartRenderingInfo info = chartPanel.getChartRenderingInfo();
            Rectangle2D plotArea = info.getPlotInfo().getDataArea();
            XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
            ValueAxis range = plot.getRangeAxis();
            double java2DY = p.getY();
            double axisY = range.java2DToValue(java2DY, plotArea, plot.getRangeAxisEdge());
            return axisY;
        } catch (Exception ex) {
            XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
            ValueAxis range = plot.getRangeAxis();
            return (range.getLowerBound() + range.getUpperBound()) / 2.0;
        }
    }

    private void performZoomAtMouse(Point mousePoint, double factor) {
        if (chartPanel == null || chartPanel.getChart() == null) return;
        XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
        if (plot == null) return;

        // translate the screen point to plot data coordinates
        double domainAnchor = screenToDomainValue(mousePoint);
        double rangeAnchor = screenToRangeValue(mousePoint);

        zoomChartAt(domainAnchor, rangeAnchor, factor);
    }

    /**
     * Zoom around an anchor data point (domainAnchor, rangeAnchor).
     * factor < 1 -> zoom in; factor > 1 -> zoom out.
     */
    private void zoomChartAt(double domainAnchor, double rangeAnchor, double factor) {
        if (chartPanel == null || chartPanel.getChart() == null) return;
        XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
        try {
            ValueAxis domain = plot.getDomainAxis();
            ValueAxis range = plot.getRangeAxis();

            double dLow = domain.getLowerBound();
            double dHigh = domain.getUpperBound();
            double newDLow = domainAnchor - (domainAnchor - dLow) * factor;
            double newDHigh = domainAnchor + (dHigh - domainAnchor) * factor;
            domain.setLowerBound(newDLow);
            domain.setUpperBound(newDHigh);

            double rLow = range.getLowerBound();
            double rHigh = range.getUpperBound();
            double newRLow = rangeAnchor - (rangeAnchor - rLow) * factor;
            double newRHigh = rangeAnchor + (rHigh - rangeAnchor) * factor;
            // enforce floor >= 0 for price axis
            newRLow = Math.max(0.0, newRLow);
            range.setLowerBound(newRLow);
            range.setUpperBound(newRHigh);
        } catch (Exception ignored) {}
    }

    private void resetZoom() {
        if (chartPanel == null || chartPanel.getChart() == null) return;
        XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
        try {
            plot.getDomainAxis().setAutoRange(true);
            plot.getRangeAxis().setAutoRange(true);
        } catch (Exception ignored) {}
    }

    /** ---------------------- STYLING ------------------------ */
    private void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(BG);
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(20, 26, 34));
        plot.setDomainGridlinePaint(new Color(110, 120, 140));
        plot.setRangeGridlinePaint(new Color(110, 120, 140));
        CandlestickRenderer r = new CandlestickRenderer();
        r.setUseOutlinePaint(true);
        r.setUpPaint(new Color(60, 180, 75));
        r.setDownPaint(new Color(230, 80, 80));
        plot.setRenderer(r);
    }

    private void styleTable(JTable t) {
        t.setFillsViewportHeight(true);
        t.setShowGrid(false);
        t.setRowHeight(22);
        t.setBackground(PANEL);
        t.setIntercellSpacing(new Dimension(0,0));
        t.setSelectionBackground(new Color(220,235,250));
        t.setSelectionForeground(Color.BLACK);
        t.getTableHeader().setBackground(new Color(245,247,250));
        t.getTableHeader().setForeground(TEXT_GRAY);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer alt = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) c.setBackground((row%2==0) ? PANEL : TABLE_ALT);
                setForeground(Color.DARK_GRAY);
                if (value instanceof Number || (value!=null && value.toString().matches("^\\d+(\\.\\d+)?$"))) setHorizontalAlignment(RIGHT); else setHorizontalAlignment(LEFT);
                setBorder(new EmptyBorder(2,6,2,6));
                return c;
            }
        };
        for (int i=0;i<t.getColumnCount();i++) t.getColumnModel().getColumn(i).setCellRenderer(alt);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiApp::new);
    }
}
