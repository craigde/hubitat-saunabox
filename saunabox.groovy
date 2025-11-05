//	===== Definitions, Installation and Updates =====
//https://technical.blebox.eu/archives/saunaBoxAPI/
def driverVer() { return "1.1.1" }
def apiLevel() { return 20180604 }	//	bleBox latest SaunaBox API Level, 3.19.2025

//import groovy.json.JsonSlurper 

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
    //called on hub startup if driver specifies cabaility "initalize"
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
    
    def currentMode = device.currentValue("thermostatMode")
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
	
    if (resp.getStatus() == 200) {
        logDebug("GetSaunaStatusHandler: Data from SaunaBox - ${resp.data}")
        doPoll(resp)
    } 
    else {log.error "SaunaBox did not return anydata: $resp"}    
}

def doPoll(response){

    logDebug("GetSaunaStatus: Succeeded Response data - ${response.data}")
            
        def objSaunaStatus = parseJson(response.data)
            logDebug("GetSaunaStatus: desired Temp${objSaunaStatus.heat.desiredTemp}")
           
 			//decide the unit type
            if (isFahrenheit) {
               unit = "F" } 
            else {
               unit = "C" 
           }
                                
            sendEvent(name: "heatingSetpoint", value: convertTemp(objSaunaStatus.heat.desiredTemp), unit: unit)
            sendEvent(name: "thermostatSetpoint", value: convertTemp(objSaunaStatus.heat.desiredTemp), unit: unit)
           
            def previousMode = device.currentValue("thermostatMode")
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
            
            // Handle external state changes
            if (previousMode != newMode) {
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
           
            sendEvent(name: "temperature", value: convertTemp(objSaunaStatus.heat.sensors[0].value), unit: unit)
            sendEvent(name: "supportedThermostatModes", value: ["heat", "off"])
            sendEvent(name: "supportedThermostatFanModes", value: ["auto"])
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

    makeHttpRequest("GET", command) { response ->
        if (response.status == 200) {
            logDebug ("SaunaOff: Succeeded Response data - ${response.status} - ${response.data}")
            
            // Update mode immediately
            sendEvent(name: "thermostatMode", value: "off")
            sendEvent(name: "thermostatOperatingState", value: "idle")
            
            // Update polling schedule
            updatePollingSchedule()
            
            // Refresh status
            runIn(2, "GetSaunaStatus")
        } else {
            log.error "SaunaOff: Failed - Response data: ${response.status} - ${response.data}"
        }
    }
}

def SaunaOn() {
    logDebug("SaunaOn: Running")
    def command = "/s/1"

    logDebug ("SaunaOn: command - " + command)

    makeHttpRequest("GET", command) { response ->
        if (response.status == 200) {
            logDebug("SaunaOn: Succeeded Response data - ${response.status} - ${response.data}")
            
            // Update mode immediately
            sendEvent(name: "thermostatMode", value: "heat")
            sendEvent(name: "thermostatOperatingState", value: "heating")
            
            // Update polling schedule
            updatePollingSchedule()
            
            // Schedule auto-shutoff
            Integer minutes = settings?.autoOffMinutes ? settings.autoOffMinutes.toInteger() : 60
            logDebug("SaunaOn: Scheduling auto shutoff in ${minutes} minutes")
            runIn(minutes * 60, "autoShutoff", [overwrite: true])
            
            // Refresh status
            runIn(2, "GetSaunaStatus")
        } else {
            log.error "SaunaOn: Failed - Response data: ${response.status} - ${response.data}"
        }
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
    
    def unit
    if (isFahrenheit) {
        logDebug ("setTargetTemperature: Converting to °C") 
        temp = fahrenheitToCelsius (temp)
        unit = "F"
    } else {
        unit = "C"
    }
    
    //convert temp to Saunabox API format
    temp = temp * 100
    strTemp = temp.toString()
    strTemp = strTemp.substring (0,4)
    logDebug ("setTargetTemperature: Temp ${strTemp} in SaunaBox format")
    
    logDebug ("setTargetTemperature: Setting target temperature to ${strTemp}") 
    logDebug ("setTargetTemperature: originalTemp ${originalTemp}") 

    def path = "/s/t/" + strTemp

    //set temp
    makeHttpRequest("GET", path) { response ->
        if (response.status == 200) {
            logDebug ("setTargetTemperature: Temperature set to ${strTemp}")
            
            //We dont support heating and cooling setpoints so make them all the TargetTemp
            sendEvent(name: "heatingSetpoint", value: originalTemp, unit: unit)
            sendEvent(name: "coolingSetpoint", value: originalTemp, unit: unit)
            sendEvent(name: "thermostatSetpoint", value: originalTemp, unit: unit)
  
        } else {
            log.error "setTargetTemperature: Failed to set temperature: ${response.status} - ${response.data}"
        }
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

//	===== Communications =====
def makeHttpRequest(method, path, closure) {
    if (!settings.saunaBoxIP) {
        log.error "IP not set. Please configure the settings."
        return
    }

    def url = "http://${settings.saunaBoxIP}${path}"
    logDebug ("makeHttpRequest: url = ${url}")  

    try {
        if (method == "POST") {
           logDebug ("makeHttpRequest: POST requested") 
            httpPost(url, closure)
        } else if (method == "GET") {
           logDebug ("makeHttpRequest: GET requested")  
            httpGet(url, closure)
        }
    } catch (Exception e) {
        log.error "Error in ${method} request: ${e.message}"
    }
}
