//	===== Definitions, Installation and Updates =====
//https://technical.blebox.eu/archives/saunaBoxAPI/
def driverVer() { return "1.1.2" }
def apiLevel() { return 20180604 }	//	bleBox latest SaunaBox API Level, 3.19.2025

// Constants
def getRefreshDelaySeconds() { return 2 }  // Delay before refreshing status after command

metadata {
   definition (name: "Blebox Saunabox", namespace: "craigde", author: "craigde") {
       capability "Thermostat"
       capability "Actuator"
       capability "Sensor"
       capability "TemperatureMeasurement"
       command "refresh"
   }
}

 preferences {
    section("SaunaBox IP Address (x.x.x.x):") {
        input "saunaBoxIP", "string", required: true, title: "IP?"
    }
     section("Default temp") {
       input "defaultTemp", "integer", title:"Temp", required:true, defaultValue:170
     }

     section("Auto shutoff timer (minutes)") {
         input "autoOffMinutes", "integer", title:"Auto-off timer (minutes)", required:true, defaultValue:60
     }
     
     section("Polling interval when OFF (minutes)") {
         input "pollingIntervalOff", "enum", 
               title:"Check status every X minutes when OFF", 
               required:true, 
               defaultValue:"5",
               options: ["1", "5", "10", "15", "30"]
     }
     
     section("Timer compensation for external turn-on") {
         input "timerCompensation", "enum",
               title:"How to adjust timer when detecting external turn-on",
               required:true,
               defaultValue:"conservative",
               options: [
                   "conservative": "Conservative - Subtract full polling interval (safest, may run short)",
                   "balanced": "Balanced - Subtract half polling interval (average)",
                   "off": "None - Use full timer (may run over intended time)"
               ]
     }
     
     section("Temperature units") {
         input "isFahrenheit", "bool", title:"Use Imperial units?", required:true, defaultValue:true
     }
     
     section("Log debug information") {
      input "isDebug", "bool", title:"Debug mode", required:true, defaultValue:false
    }     
}

def initialize() {
    //called on hub startup if driver specifies capability "initialize"
    logDebug ("initialize: Running")
    GetSaunaStatus()
    updatePollingSchedule()
}
       
def installed() {
	//called when device is first added
    logDebug ("installed: Running")
    initialize()
}

def refresh() {
    logDebug ("refresh: Running")
    initialize()
}

def updated() {
    //called when preferences are changed
    logDebug ("updated: Running" )
}

def uninstalled() {
    unschedule()
    logDebug ("uninstalled: Running" )
}

def updatePollingSchedule() {
    // Cancel all existing schedules first
    unschedule("GetSaunaStatus")

    def currentMode = device.currentValue("thermostatMode") ?: "off"
    logDebug("updatePollingSchedule: Current mode is ${currentMode}")

    if (currentMode == "heat") {
        logDebug("updatePollingSchedule: Setting refresh time to 1 minute")
        runEvery1Minute("GetSaunaStatus")
    }
    else {
        // Use configurable polling interval when off
        def pollMinutes = settings?.pollingIntervalOff ?: "5"
        logDebug("updatePollingSchedule: Setting refresh time to ${pollMinutes} minutes")

        switch(pollMinutes) {
            case "1":
                runEvery1Minute("GetSaunaStatus")
                break
            case "5":
                runEvery5Minutes("GetSaunaStatus")
                break
            case "10":
                runEvery10Minutes("GetSaunaStatus")
                break
            case "15":
                runEvery15Minutes("GetSaunaStatus")
                break
            case "30":
                runEvery30Minutes("GetSaunaStatus")
                break
        }
	}
}

def GetSaunaStatusHandler(resp, data) {
    try {
        if (resp.getStatus() == 200) {
            logDebug("GetSaunaStatusHandler: Data from SaunaBox - ${resp.data}")
            doPoll(resp)
        }
        else {
            log.error "GetSaunaStatusHandler: Unexpected response status ${resp.getStatus()}"
        }
    } catch (Exception e) {
        log.error "GetSaunaStatusHandler: Error processing response: ${e.message}"
        logDebug("GetSaunaStatusHandler: Stack trace: ${e}")
    }
}

