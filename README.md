# Simulator
## Description
The Simulator is service for simulate different logic.
All logic contains in a Rule. 
You can turn on/off rules for different connections or some rules for one connection.
This project is java framework for creating custom the Simulator 
## Interfaces
### ISimulator
Main interface of simulator, which contains logic for managing rules and handle on message 
### ISimulatorServer
Interface for managing gRPC server
### ISimulatorPart
Interface for gRPC services for creating Rules
## Settings
Simulator using schema api for settings. \
Requirements: ``rabbitMq.json``, ``mq.json``, ``grpc.json`` (server only), ``custom.json`` (optional) 
#### Pins in MessageRouter
Simulators subscribe message batches from pins with attributes: ``first``, ``subscribe``, ``parsed`` \
Simulator sends message bathes to pins with attributes ``second``, ``publish``, ``parsed`` and which name session alias \
*Example:*
```json
{
  "queues": {
    "subscribe1":{
      "name": "subscribe1_name",
      "queue": "subscribe1_queue",
      "exchange": "subscribe1_exchange",
      "attributes": ["first", "subscribe", "parsed"]
    },
    "send1": {
      "name": "send1_name",
      "queue": "send1_queue",
      "exchange": "send1_exchange",
      "attributes": ["second", "publish", "parsed", "send1_session_alias"]
    },
    "send2": {
      "name": "send2_name",
      "queue": "send2_queue",
      "exchange": "send2_exchange",
      "attributes": ["second", "publish", "parsed", "send2_session_alias"]
    }
  }
}
```
#### Custom configuration
Have only settings for defaults rules \
*Example:*
```json
{ "defaultRules" : [
        {
          "methodName": "createRuleFIX",
          "enable": false,
          "settings": {
            "fields": {
              "ClOrdID": {
                "simple_value":"order_id"
              }
            },
            "connection_id": {
              "session_alias": "fix-client"
            }
          }
        }
    ]
}
```
