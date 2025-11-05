# hubitat-saunabox

# Sauna Driver - Configurable Version Guide

## Overview

This driver now gives you complete control over:
1. **Auto-shutoff timer duration** (default: 60 minutes)
2. **Polling interval when OFF** (default: 5 minutes)
3. **Timer compensation strategy** (default: Conservative)

## Understanding the Settings

### 1. Auto Shutoff Timer (minutes)
**What it does:** Automatically turns off the sauna after this many minutes
**Default:** 60 minutes
**Typical range:** 30-120 minutes

**Example:**
- Set to 60: Sauna turns off after 1 hour
- Set to 90: Sauna turns off after 1.5 hours

### 2. Polling Interval When OFF (minutes)
**What it does:** How often to check sauna status when it's off
**Default:** 5 minutes
**Options:** 1, 5, 10, 15, or 30 minutes

**Impact on detection time:**
- 1 minute: External turn-on detected within 1 minute (most network load)
- 5 minutes: External turn-on detected within 5 minutes (recommended)
- 10 minutes: External turn-on detected within 10 minutes (balanced)
- 15 minutes: External turn-on detected within 15 minutes (less network load)
- 30 minutes: External turn-on detected within 30 minutes (not recommended)

**When ON:** Always polls every 1 minute regardless of this setting

### 3. Timer Compensation Strategy
**What it does:** Adjusts the auto-shutoff timer when an external turn-on is detected

**Why needed:** When you turn the sauna on via physical button, the driver doesn't know about it until the next polling cycle. The sauna has been running for somewhere between 0 and [polling interval] minutes.

#### Strategy Options:

**Conservative (Recommended):**
- Subtracts the FULL polling interval from the timer
- **Guarantees** sauna never runs longer than intended
- May run slightly shorter than intended if detected quickly
- Formula: `Timer = Auto-off setting - Polling interval`

**Balanced:**
- Subtracts HALF the polling interval from the timer
- Averages out over many uses
- May run slightly over or under intended time
- Formula: `Timer = Auto-off setting - (Polling interval / 2)`

**None:**
- Uses full timer regardless of when detected
- User always gets full intended runtime
- May run up to [polling interval] over intended time
- Formula: `Timer = Auto-off setting`

---

## Example Scenarios

### Scenario A: Conservative + 5 min polling
**Settings:**
- Auto-shutoff: 60 minutes
- Polling interval: 5 minutes
- Compensation: Conservative

**Timeline:**
- 3:00 PM - Press physical button
- 3:03 PM - Driver detects it's on (next poll)
- Timer starts at 55 minutes (60 - 5)
- 3:58 PM - Auto-shutoff triggers
- **Total runtime: 58 minutes** ✅

**Analysis:**
- Intended: 60 min
- Actual: 58 min
- Difference: -2 min (ran 2 min short)
- Safety: ✅ Never over

---

### Scenario B: Balanced + 10 min polling
**Settings:**
- Auto-shutoff: 60 minutes
- Polling interval: 10 minutes
- Compensation: Balanced

**Timeline:**
- 3:00 PM - Press physical button
- 3:07 PM - Driver detects it's on
- Timer starts at 55 minutes (60 - 5)
- 4:02 PM - Auto-shutoff triggers
- **Total runtime: 62 minutes** 😐

**Analysis:**
- Intended: 60 min
- Actual: 62 min
- Difference: +2 min (ran 2 min over)
- Safety: ⚠️ Slightly over

---

### Scenario C: None + 15 min polling
**Settings:**
- Auto-shutoff: 60 minutes
- Polling interval: 15 minutes
- Compensation: None

**Timeline:**
- 3:00 PM - Press physical button
- 3:14 PM - Driver detects it's on
- Timer starts at 60 minutes (no compensation)
- 4:14 PM - Auto-shutoff triggers
- **Total runtime: 74 minutes** ❌

**Analysis:**
- Intended: 60 min
- Actual: 74 min
- Difference: +14 min (ran 14 min over)
- Safety: ❌ Significantly over

---

### Scenario D: Conservative + 15 min polling
**Settings:**
- Auto-shutoff: 60 minutes
- Polling interval: 15 minutes
- Compensation: Conservative

