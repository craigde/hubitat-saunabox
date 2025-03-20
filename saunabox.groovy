//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.0.0" }
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
    if (state.thermoStateMode == "heat") {
        logDebug("initialize: Setting refresh time to 1 minute")
        runEvery1Minute ("GetSaunaStatus")
    }
    else {
        logDebug("initialize: Setting refresh time to 30 minutes}")
        runEvery30Minutes ("GetSaunaStatus")
	}
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
           
            if (objSaunaStatus.heat.state == 0) {
               sendEvent(name: "thermostatMode", value: "off")
               sendEvent(name: "thermostatOperatingState", value: "idle") 
            }
            else {
               sendEvent(name: "thermostatMode", value: "heat")
               sendEvent(name: "thermostatOperatingState", value: "heating")
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
	logDebug("SaunaOff")
	def command = "/s/0"
	
    logDebug ("saunaOff: command - " + command)
        
       makeHttpRequest("GET", command) { response ->
        if (response.status == 200) {
            logDebug ("Succeeded Response data - ${response.status} - ${response.data}")
        } else {
            log.error "Failed - Response data: ${response.status} - ${response.data}"
        }
    }
    
    GetSaunaStatus()
    logDebug("SaunaOff: Setting refresh time to 30 minutes")
    runEvery30Minutes ("GetSaunaStatus")
}

def SaunaOn() {
	logDebug("SaunaOn")
	def command = "/s/1"
	
    logDebug ("saunaOn: command - " + command)
        
       makeHttpRequest("GET", command) { response ->
        if (response.status == 200) {
            logDebug("Succeeded Response data - ${response.status} - ${response.data}")
        } else {
            log.error "Failed - Response data: ${response.status} - ${response.data}"
        }
    }
    GetSaunaStatus()
    logDebug("SaunaOn: Setting refresh time to 1 minutes")
    runEvery1Minute ("GetSaunaStatus")

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
    if (IsDebug) {log.debug "setTargetTemperature: Setting target temperature to ${temp}°F" }
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
                    
