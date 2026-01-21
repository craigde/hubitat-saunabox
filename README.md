# Blebox Saunabox Driver for Hubitat

A Hubitat Elevation driver for controlling and monitoring Blebox SaunaBox devices. This driver provides full thermostat integration, automatic safety shutoff, and intelligent polling to seamlessly integrate your sauna into your smart home.

## Overview

The Blebox SaunaBox is a WiFi-enabled controller that turns any traditional sauna into a smart sauna. This Hubitat driver allows you to:

- **Control your sauna** remotely from Hubitat dashboards, rules, and apps
- **Monitor temperature** in real-time with automatic updates
- **Set target temperatures** for your sauna sessions
- **Automatic safety shutoff** - never worry about leaving the sauna on
- **Works with physical controls** - detects and responds to manual button presses
- **Dashboard integration** - displays as a thermostat with full control

### Key Features

#### 🔥 Smart Control
- Turn sauna on/off from anywhere
- Set and maintain target temperatures
- Full Hubitat thermostat capability integration
- Works with Hubitat dashboards and automations

#### 🛡️ Safety First
- Configurable auto-shutoff timer (default: 60 minutes)
- Timer works regardless of how sauna was turned on (app, physical button, etc.)
- Intelligent compensation for external turn-ons
- Automatic timer cancellation when turned off

#### 🔄 Intelligent Polling
- Fast polling (1 minute) when sauna is ON - real-time temperature updates
- Configurable slow polling when OFF - saves network bandwidth
- Automatic detection of external state changes
- Adjusts polling frequency based on sauna state

#### ⚙️ Highly Configurable
- Auto-shutoff duration
- Polling intervals (1, 5, 10, 15, or 30 minutes)
- Timer compensation strategies (Conservative, Balanced, None)
- Imperial or Metric temperature units
- Debug logging

## Requirements

- **Hardware**: Blebox SaunaBox device installed in your sauna
- **Hub**: Hubitat Elevation hub (C-7 or newer recommended)
- **Network**: SaunaBox and Hubitat on same network (or routable)
- **API**: SaunaBox API Level 20180604 or newer

## Installation

### Step 1: Add the Driver Code

1. Log into your Hubitat hub
2. Navigate to **Drivers Code**
3. Click **+ New Driver**
4. Copy and paste the contents of `Driver_Fixed_Simple.groovy`
5. Click **Save**

### Step 2: Create a Virtual Device

1. Navigate to **Devices**
2. Click **+ Add Virtual Device**
3. Enter a descriptive name (e.g., "Sauna")
4. Select **Type**: "Blebox Saunabox"
5. Click **Save Device**

### Step 3: Configure the Device

1. Click on your new sauna device
2. Scroll to **Preferences**
3. Configure the required settings:
   - **SaunaBox IP Address**: Your SaunaBox's local IP (e.g., 192.168.1.100)
   - **Default temp**: Your preferred sauna temperature (default: 170°F)
   - **Auto shutoff timer**: How long before auto-shutoff (default: 60 minutes)
   - **Polling interval when OFF**: How often to check status (default: 5 minutes)
   - **Timer compensation**: Safety strategy (default: Conservative)
   - **Use Imperial units**: Temperature display (default: true for Fahrenheit)
4. Click **Save Preferences**

### Step 4: Test

1. Click the **Refresh** button to get initial status
2. Try turning the sauna on/off using the device controls
3. Check that temperature updates appear
4. Test the physical button on your sauna - verify driver detects the change

## Quick Start Configuration

### Recommended Settings for Most Users

```
SaunaBox IP: [Your SaunaBox IP address]
Default temp: 170°F (or your preference)
Auto shutoff timer: 60 minutes
Polling interval when OFF: 5 minutes
Timer compensation: Conservative
Use Imperial units: Yes (for Fahrenheit)
Debug mode: No
```

These settings provide:
- ✅ Good safety margin (never runs more than ~60 minutes)
- ✅ Quick detection of external turn-ons (within 5 minutes)
- ✅ Minimal network load
- ✅ Predictable behavior

## Basic Usage

### From Hubitat Dashboard

Add your sauna device to a dashboard using the **Thermostat** template:
- **Temperature** - Current sauna temperature
- **Setpoint** - Target temperature
- **Mode** - Heat (on) or Off
- **Operating State** - Heating or Idle

### From Rules & Automations

Use the sauna in Hubitat rules:
```
Trigger: Time is 6:00 PM
Action: Set Sauna to Heat mode
Action: Set Sauna temperature to 170°F
```

```
Trigger: Sauna temperature >= 165°F
Action: Send notification "Sauna is ready!"
```

```
Trigger: Nobody home
Action: Set Sauna to Off mode
```

### Voice Control (via Hubitat integrations)

If you have Alexa or Google Home integrated:
- "Alexa, turn on the sauna"
- "Hey Google, set the sauna to 180 degrees"
- "Alexa, what's the sauna temperature?"

