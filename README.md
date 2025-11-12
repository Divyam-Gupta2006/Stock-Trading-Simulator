# 📊 Stock Trading Simulator (Java)

A **real-time stock trading simulator** built entirely in **Java**, featuring interactive **candlestick charts**, **live price updates**, and an intuitive trading interface.

This project combines object-oriented programming concepts with GUI design (Swing) and charting (JFreeChart) to create a realistic yet educational trading experience.

---

## 🚀 Features

### 🖥️ Interactive Simulation

* **Real-time price ticks** (generated algorithmically or from CSV data)
* **Buy/Sell orders** with Market or Limit types
* **Portfolio management** (cash, positions, trade log)
* **Order book visualization**
* **Auto-run toggle** (simulate live market ticking)

### 📉 Candlestick Chart

* Built using **JFreeChart**
* **Cursor-centered zooming** (mouse wheel, drag, or buttons)
* **Zoom In / Zoom Out / Reset** controls
* Clean, dark trading-style chart theme

### 👤 Single-User Mode

* No bots — only user-initiated trades
* Trade manually while the market continues ticking
* See how your balance and holdings evolve in real time

---

## 🧠 Tech Stack

| Component       | Technology                   |
| --------------- | ---------------------------- |
| Language        | Java 17+                     |
| GUI Framework   | Swing                        |
| Charting        | JFreeChart                   |
| Build Type      | Object-Oriented Java Project |
| IDE             | VS Code / IntelliJ / Eclipse |
| Version Control | Git + GitHub                 |

---

## 🗂️ Project Structure

```
Stock-Trading-Simulator/
├── src/
│   ├── gui/GuiApp.java               # Main GUI window (Swing + JFreeChart)
│   ├── market/MarketDataFeed.java    # Price feed simulation
│   ├── market/Market.java            # Core market engine
│   ├── model/Order.java, Trade.java  # Data models
│   ├── sim/Simulator.java            # Simulation logic
│   └── trader/Trader.java            # User portfolio
│
├── lib/                              # JFreeChart and JCommon JARs
├── data/                             # Optional CSVs (e.g., ONGC.csv)
├── out/                              # Compiled .class files
├── .gitignore
└── README.md
```

---

## ⚙️ Setup & Run

### 🧱 1. Compile

```powershell
$jars = (Get-ChildItem -Path lib -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
& javac -d out -cp $jars (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })
```

### ▶️ 2. Run

```powershell
$runCp = "out" + ";" + $jars
java -cp $runCp gui.GuiApp
```

> 💡 **Tip:** Ensure your `lib/` folder includes `jfreechart-x.x.x.jar` and `jcommon-x.x.x.jar`.

---

## 📸 Screenshots (optional)

| Candlestick Chart                                                             | Trading Controls                                                              |
| ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| <img width="953" height="747" alt="Screenshot 2025-11-13 004404" src="https://github.com/user-attachments/assets/1a72e25d-9ed6-4dd9-9fa8-e824de4a9068" />|
|<img width="1472" height="955" alt="Screenshot 2025-11-13 004423" src="https://github.com/user-attachments/assets/e3abe426-4515-435a-ac0b-7458d8f309a7" />|
---

## 📊 How Prices Are Generated

The `MarketDataFeed` class uses a **stochastic algorithm** to generate smooth, semi-random price movements:

* Each tick slightly moves the price up or down based on momentum and noise.
* Prevents repetitive or predictable patterns.
* Optionally supports real market CSV data (e.g., from Investing.com).

---

## 🎮 Controls Summary

| Action              | Shortcut                                      |
| ------------------- | --------------------------------------------- |
| **Zoom In/Out**     | Mouse Wheel / Buttons                         |
| **Reset Zoom**      | Reset Button                                  |
| **Start Auto Tick** | “Start Auto” Toggle                           |
| **Place Trade**     | Fill order form + Click "Buy/Sell"            |
| **Change Stock**    | Select from dropdown (AAPL, GOOG, MSFT, ONGC) |

---

## 🙋 Author

**Divyam Gupta**
📍 IIIT Nagpur
📧 [divyamguptaofficalpostbox@gmail.com](mailto:divyamguptaofficalpostbox@gmail.com)

---

### ⭐ Star this repo if you like the project!

It helps others find it and shows your support 🚀