def doPoll(response){

    logDebug("GetSaunaStatus: Succeeded Response data - ${response.data}")

    try {
        def objSaunaStatus = parseJson(response.data)
        logDebug("GetSaunaStatus: desired Temp${objSaunaStatus.heat.desiredTemp}")

        // Validate that we have the expected data structure
        if (!objSaunaStatus?.heat) {
            log.error "doPoll: Invalid response structure - missing 'heat' object"
            return
        }

        //decide the unit type
        def unit
        if (isFahrenheit) {
            unit = "F"
        } else {
            unit = "C"
        }

        sendEvent(name: "heatingSetpoint", value: convertTemp(objSaunaStatus.heat.desiredTemp), unit: unit)
        sendEvent(name: "thermostatSetpoint", value: convertTemp(objSaunaStatus.heat.desiredTemp), unit: unit)

        def previousMode = device.currentValue("thermostatMode") ?: "off"
        def newMode
        def newState

        if (objSaunaStatus.heat.state == 0) {
            newMode = "off"
            newState = "idle"
        }
        else {
            newMode = "heat"
            newState = "heating"
        }

        sendEvent(name: "thermostatMode", value: newMode)
        sendEvent(name: "thermostatOperatingState", value: newState)

        // Handle external state changes (skip if this is the first poll)
        if (previousMode && previousMode != newMode) {
            logDebug("doPoll: Mode changed from ${previousMode} to ${newMode}")

            if (newMode == "heat" && previousMode == "off") {
                // Sauna was turned on externally - start timer with compensation
                logDebug("doPoll: External turn-on detected, starting auto-shutoff timer with compensation")

                Integer autoOffMinutes = settings?.autoOffMinutes ? settings.autoOffMinutes.toInteger() : 60
                def pollingInterval = settings?.pollingIntervalOff ? settings.pollingIntervalOff.toInteger() : 5
                def compensationStrategy = settings?.timerCompensation ?: "conservative"

                Integer compensatedMinutes
                switch(compensationStrategy) {
                    case "conservative":
                        // Subtract full polling interval - safest option
                        compensatedMinutes = autoOffMinutes - pollingInterval
                        logDebug("doPoll: Using conservative compensation: ${autoOffMinutes} - ${pollingInterval} = ${compensatedMinutes} minutes")
                        break
                    case "balanced":
                        // Subtract half polling interval - balanced approach
                        compensatedMinutes = autoOffMinutes - (pollingInterval / 2)
                        logDebug("doPoll: Using balanced compensation: ${autoOffMinutes} - ${pollingInterval/2} = ${compensatedMinutes} minutes")
                        break
                    case "off":
                        // No compensation - use full timer
                        compensatedMinutes = autoOffMinutes
                        logDebug("doPoll: No compensation, using full timer: ${compensatedMinutes} minutes")
                        break
                    default:
                        compensatedMinutes = autoOffMinutes - pollingInterval
                        logDebug("doPoll: Unknown strategy, defaulting to conservative: ${compensatedMinutes} minutes")
                        break
                }

                // Ensure we don't set a negative or zero timer
                if (compensatedMinutes <= 0) {
                    logDebug("doPoll: Compensated time is ${compensatedMinutes}, turning off immediately")
                    SaunaOff()
                } else {
                    runIn(compensatedMinutes * 60, "autoShutoff", [overwrite: true])
                }
            }
            else if (newMode == "off" && previousMode == "heat") {
                // Sauna was turned off externally
                logDebug("doPoll: External turn-off detected, cancelling auto-shutoff timer")
                unschedule("autoShutoff")
            }

            // Update polling schedule
            updatePollingSchedule()
        }
           
        // Validate sensors array exists and has data
        if (!objSaunaStatus.heat.sensors || objSaunaStatus.heat.sensors.size() == 0) {
            log.error "doPoll: Invalid response structure - missing sensors data"
            return
        }

        sendEvent(name: "temperature", value: convertTemp(objSaunaStatus.heat.sensors[0].value), unit: unit)
        sendEvent(name: "supportedThermostatModes", value: ["heat", "off"])
        sendEvent(name: "supportedThermostatFanModes", value: ["auto"])

    } catch (Exception e) {
        log.error "doPoll: Error parsing response data: ${e.message}"
        logDebug("doPoll: Stack trace: ${e}")
    }
} 

def GetSaunaStatus() {
	
    logDebug("GetSaunaStatus: Running")
    
    def command = "/api/heat/extended/state"
    logDebug ("GetSaunaStatus: command - " + command)
   
    def requestParams = [ uri: "http://${settings.saunaBoxIP}${command}" ]
    
    logDebug "GetSaunaStatus: $requestParams"    
    asynchttpGet("GetSaunaStatusHandler", requestParams)
}                      
                      
