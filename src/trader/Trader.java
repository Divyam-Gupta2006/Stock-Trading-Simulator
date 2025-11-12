package trader;

import model.Portfolio;

public class Trader {
    public final String id;
    public final String name;
    public final Portfolio portfolio;

    public Trader(String id, String name, Portfolio portfolio) {
        this.id = id;
        this.name = name;
        this.portfolio = portfolio;
    }

    @Override
    public String toString() {
        return "Trader{" + id + ":" + name + "}";
    }
}