## Understanding Auto-Shutoff & Timer Compensation

### The Challenge

When you turn on your sauna using the physical button (or external app), the Hubitat driver doesn't know immediately. It only discovers the sauna is on during the next polling cycle, which could be up to [polling interval] minutes later.

**Example without compensation:**
- 3:00 PM - Press physical button
- 3:14 PM - Driver detects it's on (14 minutes later)
- 3:14 PM - Timer starts for 60 minutes
- 4:14 PM - Sauna turns off
- **Total runtime: 74 minutes** (14 minutes over!)

### The Solution: Timer Compensation

The driver can compensate for this detection delay by adjusting the timer:

#### Conservative (Recommended)
- Subtracts the **full polling interval** from the timer
- Guarantees sauna never runs over intended time
- May run slightly short if detected quickly
- **Best for safety-conscious users**

#### Balanced
- Subtracts **half the polling interval** from the timer
- Averages out across multiple uses
- May run slightly over or under
- **Best for accuracy-focused users**

#### None
- Uses full timer regardless of detection delay
- User always gets full intended runtime
- May run over by up to [polling interval]
- **Only use with fast polling (1-5 min)**

### Example with Compensation

**Settings:** 60-min auto-off, 5-min polling, Conservative

- 3:00 PM - Press physical button
- 3:03 PM - Driver detects it's on (3 minutes later)
- 3:03 PM - Timer starts for 55 minutes (60 - 5)
- 3:58 PM - Sauna turns off
- **Total runtime: 58 minutes** (safe! ✅)

## Configuration Guide

### Polling Intervals

Choose based on your priorities:

| Interval | Detection Delay | Network Load | Best For |
|----------|----------------|--------------|----------|
| 1 minute | < 1 min | High | Maximum responsiveness |
| **5 minutes** | **< 5 min** | **Low** | **Most users (recommended)** |
| 10 minutes | < 10 min | Very Low | Balanced efficiency |
| 15 minutes | < 15 min | Minimal | Network-conscious |
| 30 minutes | < 30 min | Minimal | Not recommended |

### Auto-Shutoff Duration

Typical ranges:
- **30-45 minutes**: Quick sessions, gym/club saunas
- **60 minutes**: Standard home sauna sessions (recommended)
- **90-120 minutes**: Extended sessions, social gatherings

Tip: If using Conservative compensation with longer polling, increase the auto-shutoff time by 5-10 minutes to account for the timer reduction.

### Compensation Strategies

| Strategy | Runtime Behavior | Trade-off |
|----------|------------------|-----------|
| **Conservative** | 55-60 min (5-min poll) | Safety over precision |
| Balanced | 57.5-62.5 min (5-min poll) | Precision over guarantee |
| None | 60-65 min (5-min poll) | Comfort over safety |

## Troubleshooting

### Sauna Not Responding

1. Verify SaunaBox IP address is correct
2. Check that SaunaBox is on your network
3. Ping the SaunaBox IP from a computer
4. Check hub logs for error messages
5. Try the SaunaBox's web interface directly

### Temperature Not Updating

1. Check that sauna is ON (should poll every 1 minute when on)
2. Enable debug logging in preferences
3. Check logs during next polling cycle
4. Verify SaunaBox API is responding

### Timer Not Working

1. Check logs for timer start/stop messages
2. Verify auto-shutoff timer setting is not 0
3. Test by turning on sauna and watching for auto-shutoff
4. Check that timer cancels when turned off manually

### Detection Too Slow

1. Decrease polling interval (try 5 minutes)
2. Check network latency to SaunaBox
3. Verify hub schedule is running (check logs)

### Running Too Short/Long

**If running too short:**
- Increase auto-shutoff duration by 5-10 minutes, OR
- Change compensation to Balanced, OR
- Decrease polling interval

**If running too long:**
- Use Conservative compensation, OR
- Decrease polling interval, OR
- Check that timer is actually starting (enable debug logs)

## Advanced Features

### Dashboard Integration

Add to dashboard as thermostat template. Recommended tiles:
- Current temperature (large)
- Setpoint control
- Heat/Off mode toggle
- Operating state indicator

### Rule Machine Integration

Example automations:
- **Scheduled warmup**: Turn on sauna 30 minutes before typical usage time
- **Ready notification**: Alert when target temperature reached
- **Presence-based shutoff**: Turn off if house mode changes to Away
- **Temperature presets**: Create scenes for different temperature preferences
- **Usage tracking**: Log sauna sessions to a virtual device counter

### Logging & Debugging

Enable **Debug mode** in preferences to see detailed logging:
- API requests and responses
- Polling schedule changes
- Timer start/stop events
- Mode changes and compensation calculations
- Temperature conversions

## Version History

