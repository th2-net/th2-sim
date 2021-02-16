# Simulator
## Description
The Simulator is a service used for simulate different logics.
All the logic is contained inside Rule. 
You can turn on/off rules for one connection or for different connections.
This project is java framework, so it is possible to create a custom Simulator 
## Interfaces
### ISimulator
The main interface of simulator, which contains the logic for managing rules and handle messages 
### ISimulatorServer
The interface used for managing gRPC server
### ISimulatorPart
The interface used for gRPC services to create Rules
### IRuleContext
The interface used for sending rules from IRule
## Triggering rule without income message
You can trigger a rule without an income message if you call the gRPC request ``touchRule``. 
You can transfer arguments to rule with it.
In a rule, you should override the method with the name ``touch``, which will call on triggering.
## Settings
The simulator using schema api for settings. \
Requirements: ``rabbitMq.json``, ``mq.json``, ``grpc.json`` (server only), ``custom.json`` (optional) 
#### Pins in MessageRouter
Simulator subscribe message batches from pins with the attributes: ``first``, ``subscribe``, ``parsed`` \
Simulator sends message bathes to pins with the attributes ``second``, ``publish``, ``parsed`` and which name session alias \
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
It contains the settings for Simulator \
The field `strategyDefaultRules` can take the values `ON_ADD` or `ON_TRIGGER`. 
The default value is set to `ON_TRIGGER`.
If you set the value to `ON_ADD`, the default rules will be disabled if an user adds non-default rule.
If you set the value to `ON_TRIGGER`, the default rules will be disabled if non-default rules will be triggered on the same message. \
*Example:*

```json
{
  "strategyDefaultRules": "ON_ADD",
  "defaultRules": [
    {
      "methodName": "createRuleFIX",
      "enable": false,
      "settings": {
        "fields": {
          "ClOrdID": {
            "simple_value": "order_id"
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
### Custom Resources for infra-mgr
```ymal
apiVersion: th2.exactpro.com/v1
kind: Th2GenericBox
spec:
  type: th2-sim
  custom-config:
    defaultRules:
      - methodName: createDemoRule
        enable: true
        settings:
          fields:
            ClOrdID: 
              simple_value: order_id
          connection_id:
            session_alias: fix-client
  pins:
    - name: subscribe1
      connection-type: mq
      attributes:
        - first
        - subscribe
        - parsed
    - name: send1
      connection-type: mq
      attributes:
        - second
        - publish
        - parsed
        - send1_session_alias
    - name: send2
      connection-type: mq
      attributes:
        - second
        - publish
        - parsed
        - send2_session_alias
```