def SaunaOff() {
    logDebug("SaunaOff: Running")

    // Cancel the auto-shutoff timer
    unschedule("autoShutoff")
    logDebug("SaunaOff: Cancelled auto-shutoff timer")

    def command = "/s/0"
    logDebug ("SaunaOff: command - " + command)

    makeAsyncHttpRequest("GET", command, "SaunaOffHandler")
}

def SaunaOffHandler(resp, data) {
    try {
        if (resp.getStatus() == 200 || resp.getStatus() == 204) {
            logDebug ("SaunaOffHandler: Succeeded Response status - ${resp.getStatus()}")

            // Update mode immediately
            sendEvent(name: "thermostatMode", value: "off")
            sendEvent(name: "thermostatOperatingState", value: "idle")

            // Update polling schedule
            updatePollingSchedule()

            // Refresh status after a short delay
            runIn(getRefreshDelaySeconds(), "GetSaunaStatus")
        } else {
            log.error "SaunaOffHandler: Failed - Response status: ${resp.getStatus()}"
        }
    } catch (Exception e) {
        log.error "SaunaOffHandler: Error processing response: ${e.message}"
        logDebug("SaunaOffHandler: Stack trace: ${e}")
    }
}

def SaunaOn() {
    logDebug("SaunaOn: Running")
    def command = "/s/1"
    logDebug ("SaunaOn: command - " + command)

    makeAsyncHttpRequest("GET", command, "SaunaOnHandler")
}

def SaunaOnHandler(resp, data) {
    try {
        if (resp.getStatus() == 200 || resp.getStatus() == 204) {
            logDebug("SaunaOnHandler: Succeeded Response status - ${resp.getStatus()}")

            // Update mode immediately
            sendEvent(name: "thermostatMode", value: "heat")
            sendEvent(name: "thermostatOperatingState", value: "heating")

            // Update polling schedule
            updatePollingSchedule()

            // Schedule auto-shutoff
            Integer minutes = settings?.autoOffMinutes ? settings.autoOffMinutes.toInteger() : 60
            logDebug("SaunaOnHandler: Scheduling auto shutoff in ${minutes} minutes")
            runIn(minutes * 60, "autoShutoff", [overwrite: true])

            // Refresh status after a short delay
            runIn(getRefreshDelaySeconds(), "GetSaunaStatus")
        } else {
            log.error "SaunaOnHandler: Failed - Response status: ${resp.getStatus()}"
        }
    } catch (Exception e) {
        log.error "SaunaOnHandler: Error processing response: ${e.message}"
        logDebug("SaunaOnHandler: Stack trace: ${e}")
    }
}

def autoShutoff() {
    logDebug("autoShutoff: timer elapsed, turning off")
    SaunaOff()
}

//set temp points
def setHeatingSetpoint(temp) {
    setTargetTemperature(temp)
}

def setCoolingSetpoint(temp) {
    setTargetTemperature(temp)
}

//fan modes - unimplemented
def fanAuto() {}
def fanCirculate() {}
def setThermostatFanMode(fanmode) {}

//heating modes
def auto() { setThermostatMode("auto") }
def cool() { setThermostatMode("cool") }
def emergencyHeat() { setThermostatMode("emergency heat") }
def heat() { setThermostatMode("heat") }
def off() { setThermostatMode("off") }

def setThermostatMode(mode) {
    if (!(mode in ["off", "auto", "cool", "heat", "emergency heat"])) {
        log.warn "setThermostatMode: Unknown mode, ${mode}, ignored"
        return
    } else {
        logDebug("setThermostatMode: mode - ${mode}")    
    }
    if (mode == "emergency heat") {
        // We don't support an emergency backup heater, so...
        mode = "heat"
    }

    if (mode == "heat") {
        SaunaOn() } 
    else {
        SaunaOff()
        mode = "off"
    }
}

def setTargetTemperature(temp) {
    if (isDebug) {log.debug "setTargetTemperature: Setting target temperature to ${temp}°F" }
    def originalTemp = temp

    // Validate input temperature
    if (!validateTemperature(temp, isFahrenheit)) {
        return
    }

    def unit
    if (isFahrenheit) {
        logDebug ("setTargetTemperature: Converting to °C")
        temp = fahrenheitToCelsius (temp)
        unit = "F"
    } else {
        unit = "C"
    }

    // Validate temperature range (SaunaBox supports 0-125°C)
    if (temp < 0 || temp > 125) {
        log.error "setTargetTemperature: Temperature ${temp}°C out of valid range (0-125°C)"
        return
    }

    // Convert temp to Saunabox API format (XXXX = XX.XX°C)
    // Example: 76.67°C → 7667
    def tempInt = Math.round(temp * 100).toInteger()
    def strTemp = String.format("%04d", tempInt)
    logDebug ("setTargetTemperature: Temp ${strTemp} in SaunaBox format (${temp}°C)")

    logDebug ("setTargetTemperature: Setting target temperature to ${strTemp}")
    logDebug ("setTargetTemperature: originalTemp ${originalTemp}")

    def path = "/s/t/" + strTemp

    // Store values in state for the handler to access
    state.pendingTempSet = [originalTemp: originalTemp, unit: unit, strTemp: strTemp]

    //set temp
    makeAsyncHttpRequest("GET", path, "setTargetTemperatureHandler")
}