**Timeline:**
- 3:00 PM - Press physical button
- 3:02 PM - Driver detects it's on (lucky, detected quickly!)
- Timer starts at 45 minutes (60 - 15)
- 3:47 PM - Auto-shutoff triggers
- **Total runtime: 47 minutes** 😞

**Analysis:**
- Intended: 60 min
- Actual: 47 min
- Difference: -13 min (ran 13 min short)
- Safety: ✅ Never over, but much shorter

---

## Recommended Configurations

### For Safety (Recommended for most users)
```
Auto-shutoff: 60 minutes
Polling interval: 5 minutes
Compensation: Conservative
```
**Result:** 55-60 minute runtime, never over, minimal detection delay

### For Accuracy (If you want precise timing)
```
Auto-shutoff: 60 minutes
Polling interval: 1 minute
Compensation: Balanced
```
**Result:** 59-61 minute runtime, very accurate

### For Efficiency (Minimize network traffic)
```
Auto-shutoff: 60 minutes
Polling interval: 15 minutes
Compensation: Conservative
```
**Result:** 45-60 minute runtime, less network load, less predictable

### For Full Runtime (User comfort over precision)
```
Auto-shutoff: 60 minutes
Polling interval: 10 minutes
Compensation: None
```
**Result:** 60-70 minute runtime, may run over but user always gets full time

---

## The Tradeoff Triangle

You can't optimize all three - pick what matters most:

```
        Safety
       /      \
      /        \
     /  Pick 2  \
    /____________\
Efficiency    Accuracy
```

- **Safety + Accuracy** = Use fast polling (1-5 min) + Conservative
- **Safety + Efficiency** = Use slow polling (15 min) + Conservative (but less accurate)
- **Accuracy + Efficiency** = Use slow polling (15 min) + Balanced (but may run over)

---

## My Personal Recommendation

Start with these settings and adjust based on experience:

```
Auto-shutoff timer: 60 minutes
Polling interval when OFF: 5 minutes
Timer compensation: Conservative
```

**Why:**
- 5 min polling is negligible network load
- Conservative ensures safety feature works
- Max 5-minute detection delay is acceptable
- Worst case: 55 minutes instead of 60 (still plenty of sauna time!)
- If you find you want more time, increase auto-shutoff to 65 or 70

After a week or two, you'll know if you want to adjust:
- Sauna running too short? → Increase auto-shutoff to 70 min
- Want less network traffic? → Increase polling to 10 min
- Want more accuracy? → Change to Balanced compensation

---

## Technical Details

### When Timer Starts:
- **Via driver/app:** Immediately when you press "heat"
- **Via physical button:** At next polling cycle (delayed by 0 to [polling interval] minutes)

### Timer Compensation Only Applies To:
- External turn-ons (physical button, other apps)
- Does NOT apply when turned on via this driver

### Edge Case Handling:
- If compensated time ≤ 0, sauna turns off immediately
- Timer always cancels when sauna turns off (any method)
- Timer always restarts if turned on again

### Network Impact:
- 1 min polling: 1,440 polls/day when OFF
- 5 min polling: 288 polls/day when OFF (recommended)
- 10 min polling: 144 polls/day when OFF
- 15 min polling: 96 polls/day when OFF
- When ON: Always 1,440 polls/day (1-min intervals)

---

## Testing Your Configuration

1. Set your desired configuration
2. Turn on sauna via physical button
3. Check logs to see:
   - When external turn-on was detected
   - What compensated timer was set to
4. Verify it turns off at expected time
5. Adjust settings if needed

**Example log output:**
```
doPoll: Mode changed from off to heat
doPoll: External turn-on detected, starting auto-shutoff timer with compensation
doPoll: Using conservative compensation: 60 - 5 = 55 minutes
```

---

## FAQ

**Q: Why not just use 1-minute polling all the time?**
A: When sauna is off, you don't need frequent updates. 5-15 minute checks are fine and save hub resources.

**Q: Does compensation apply when I turn on via the app?**
A: No, only for external turn-ons. When you use the driver/app, timer starts immediately with full duration.

**Q: What if I change settings while sauna is running?**
A: Changes take effect on next turn-on. Current session continues with old settings.

**Q: Can I disable auto-shutoff completely?**
A: Not currently, but you can set it to a very high value (e.g., 300 minutes = 5 hours)

**Q: Which strategy should I use?**
A: Conservative for safety, Balanced for accuracy, None only if you're okay with overtime.
