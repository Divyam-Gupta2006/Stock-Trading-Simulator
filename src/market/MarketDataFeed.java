package market;

import org.jfree.data.xy.DefaultHighLowDataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MarketDataFeed
 *
 * - Loads Investing.com CSVs (optional) via loadInvestingCsv(ticker,path)
 * - Provides tickPrice/tickAll that advance either replayed CSV history or a
 *   stochastic, non-repeating synthetic model (GARCH-like + jumps + trend).
 * - Exposes getHighLowDataset(ticker, window) for JFreeChart candlesticks.
 *
 * Usage:
 *   MarketDataFeed feed = new MarketDataFeed(initialMap);
 *   feed.loadInvestingCsv("ONGC", Paths.get("data","ONGC.csv")); // optional
 *   feed.tickAll(); // advances every known ticker by one bar
 *   BigDecimal p = feed.getPrice("ONGC");
 */
public class MarketDataFeed {
    // ---- Data stores for CSV-based history (chronological order) ----
    private final Map<String, List<Date>> dates = new HashMap<>();
    private final Map<String, List<Double>> opens = new HashMap<>();
    private final Map<String, List<Double>> highs = new HashMap<>();
    private final Map<String, List<Double>> lows = new HashMap<>();
    private final Map<String, List<Double>> closes = new HashMap<>();
    private final Map<String, List<Double>> vols = new HashMap<>();
    private final Map<String, Integer> replayIndex = new HashMap<>();

    // ---- Last known price map used by market/simulator (updated each tick) ----
    private final Map<String, BigDecimal> lastPrices = new HashMap<>();

    // ---- Synthetic dynamics state (for tick generator) ----
    private final Random rng = new Random();
    private final Map<String, Double> lastDouble = new HashMap<>();
    private final Map<String, Double> volSq = new HashMap<>();   // variance per ticker
    private final Map<String, Double> trend = new HashMap<>();   // slow trend per ticker
    private final Map<String, Double> ar = new HashMap<>();      // short AR noise
    private long tickCounter = 0;

    // ---- Constructor ----
    public MarketDataFeed(Map<String, BigDecimal> initial) {
        if (initial != null) {
            for (Map.Entry<String, BigDecimal> e : initial.entrySet()) {
                lastPrices.put(e.getKey(), e.getValue());
                lastDouble.put(e.getKey(), e.getValue().doubleValue());
                volSq.put(e.getKey(), 0.0004); // initial variance
                trend.put(e.getKey(), e.getValue().doubleValue());
                ar.put(e.getKey(), 0.0);
                replayIndex.putIfAbsent(e.getKey(), 0);
            }
        }
    }