### Version 1.1.2 (Current)
- ✅ Fixed critical temperature conversion bug - added proper formatting and bounds checking
- ✅ Added comprehensive error handling with try/catch blocks for all API responses
- ✅ Added input validation for IP addresses and temperature ranges
- ✅ Converted all synchronous HTTP calls to async for better responsiveness
- ✅ Added null checks for device state to prevent crashes on first run
- ✅ Improved error handling consistency across all handlers
- ✅ Fixed typos and removed unused code
- ✅ Added constants for magic numbers (refresh delays)
- ✅ Enhanced robustness and production readiness

### Version 1.1.1
- ✅ Fixed auto-shutoff timer bugs
- ✅ Fixed polling frequency not updating
- ✅ Added intelligent timer compensation for external turn-ons
- ✅ Made polling interval configurable
- ✅ Added three compensation strategies
- ✅ Improved logging and error handling
- ✅ Better edge case handling

### Version 1.1.0 (Original)
- Basic thermostat functionality
- API integration with SaunaBox
- Temperature monitoring and control
- Auto-shutoff timer (with bugs)
- Basic polling (with bugs)

## Technical Details

### What Got Fixed in v1.1.2

#### Critical Bug Fix: Temperature Conversion
**Problem**: Temperature formatting used substring which crashed for temps < 10°C or >= 100°C
**Solution**:
- Replaced unsafe substring with proper String.format()
- Added temperature range validation (0-125°C)
- Proper bounds checking before API calls

#### Enhancement: Error Handling
**Problem**: No error handling for malformed API responses or network failures
**Solution**:
- Added try/catch blocks around all JSON parsing
- Validated API response structure before accessing fields
- Added error handling to all async HTTP handlers
- Graceful degradation on errors

#### Enhancement: Async HTTP
**Problem**: Synchronous HTTP calls blocked execution during API requests
**Solution**:
- Converted all HTTP calls to async (asynchttpGet/Post)
- Added 10-second timeout to all requests
- Non-blocking command execution for better responsiveness

#### Enhancement: Input Validation
**Problem**: No validation of user inputs (IP addresses, temperatures)
**Solution**:
- Added IP address format validation with regex
- Added temperature range validation for both °F and °C
- Prevents invalid API calls before they occur

#### Enhancement: Null Safety
**Problem**: device.currentValue() could return null on first run causing crashes
**Solution**:
- Added null coalescing operators (?:) for all device.currentValue() calls
- Proper defaults for first-run scenarios
- Skip state change logic if no previous state exists

### What Got Fixed in v1.1.1

#### Bug 1: Auto-Shutoff Timer
**Problem**: Timer only worked when turned on via driver, didn't work with physical button
**Solution**:
- Added state change detection in polling function
- Timer starts for ANY turn-on event (driver or external)
- Timer properly cancels for ANY turn-off event
- Added intelligent compensation for external turn-ons

#### Bug 2: Polling Frequency
**Problem**: Polling didn't switch between fast/slow based on state
**Solution**:
- Created centralized polling schedule function
- Properly cancels existing schedules before creating new ones
- Detects external state changes and adjusts accordingly
- Uses actual device state instead of non-existent state variables

#### Bug 3: Temperature Updates
**Problem**: Temperature not updating frequently when sauna was on
**Solution**:
- Fixed polling schedule switching
- Ensures 1-minute polling when heating
- Immediate schedule update on state change

### API Integration

The driver communicates with the SaunaBox API:
- **Status endpoint**: `/api/heat/extended/state` - Get current state
- **Control endpoints**: 
  - `/s/0` - Turn off
  - `/s/1` - Turn on
  - `/s/t/[temp]` - Set temperature (in format XXXX = XX.XX°C)

Temperature conversion:
- SaunaBox uses format: `XXXX` = `XX.XX°C`
- Driver handles Fahrenheit/Celsius conversion
- Example: 170°F → 76.67°C → 7667 (API format)

### Polling Behavior

**When OFF:** Configurable interval (1, 5, 10, 15, or 30 minutes)
**When ON:** Fixed 1-minute interval
**Transition:** Immediate when state change detected

### Network Load

Approximate requests per day:
- **OFF (5 min)**: 288 requests/day
- **OFF (15 min)**: 96 requests/day
- **ON (always 1 min)**: 1,440 requests/day

Each request is a lightweight HTTP GET (~200 bytes response)

## Contributing

Found a bug? Have a feature request? 
- Open an issue with detailed description
- Include hub logs if reporting a bug
- Specify your SaunaBox firmware version
- Include your driver configuration

## License

[Specify your license here]

## Credits

- Original driver: craigde
- Version 1.1.1 enhancements: [Your credits]
- Blebox SaunaBox API documentation

## Support

For support:
1. Check the troubleshooting section above
2. Enable debug logging and check logs
3. Post in Hubitat Community forums
4. Include driver version, SaunaBox firmware, and relevant logs

---

**Enjoy your smart sauna! 🧖‍♂️**
