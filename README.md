# CryptoStatus - Futures Tracker

A powerful, feature-rich Android application for tracking cryptocurrency futures, real-time market data, charts, and portfolio management. Monitor open interest, funding rates, Fear & Greed Index, and manage customizable home screen widgets with ease.

![API Level](https://img.shields.io/badge/API%20Level-24%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue)
![Android Studio](https://img.shields.io/badge/Android%20Studio-Latest-green)
![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)

## Features

### 📱 Dashboard & Navigation
- **Multi-tab Navigation**: Home, Charts, Widgets, Discussion, and Blog fragments
- **ViewPager2 Implementation**: Smooth page transitions and fragment management
- **Bottom Navigation View**: Easy access to all major features
- **Theme Customization**: Built-in theme manager for personalized UI

### 📈 Market Data & Charts
- **Real-time Price Tracking**: Monitor cryptocurrency prices with live updates
- **Advanced Chart Visualization**:
  - Open Interest (OI) charts with custom rendering
  - Sparkline renderers for quick trend analysis
  - Multiple timeframe support (15m, 1h, 4h, 1d, 1w, 1mo)
- **Fear & Greed Index**: Visual gauge and metrics for market sentiment
- **Funding Rates Dashboard**: Track crypto funding rates and trends
- **Candlestick & Line Charts**: Comprehensive chart types for technical analysis

### 💼 Portfolio Management
- **Track Holdings**: Add and manage your cryptocurrency portfolio
- **Portfolio Analytics**: Monitor portfolio value, gains/losses
- **Watchlist Management**: Create custom watchlists for tracking
- **Position Tracking**: Manage your crypto positions with entry prices

### 🔔 Alerts & Notifications
- **Price Alarms**: Set custom price alerts for cryptocurrencies
- **Foreground Service**: Continuous background monitoring
- **Smart Notifications**: Real-time notifications when conditions are met
- **Alarm Configuration**: Flexible alarm settings UI

### 🏠 Home Screen Widgets
- **14 Widget Sizes**: From 1x1 to 4x4 grid sizes
- **Real-time Updates**: Widget data syncs with app data
- **Customizable Widgets**:
  - `CryptoWidgetProvider`: Standard crypto price widget
  - `PriceWidgetProvider`: Focused price display widget
  - `FearGreedWidget`: Fear & Greed Index widget
  - Multiple size variants for flexible home screen placement
- **Widget Configuration Activity**: Easy widget setup and customization
- **Persistent Widget Preferences**: Save widget configurations

### 🌐 Community Features
- **Blog Section**: Cryptocurrency news and insights
- **Discussion Forum**: Engage with other traders
- **Blog Adapter**: Feed-style content display

### 🎨 UI/UX Features
- **Dark Mode Support**: Full dark theme implementation
- **Material Design 3**: Modern Material Design components
- **Responsive Layouts**: ConstraintLayout for adaptive UI
- **Smooth Animations**: ViewPager2 transitions and fragment animations
- **Accessibility**: Proper content descriptions and keyboard navigation

---

## Tech Stack

### Core Framework
- **Android API Level**: 24+ (Android 7.0+) to
- **Language**: Kotlin 1.9.24
- **Build System**: Gradle 8.9.0 with Kotlin DSL

### UI Framework
- **AndroidX Core**: Latest stable versions
- **Material Design**: Material Components 1.12.0
- **Layouts**: ConstraintLayout 2.1.4
- **ViewPager2**: Fragment-based navigation
- **AppCompat**: Full backward compatibility

### Networking & API
- **Retrofit 2.9.0**: REST API client
- **Gson**: JSON serialization/deserialization
- **OkHttp**: HTTP client (via Retrofit)
- **Custom API Clients**: CryptoApi, FearGreedApi, ExchangeInfoModels

### Asynchronous Programming
- **Kotlin Coroutines**: Async operations and background tasks
- **Android WorkManager**: Scheduled background work (2.9.0)

### Data Management
- **GSON**: JSON parsing for API responses
- **SharedPreferences**: Widget and user preferences
- **Kotlin Data Classes**: Type-safe model representations

### Testing
- **JUnit 4**: Unit testing framework
- **Android Test Runner**: Instrumentation testing
- **Espresso**: UI testing framework

---

## Requirements

### Development Environment
- **Android Studio**: Latest version (2024.1+)
- **JDK**: Java 11 or higher
- **Gradle**: 8.9.0 (included via wrapper)
- **macOS/Linux/Windows**: Any OS with Android Studio support

### SDK Requirements
- **Minimum SDK**: API Level 24 (Android 7.0)
- **Target SDK**: API Level 35 (Android 15)
- **Compile SDK**: API Level 35

### System Permissions
The app requires the following permissions (defined in AndroidManifest.xml):
- `INTERNET`: Network requests for API calls
- `FOREGROUND_SERVICE`: Background monitoring and notifications
- `FOREGROUND_SERVICE_DATA_SYNC`: Continuous data synchronization
- `POST_NOTIFICATIONS`: Push notifications for alerts

### Network Requirements
- Active internet connection for API calls
- Access to cryptocurrency data APIs (Binance, CoinGecko, etc.)
- Support for WebSocket connections (for real-time data)

---

## Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/CryptoStatus.git
cd CryptoStatus
```

### 2. Open in Android Studio
```bash
# Open the project in Android Studio
open -a "Android Studio" .
```

### 3. Sync Gradle
- Android Studio will automatically prompt you to sync Gradle
- If not, go to `File` → `Sync Now`
- Wait for all dependencies to download

### 4. Setup Local Configuration
Create a `local.properties` file if it doesn't exist:
```properties
sdk.dir=/path/to/Android/sdk
```

### 5. Build the Project
```bash
# Using Gradle Wrapper (recommended)
./gradlew build

# Or build debug APK
./gradlew :app:assembleDebug

# Or build release APK
./gradlew :app:assembleRelease
```

### 6. Run on Emulator/Device
```bash
# Install and run on connected device
./gradlew :app:installDebug

# Or use Android Studio's Run button (Shift + F10)
```

### Configuration Files
- **`gradle.properties`**: JVM memory and encoding settings
- **`local.properties`**: SDK path (auto-generated or manually created)
- **`gradle/libs.versions.toml`**: Centralized dependency versioning
- **`build.gradle.kts`**: Module-level build configuration

---

## 📁 Project Structure

```
CryptoStatus/
├── app/                                      # Main application module
│   ├── build.gradle.kts                     # Module build configuration
│   ├── proguard-rules.pro                   # ProGuard/R8 obfuscation rules
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # App manifest with permissions
│   │   │   ├── java/com/rajvir/FuturesTracker/
│   │   │   │   ├── MainActivity.kt          # Main entry point
│   │   │   │   ├── DashboardActivity.kt     # Dashboard with ViewPager
│   │   │   │   ├── PortfolioActivity.kt     # Portfolio management
│   │   │   │   ├── AlarmListActivity.kt     # Alarm management
│   │   │   │   ├── WidgetConfigActivity.kt  # Widget configuration
│   │   │   │   │
│   │   │   │   ├── Fragments/
│   │   │   │   │   ├── HomeFragment.kt      # Home screen
│   │   │   │   │   ├── ChartsFragment.kt    # Charts and analysis
│   │   │   │   │   ├── FundingDashboardFragment.kt  # Funding rates
│   │   │   │   │   ├── PortfolioFragment.kt # Portfolio view
│   │   │   │   │   ├── WidgetsFragment.kt   # Widget management
│   │   │   │   │   ├── BlogFragment.kt      # Blog/news
│   │   │   │   │   ├── DiscussionFragment.kt # Forum
│   │   │   │   │
│   │   │   │   ├── Widgets/
│   │   │   │   │   ├── CryptoWidgetProvider.kt      # Main widget
│   │   │   │   │   ├── PriceWidgetProvider.kt       # Price widget
│   │   │   │   │   ├── FearGreedWidget.kt           # FG Index widget
│   │   │   │   │   ├── Widget1x1Provider.kt         # 1x1 variant
│   │   │   │   │   ├── Widget1x2Provider.kt         # 1x2 variant
│   │   │   │   │   ├── ... (Widget sizes up to 4x4)
│   │   │   │   │   ├── BaseWidgetProvider.kt        # Base class
│   │   │   │   │   ├── AdaptiveWidgetProvider.kt    # Adaptive widgets
│   │   │   │   │   ├── WidgetUpdater.kt             # Update logic
│   │   │   │   │   ├── WidgetPrefs.kt               # Widget storage
│   │   │   │   │
│   │   │   │   ├── API & Networking/
│   │   │   │   │   ├── CryptoApi.kt                 # Crypto data API
│   │   │   │   │   ├── FearGreedApi.kt              # FG Index API
│   │   │   │   │   ├── ApiClient.kt                 # Retrofit setup
│   │   │   │   │   ├── PriceService.kt              # Price updates
│   │   │   │   │
│   │   │   │   ├── Models/
│   │   │   │   │   ├── CoinModels.kt                # Cryptocurrency models
│   │   │   │   │   ├── PriceModels.kt               # Price data models
│   │   │   │   │   ├── FearGreedModels.kt           # FG Index models
│   │   │   │   │   ├── OpenInterestModels.kt        # OI data models
│   │   │   │   │   ├── FundingModels.kt             # Funding rates models
│   │   │   │   │   ├── PortfolioModels.kt           # Portfolio models
│   │   │   │   │   ├── ExchangeInfoModels.kt        # Exchange data
│   │   │   │   │
│   │   │   │   ├── Adapters/
│   │   │   │   │   ├── BlogAdapter.kt                # Blog feed adapter
│   │   │   │   │   ├── AlarmListAdapter.kt           # Alarm list adapter
│   │   │   │   │   ├── PortfolioAdapter.kt           # Portfolio list
│   │   │   │   │   ├── CoinConfigAdapter.kt          # Coin configuration
│   │   │   │   │   ├── FundingAdapter.kt             # Funding rates list
│   │   │   │   │   ├── SearchDropdownAdapter.kt      # Search dropdown
│   │   │   │   │
│   │   │   │   ├── UI Components/
│   │   │   │   │   ├── FearGreedGaugeView.kt         # FG gauge custom view
│   │   │   │   │   ├── SparklineRenderer.kt          # Sparkline drawing
│   │   │   │   │   ├── OIChartRenderer.kt            # OI chart renderer
│   │   │   │   │
│   │   │   │   ├── Managers/
│   │   │   │   │   ├── AlarmManager.kt               # Alarm management
│   │   │   │   │   ├── WatchlistManager.kt           # Watchlist storage
│   │   │   │   │   ├── ThemeManager.kt               # Theme handling
│   │   │   │   │
│   │   │   │   ├── BottomSheets/
│   │   │   │   │   ├── AddTrackerBottomSheet.kt      # Add tracker UI
│   │   │   │   │   ├── ManageWatchlistSheet.kt       # Watchlist management
│   │   │   │   │
│   │   │   │   ├── Dialogs/
│   │   │   │   │   ├── ThemePickerDialog.kt          # Theme selection
│   │   │   │   │
│   │   │   │   ├── Utils/
│   │   │   │   │   ├── GraphTimeframes.kt            # Chart timeframes
│   │   │   │   │   ├── UpdateIntervals.kt            # Update configurations
│   │   │   │   │
│   │   │   ├── res/                                  # Resources
│   │   │   │   ├── layout/                          # XML layouts
│   │   │   │   ├── drawable/                        # Images and vectors
│   │   │   │   ├── values/                          # Colors, strings, styles
│   │   │   │   ├── xml/                             # Widget info, backup rules
│   │   │   │
│   │   ├── androidTest/                    # Instrumentation tests
│   │   └── test/                           # Unit tests
│   │
│   └── build/                              # Generated build files
│
├── gradle/                                  # Gradle wrapper configs
│   ├── libs.versions.toml                  # Dependency versions
│   └── wrapper/
│       └── gradle-wrapper.properties
│
├── build.gradle.kts                         # Root build file
├── settings.gradle.kts                      # Multi-module settings
├── gradle.properties                        # Global Gradle config
├── gradlew                                  # Gradle wrapper script (Linux/Mac)
├── gradlew.bat                              # Gradle wrapper script (Windows)
├── local.properties                         # Local SDK path (auto-generated)
│
└── Python Scripts/                          # Development utilities
    ├── run.py                              # Run app script
    ├── update_charts.py                    # Chart update utility
    ├── fix_padding.py                      # Layout padding fixer
    ├── fix.py                              # General fixes
    └── fix2.py                             # Additional fixes
```

---

## Architecture

### Architecture Pattern: MVVM + Repository Pattern

The app follows the **Model-View-ViewModel (MVVM)** architecture combined with the **Repository Pattern** for clean data management:

```
┌─────────────────────────────────────────────────────────┐
│                   User Interface Layer                  │
│  (Activities, Fragments, Adapters, Custom Views)       │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                   ViewModel Layer                       │
│  (State management, Coroutines, Business Logic)        │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Repository/Manager Layer                   │
│  (Data aggregation, Business Rules)                    │
│  - AlarmManager, WatchlistManager, ThemeManager        │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────┴──────────────┐
         │                          │
┌────────▼──────────┐      ┌────────▼──────────┐
│  Local Data       │      │  Remote Data      │
│  (SharedPrefs)    │      │  (API Services)   │
│                   │      │                   │
│ - Widget Prefs    │      │ - CryptoApi       │
│ - Watchlists      │      │ - FearGreedApi    │
│ - Alarms          │      │ - Price Service   │
└───────────────────┘      └───────────────────┘
```

### Key Architectural Components

#### 1. **Activities** (UI Controllers)
- `MainActivity`: Entry point
- `DashboardActivity`: Main navigation hub with ViewPager2
- `PortfolioActivity`: Portfolio management
- `AlarmListActivity`: Alarms and notifications
- `WidgetConfigActivity`: Widget setup

#### 2. **Fragments** (Reusable UI)
- Fragment-based navigation for better modularity
- Share common ViewModels with Activities
- Lifecycle-aware components

#### 3. **ViewModels** (State Management)
- Manage UI-related data
- Survive configuration changes
- Handle Coroutine scopes

#### 4. **Services & Managers**
- `PriceService`: Real-time price updates
- `AlarmManager`: Notification and alert management
- `WatchlistManager`: Persistence for watchlists
- `ThemeManager`: Theme application and persistence

#### 5. **API Layer**
- `ApiClient`: Retrofit setup with Gson converters
- `CryptoApi`: Cryptocurrency price and data endpoints
- `FearGreedApi`: Fear & Greed Index data
- Models: Type-safe representations of API responses

#### 6. **Data Models** (POJO/Data Classes)
- `CoinModels`: Cryptocurrency entities
- `PriceModels`: Price data structures
- `FearGreedModels`: Market sentiment models
- `PortfolioModels`: User holdings

#### 7. **Adapters** (RecyclerView/List Binding)
- `BlogAdapter`: Feed display
- `AlarmListAdapter`: Alarm list presentation
- `PortfolioAdapter`: Holdings display
- `FundingAdapter`: Funding rates list

#### 8. **Custom Views** (Advanced UI)
- `FearGreedGaugeView`: Gauge visualization
- `SparklineRenderer`: Mini trend charts
- `OIChartRenderer`: Open Interest visualization

---

## 🔧 Key Components

### 📱 Fragment System

All main screens are implemented as Fragments:
- **HomeFragment**: Dashboard overview
- **ChartsFragment**: Technical analysis with multiple chart types
- **FundingDashboardFragment**: Funding rates and trends
- **PortfolioFragment**: Holdings and assets
- **WidgetsFragment**: Widget management
- **BlogFragment**: News and articles
- **DiscussionFragment**: Community forum

### 🏠 Widget Architecture

The app supports 16 widget sizes (1x1 to 4x4):
- Each size has a dedicated Provider class
- All inherit from `BaseWidgetProvider` for common logic
- `WidgetUpdater` handles refresh logic
- `WidgetPrefs` persists widget configurations
- `CryptoWidgetProvider`: Main cryptocurrency widget
- `PriceWidgetProvider`: Price-focused widget
- `FearGreedWidget`: Market sentiment widget

### 🔔 Alert System

- **AlarmManager**: Manages price alerts and conditions
- **AlarmListActivity**: UI for managing alarms
- **AlarmListAdapter**: Display and interaction
- **Foreground Service**: Continuous monitoring without killing the app
- **Notifications**: User alerts when conditions met

### 🎨 Theme System

- **ThemeManager**: Central theme management
- **ThemePickerDialog**: User-friendly theme selection
- **Support for**: Dark mode, Light mode, System defaults
- **Persistent**: Theme preferences saved to SharedPreferences

### 📊 Chart Rendering

- **OIChartRenderer**: Renders Open Interest charts
- **SparklineRenderer**: Mini trend indicators
- **GraphTimeframes**: Timeframe management (15m, 1h, 4h, 1d, 1w, 1mo)
- **Custom Canvas drawing** for optimized performance

---

## 💻 Usage Guide

### Adding a New Cryptocurrency to Track

1. Open the app and navigate to **Home** or **Charts** tab
2. Tap the **+** button or "Add Tracker"
3. Search for the cryptocurrency
4. Tap to add to your watchlist

### Setting Price Alerts

1. Navigate to **Alarms** tab or menu
2. Tap **Create New Alarm**
3. Select cryptocurrency
4. Set price target and condition (above/below)
5. Configure notification preferences
6. Save

### Customizing Your Portfolio

1. Go to **Portfolio** tab
2. Add holdings with:
   - Cryptocurrency name
   - Quantity
   - Entry price
3. View gains/losses in real-time
4. Edit or remove positions anytime

### Managing Widgets

1. Long-press on home screen
2. Select "Add widget"
3. Choose from multiple CryptoStatus widget sizes
4. Tap **Configure** to customize
5. Select cryptocurrencies to display
6. Set update frequency

### Changing Theme

1. Tap **Settings** (gear icon)
2. Select **Theme**
3. Choose from available themes:
   - Light
   - Dark
   - System Default
4. Changes apply instantly

---

## 🌐 API Integration

### Supported APIs

#### 1. **Cryptocurrency Data** (CryptoApi)
- Real-time price data
- Market cap and volume
- 24h change percentages
- Historical data for charts

**Example Endpoints:**
- `/api/v1/prices` - Get current prices
- `/api/v1/crypto/{symbol}` - Get crypto details
- `/api/v1/charts/{symbol}/{timeframe}` - Get chart data

#### 2. **Fear & Greed Index** (FearGreedApi)
- Current sentiment score
- Historical sentiment data
- Sentiment trends

**Example Response:**
```json
{
  "value": 65,
  "value_classification": "Greed",
  "timestamp": 1234567890
}
```

#### 3. **Exchange Information** (ExchangeInfoModels)
- Trading pairs
- Exchange details
- Market data

### API Implementation Details

**ApiClient.kt** sets up Retrofit:
```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

**Coroutine Integration:**
```kotlin
viewModelScope.launch {
    try {
        val prices = priceService.fetchPrices()
        // Update UI
    } catch (e: Exception) {
        // Handle error
    }
}
```

### Adding New API Endpoints

1. Define the endpoint in the API interface:
```kotlin
interface CryptoApi {
    @GET("api/v1/new-endpoint")
    suspend fun getNewData(): Response<NewDataModel>
}
```

2. Create a data model:
```kotlin
data class NewDataModel(
    val id: String,
    val value: Double
)
```

3. Use in ViewModel via Coroutines:
```kotlin
val data = apiService.getNewData()
```

---

## 🔨 Building & Distribution

### Building Debug APK

```bash
./gradlew :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Building Release APK

```bash
./gradlew :app:assembleRelease
```

**Note:** Release builds require signing configuration. Configure in `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("path/to/keystore.jks")
        storePassword = "your-password"
        keyAlias = "your-key-alias"
        keyPassword = "your-key-password"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

### Building Android Bundle (AAB)

```bash
./gradlew :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

For Google Play distribution.

### Code Obfuscation (ProGuard)

Edit `proguard-rules.pro`:
```
-keep class com.rajvir.FuturesTracker.models.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
```

### Version Management

Update in `app/build.gradle.kts`:
```kotlin
versionCode = 2      // Increment for every release
versionName = "1.1"  // Semantic versioning
```

---

## 🎨 Customization

### Changing App Name

Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">Your App Name</string>
```

### Customizing Colors

Edit `app/src/main/res/values/colors.xml`:
```xml
<color name="primary">#6200EE</color>
<color name="secondary">#03DAC6</color>
```

### Customizing Fonts

1. Add fonts to `app/src/main/res/font/`
2. Reference in styles:
```xml
<item name="fontFamily">@font/custom_font</item>
```

### Adding New Themes

1. Create `app/src/main/res/values-night/colors.xml`
2. Override colors for dark mode
3. Update `ThemeManager.kt` to apply custom themes

### Configuring Update Intervals

Edit `UpdateIntervals.kt`:
```kotlin
object UpdateIntervals {
    const val PRICE_UPDATE_INTERVAL = 5000L      // 5 seconds
    const val CHART_UPDATE_INTERVAL = 60000L     // 1 minute
    const val WIDGET_UPDATE_INTERVAL = 300000L   // 5 minutes
}
```

### Widget Customization

Edit `WidgetPrefs.kt` to add custom preferences:
```kotlin
fun saveWidgetCurrency(appWidgetId: Int, currency: String) {
    prefs.edit().putString("widget_$appWidgetId", currency).apply()
}
```

---

## 🐛 Troubleshooting

### Build Issues

**Problem:** `Gradle sync failed`
```
Solution:
1. File → Invalidate Caches and Restart
2. Delete.gradle folder in project root
3. Sync Gradle again
```

**Problem:** `SDK version too low`
```
Solution: Update compileSdk in app/build.gradle.kts:
compileSdk = 35
```

### Runtime Issues

**Problem:** App crashes on startup
```
Solution:
1. Check logcat: View → Tool Windows → Logcat
2. Look for NullPointerException or ClassNotFoundException
3. Ensure all API endpoints are correct
4. Check manifest for missing activity declarations
```

**Problem:** Widgets not updating
```
Solution:
1. Ensure WidgetUpdater is called correctly
2. Check WorkManager configuration
3. Verify API endpoints are accessible
4. Check widget permissions in manifest
```

**Problem:** Charts not rendering
```
Solution:
1. Verify OIChartRenderer has canvas space
2. Check data is being fetched from API
3. Ensure GraphTimeframes are initialized
4. Check for NaN or infinity values in data
```

**Problem:** Alarms not triggering
```
Solution:
1. Ensure FOREGROUND_SERVICE permission is granted
2. Check AlarmManager is properly initialized
3. Verify app is not being killed by system
4. Check notification settings are enabled
```

### Performance Issues

**Problem:** App is slow/laggy
```
Solution:
1. Use Profiler: Run → Profile App
2. Check for main thread operations
3. Use Coroutines for long-running tasks
4. Reduce refresh intervals temporarily
5. Check for memory leaks
```

**Problem:** Battery drain
```
Solution:
1. Increase UpdateIntervals
2. Use WorkManager for background tasks
3. Reduce sensor usage
4. Disable unnecessary real-time updates
```

### Network Issues

**Problem:** Network requests failing
```
Solution:
1. Check internet connectivity
2. Verify API endpoints are correct
3. Check firewall/proxy settings
4. Test with different network (WiFi/LTE)
5. Check API rate limits
```

---

##  Contributing

### Development Workflow

1. **Fork the repository**
   ```bash
   git clone https://github.com/yourusername/CryptoStatus.git
   ```

2. **Create feature branch**
   ```bash
   git checkout -b feature/feature-name
   ```

3. **Make changes**
   - Follow Kotlin style guide
   - Add descriptive commit messages
   - Write tests for new features

4. **Commit and push**
   ```bash
   git add .
   git commit -m "feat: Add new feature description"
   git push origin feature/feature-name
   ```

5. **Create Pull Request**
   - Include detailed description
   - Reference related issues
   - Add screenshots if UI changes

### Code Style Guidelines

- **Kotlin**: Follow [Official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
- **Naming**: Use camelCase for variables, PascalCase for classes
- **Comments**: Document complex logic and public APIs
- **Imports**: Remove unused imports, organize alphabetically
- **Line Length**: Maximum 120 characters

### Testing Requirements

- Unit tests for models and managers
- Instrumentation tests for UI components
- Test coverage: Aim for 80%+

```bash
# Run all tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest
```

### Reporting Issues

Include in bug reports:
- Reproduction steps
- Expected vs actual behavior
- Device/Android version
- Logcat output
- Screenshots if applicable

---

## 📄 License

This project is licensed under the **MIT License** - see [LICENSE](LICENSE) file for details.

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, and distribute...
```

---


## Future Enhancements

Planned features for upcoming releases:

- [ ] Real-time WebSocket connections for live data
- [ ] Machine learning price predictions
- [ ] Social trading features
- [ ] Cloud sync across devices
- [ ] Advanced portfolio analytics
- [ ] Custom indicator support
- [ ] Backtesting capabilities
- [ ] Push notifications for market events
- [ ] Integration with more exchanges
- [ ] Offline functionality

---

## Statistics

- **Total Classes**: 50+
- **Lines of Code**: 15,000+
- **API Integrations**: 3+
- **Widget Sizes**: 16 variants
- **Supported Cryptocurrencies**: 500+
- **Min SDK**: API 24
- **Target SDK**: API 35

---

**Last Updated**: April 2026  
**Version**: 1.0.0  
**Status**: ✅ Active Development

---

*This README is designed to be a comprehensive guide for developers looking to understand, modify, and extend the CryptoStatus application. For the latest information, visit the [GitHub repository](https://github.com/yourusername/CryptoStatus).*