    // ---------------- CSV loader (Investing.com style) ----------------
    /**
     * Load investing.com CSV.
     * Expected columns (common): Date,Price,Open,High,Low,Vol,Change %
     * This method is forgiving: strips quotes, tries several date formats.
     */
    public void loadInvestingCsv(String ticker, Path path) throws IOException {
        List<Date> d = new ArrayList<>();
        List<Double> o = new ArrayList<>();
        List<Double> h = new ArrayList<>();
        List<Double> l = new ArrayList<>();
        List<Double> c = new ArrayList<>();
        List<Double> v = new ArrayList<>();

        SimpleDateFormat[] formats = new SimpleDateFormat[]{
            new SimpleDateFormat("dd-MM-yyyy"),
            new SimpleDateFormat("dd/MM/yyyy"),
            new SimpleDateFormat("MM/dd/yyyy"),
            new SimpleDateFormat("yyyy-MM-dd"),
            new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
        };
        for (SimpleDateFormat f : formats) f.setLenient(false);

        try (BufferedReader br = java.nio.file.Files.newBufferedReader(path)) {
            String header = br.readLine();
            if (header == null) throw new IOException("Empty CSV: " + path);
            if (header.startsWith("\uFEFF")) header = header.substring(1);

            String line;
            int lineno = 1;
            while ((line = br.readLine()) != null) {
                lineno++;
                if (line.trim().isEmpty()) continue;

                // split and strip quotes per-field
                String[] raw = line.split(",", -1);
                if (raw.length < 6) {
                    System.err.println("Skipping short/malformed CSV line " + lineno + ": " + line);
                    continue;
                }
                for (int i = 0; i < raw.length; i++) {
                    raw[i] = raw[i].trim();
                    if (raw[i].startsWith("\"") && raw[i].endsWith("\"") && raw[i].length() >= 2) {
                        raw[i] = raw[i].substring(1, raw[i].length() - 1);
                    }
                }

                try {
                    // date
                    String ds = raw[0];
                    Date date = null;
                    for (SimpleDateFormat fmt : formats) {
                        try { date = fmt.parse(ds); break; } catch (ParseException ignore) {}
                    }
                    if (date == null) throw new ParseException("Unparseable date: " + ds, lineno);

                    // fields (investing: Date,Price,Open,High,Low,Vol,...)
                    String closeS = raw[1].replaceAll(",", "");
                    String openS  = raw[2].replaceAll(",", "");
                    String highS  = raw[3].replaceAll(",", "");
                    String lowS   = raw[4].replaceAll(",", "");
                    String volS   = raw[5].trim();

                    double close = Double.parseDouble(closeS);
                    double open  = Double.parseDouble(openS);
                    double high  = Double.parseDouble(highS);
                    double low   = Double.parseDouble(lowS);
                    double vol   = parseVol(volS);

                    d.add(date); o.add(open); h.add(high); l.add(low); c.add(close); v.add(vol);
                } catch (Exception rowEx) {
                    System.err.println("Failed to parse line " + lineno + " (" + path + "): " + rowEx.getMessage());
                }
            }
        } catch (Exception ex) {
            throw new IOException("Failed reading CSV " + path + ": " + ex.getMessage(), ex);
        }

        if (c.isEmpty()) {
            throw new IOException("No valid rows parsed from CSV " + path + ". Check header, delimiter and date format.");
        }

        // Investing.com lists newest first — reverse to chronological order
        Collections.reverse(d); Collections.reverse(o); Collections.reverse(h);
        Collections.reverse(l); Collections.reverse(c); Collections.reverse(v);

        dates.put(ticker, d);
        opens.put(ticker, o);
        highs.put(ticker, h);
        lows.put(ticker, l);
        closes.put(ticker, c);
        vols.put(ticker, v);
        replayIndex.put(ticker, 0);

        // initialize last price & dynamics state if not present
        double last = c.get(c.size() - 1);
        lastPrices.put(ticker, BigDecimal.valueOf(last).setScale(6, RoundingMode.HALF_UP));
        lastDouble.put(ticker, last);
        volSq.putIfAbsent(ticker, 0.0004);
        trend.putIfAbsent(ticker, last);
        ar.putIfAbsent(ticker, 0.0);
    }