def setTargetTemperatureHandler(resp, data) {
    try {
        if (resp.getStatus() == 200 || resp.getStatus() == 204) {
            def pendingData = state.pendingTempSet
            if (pendingData) {
                logDebug ("setTargetTemperatureHandler: Temperature set to ${pendingData.strTemp}")

                //We dont support heating and cooling setpoints so make them all the TargetTemp
                sendEvent(name: "heatingSetpoint", value: pendingData.originalTemp, unit: pendingData.unit)
                sendEvent(name: "coolingSetpoint", value: pendingData.originalTemp, unit: pendingData.unit)
                sendEvent(name: "thermostatSetpoint", value: pendingData.originalTemp, unit: pendingData.unit)

                state.remove("pendingTempSet")
            } else {
                log.error "setTargetTemperatureHandler: No pending temperature data found"
            }
        } else {
            log.error "setTargetTemperatureHandler: Failed to set temperature - Response status: ${resp.getStatus()}"
        }
    } catch (Exception e) {
        log.error "setTargetTemperatureHandler: Error processing response: ${e.message}"
        logDebug("setTargetTemperatureHandler: Stack trace: ${e}")
        state.remove("pendingTempSet")
    }
}

// Utils
def logDebug(msg){
	if(isDebug == true) { log.debug "<b>${device.label} ${driverVer()}</b> ${msg}" }
}

def convertTemp(value) {
//converts value from format provided by Saunabox XXXX into actual temp XX.XX F or C

	if (isFahrenheit) {
		return value/100 * 1.8 + 32

	} else {
		return value/100
	}
}

def validateIPAddress(ip) {
    // Basic IP address validation
    if (!ip || ip.trim() == "") {
        log.error "validateIPAddress: IP address is empty"
        return false
    }

    // Check for basic IPv4 format (x.x.x.x)
    def ipPattern = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/
    def matcher = ip =~ ipPattern

    if (!matcher.matches()) {
        log.error "validateIPAddress: Invalid IP format: ${ip}"
        return false
    }

    // Check each octet is 0-255
    for (int i = 1; i <= 4; i++) {
        def octet = matcher[0][i].toInteger()
        if (octet < 0 || octet > 255) {
            log.error "validateIPAddress: IP octet out of range (0-255): ${octet}"
            return false
        }
    }

    return true
}

def validateTemperature(temp, isFahrenheitUnit) {
    // Validate temperature is within reasonable sauna range
    def minTemp, maxTemp, unit

    if (isFahrenheitUnit) {
        minTemp = 32    // 0°C
        maxTemp = 257   // 125°C
        unit = "°F"
    } else {
        minTemp = 0
        maxTemp = 125
        unit = "°C"
    }

    if (temp < minTemp || temp > maxTemp) {
        log.error "validateTemperature: Temperature ${temp}${unit} out of valid range (${minTemp}-${maxTemp}${unit})"
        return false
    }

    return true
}

//	===== Communications =====
def makeAsyncHttpRequest(method, path, handlerMethod) {
    if (!settings.saunaBoxIP) {
        log.error "makeAsyncHttpRequest: IP not set. Please configure the settings."
        return
    }

    if (!validateIPAddress(settings.saunaBoxIP)) {
        log.error "makeAsyncHttpRequest: Invalid IP address. Please check settings."
        return
    }

    def url = "http://${settings.saunaBoxIP}${path}"
    logDebug ("makeAsyncHttpRequest: url = ${url}")

    def requestParams = [
        uri: url,
        timeout: 10  // 10 second timeout
    ]

    try {
        if (method == "POST") {
            logDebug ("makeAsyncHttpRequest: POST requested")
            asynchttpPost(handlerMethod, requestParams)
        } else if (method == "GET") {
            logDebug ("makeAsyncHttpRequest: GET requested")
            asynchttpGet(handlerMethod, requestParams)
        }
    } catch (Exception e) {
        log.error "makeAsyncHttpRequest: Error in ${method} request: ${e.message}"
    }
}