    // Parse volume strings like "9.42M" "12K" "1,234"
    private double parseVol(String s) {
        if (s == null || s.isEmpty() || s.equals("-")) return 0;
        s = s.trim().toUpperCase(Locale.ROOT).replaceAll(",", "");
        try {
            if (s.endsWith("K")) return Double.parseDouble(s.substring(0, s.length()-1)) * 1_000;
            if (s.endsWith("M")) return Double.parseDouble(s.substring(0, s.length()-1)) * 1_000_000;
            if (s.endsWith("B")) return Double.parseDouble(s.substring(0, s.length()-1)) * 1_000_000_000;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------- Synthetic dynamics initialization ----------------
    private void initDynamicsForTickers() {
        // ensure all known tickers have dynamics entries
        Set<String> all = new HashSet<>();
        all.addAll(lastPrices.keySet());
        all.addAll(closes.keySet());
        for (String t : all) {
            lastDouble.putIfAbsent(t, lastPrices.getOrDefault(t, BigDecimal.valueOf(10)).doubleValue());
            volSq.putIfAbsent(t, 0.0004);
            trend.putIfAbsent(t, lastDouble.get(t));
            ar.putIfAbsent(t, 0.0);
            replayIndex.putIfAbsent(t, 0);
        }
    }

    // ---------------- Ticking behavior ----------------
    /**
     * Advance all relevant tickers by one tick/bar.
     * This will advance CSV replays and/or synthetic dynamics.
     */
    public void tickAll() {
        initDynamicsForTickers();

        // union of tickers: CSV-loaded OR initial lastPrices keys
        Set<String> tickers = new LinkedHashSet<>();
        tickers.addAll(lastPrices.keySet());
        tickers.addAll(closes.keySet());

        for (String t : tickers) tickPrice(t);
        tickCounter++;
    }

    /**
     * Advance a single ticker by one tick.
     * - if CSV history is loaded and replay not finished -> use next historical close
     * - otherwise use synthetic stochastic model (GARCH-like + jumps + trend)
     */
    public void tickPrice(String ticker) {
        initDynamicsForTickers();

        // if CSV history present and we haven't reached end -> replay next close
        List<Double> c = closes.get(ticker);
        int idx = replayIndex.getOrDefault(ticker, 0);
        if (c != null && !c.isEmpty() && idx < c.size()) {
            double next = c.get(idx);
            // update state
            replayIndex.put(ticker, idx + 1);
            lastDouble.put(ticker, next);
            lastPrices.put(ticker, BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP));
            // slightly increase volatility after replayed move to simulate activity
            double sigma2 = volSq.getOrDefault(ticker, 0.0004);
            sigma2 = Math.min(0.5, sigma2 * 0.995 + Math.abs(next - lastDouble.getOrDefault(ticker, next)) * 1e-4);
            volSq.put(ticker, sigma2);
            return;
        }

        // --- Synthetic model (GARCH-like + jumps + AR + slow trend) ---
        double last = lastDouble.getOrDefault(ticker, 10.0);
        double sigma2 = volSq.getOrDefault(ticker, 0.0004);
        double muTrend = trend.getOrDefault(ticker, last);

        // parameters (tweak if needed)
        double meanReversionRate = 0.0005;
        double trendNoiseScale = 0.0002;
        double garchOmega = 1e-6;
        double garchAlpha = 0.08;
        double garchBeta = 0.90;
        double arPhi = 0.15;
        double jumpProb = 0.012;
        double jumpScale = 0.04;
        double minPrice = 0.01;

        // draws
        double shock = rng.nextGaussian();
        double retShock = shock * Math.sqrt(sigma2);

        // AR micro-noise
        double prevAr = ar.getOrDefault(ticker, 0.0);
        double newAr = arPhi * prevAr + 0.08 * rng.nextGaussian() * Math.sqrt(sigma2);
        ar.put(ticker, newAr);

        // update trend slowly
        double trendNoise = trendNoiseScale * rng.nextGaussian();
        double newTrend = muTrend * (1.0 + trendNoise);
        trend.put(ticker, newTrend);

        // mean reversion toward trend
        double reversion = meanReversionRate * (newTrend - last) / Math.max(last, minPrice);

        // base percent return
        double pctReturn = reversion + retShock + newAr;

        // occasional heavy jump
        if (rng.nextDouble() < jumpProb) {
            double heavy = Math.abs(rng.nextGaussian()) + 0.2;
            double sign = rng.nextBoolean() ? 1.0 : -1.0;
            double jump = sign * heavy * jumpScale;
            pctReturn += jump;
            sigma2 = Math.min(0.5, sigma2 + 0.5 * Math.abs(jump));
        }

        // apply return
        double next = last * (1.0 + pctReturn);

        if (Double.isNaN(next) || Double.isInfinite(next) || next < minPrice) {
            next = Math.max(minPrice, last * (1.0 + (rng.nextGaussian() * 0.001)));
        }

        // update GARCH-like variance
        double realized = pctReturn;
        double newSigma2 = garchOmega + garchAlpha * realized * realized + garchBeta * sigma2;
        newSigma2 = Math.max(1e-8, Math.min(newSigma2, 0.5));
        volSq.put(ticker, newSigma2);

        // commit state
        lastDouble.put(ticker, next);
        lastPrices.put(ticker, BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP));
    }

    // ---------------- Chart dataset builder ----------------
    /**
     * Build a DefaultHighLowDataset containing the last `window` bars up to current replay index.
     * If CSV history exists it uses real OHLC; otherwise it synthesizes small H/L around last prices
     * using deterministic jitter so chart is still meaningful.
     */
    public DefaultHighLowDataset getHighLowDataset(String ticker, int window) {
        // prefer CSV OHLC if available
        List<Date> dlist = dates.get(ticker);
        List<Double> olist = opens.get(ticker);
        List<Double> hlist = highs.get(ticker);
        List<Double> llist = lows.get(ticker);
        List<Double> clist = closes.get(ticker);
        List<Double> vlist = vols.get(ticker);

        if (dlist != null && !dlist.isEmpty()) {
            int cur = replayIndex.getOrDefault(ticker, 0);
            int end = Math.min(cur, dlist.size());
            int start = Math.max(0, end - window);
            int n = end - start;
            if (n <= 0) return null;

            Date[] ds = new Date[n];
            double[] os = new double[n];
            double[] hs = new double[n];
            double[] ls = new double[n];
            double[] cs = new double[n];
            double[] vs = new double[n];
            for (int i = 0; i < n; i++) {
                int src = start + i;
                ds[i] = dlist.get(src);
                os[i] = olist.get(src);
                hs[i] = hlist.get(src);
                ls[i] = llist.get(src);
                cs[i] = clist.get(src);
                vs[i] = vlist.get(src);
            }
            return new DefaultHighLowDataset(ticker, ds, hs, ls, os, cs, vs);
        }

        // fallback: synthesize simple OHLC from lastPrices history (lastDouble timeline is not a series),
        // create a sliding synthetic series based on last price with small random jitter (deterministic seed to avoid wild swings)
        double last = lastDouble.getOrDefault(ticker, 10.0);
        int n = Math.max(1, Math.min(window, 100));
        Date[] ds = new Date[n];
        double[] os = new double[n];
        double[] hs = new double[n];
        double[] ls = new double[n];
        double[] cs = new double[n];
        double[] vs = new double[n];

        long now = System.currentTimeMillis();
        Random r = new Random(ticker.hashCode() ^ (now / 60000)); // changes slowly
        for (int i = 0; i < n; i++) {
            // backfill with small random walk around last
            double base = last * (1.0 - 0.004 + (r.nextDouble() * 0.008));
            double o = base;
            double c = base * (1.0 - 0.002 + r.nextDouble() * 0.004);
            double high = Math.max(o, c) * (1.0 + r.nextDouble() * 0.003);
            double low  = Math.min(o, c) * (1.0 - r.nextDouble() * 0.003);
            ds[i] = new Date(System.currentTimeMillis() - (n - i) * 60L * 60L * 1000L); // hourly-like labels (approx)
            os[i] = o;
            hs[i] = high;
            ls[i] = low;
            cs[i] = c;
            vs[i] = 100 + r.nextInt(900);
        }
        return new DefaultHighLowDataset(ticker, ds, hs, ls, os, cs, vs);
    }

    // ---------------- Utility & getters ----------------
    public BigDecimal getPrice(String ticker) {
        return lastPrices.getOrDefault(ticker, BigDecimal.ZERO);
    }

    public Set<String> getKnownTickers() {
        Set<String> s = new LinkedHashSet<>();
        s.addAll(lastPrices.keySet());
        s.addAll(closes.keySet());
        return s;
    }

    public int getReplayIndex(String ticker) {
        return replayIndex.getOrDefault(ticker, 0);
    }

    public int getHistoryLength(String ticker) {
        List<Double> c = closes.get(ticker);
        return c == null ? 0 : c.size();
    }

    public void resetReplay(String ticker, int toIndex) {
        if (replayIndex.containsKey(ticker)) {
            List<Double> c = closes.get(ticker);
            int max = c == null ? 0 : c.size();
            replayIndex.put(ticker, Math.max(0, Math.min(toIndex, max)));
        }
    }
}
