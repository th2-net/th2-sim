# Simulator core
![version](https://img.shields.io/badge/version-7.0.0-blue.svg)
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
Simulator sends message group to pins with the attributes ``second``, ``publish`` \
_From **4.0.0** there no session-alias attribute anymore, please use **filter** instead._

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
      "attributes": ["second", "publish"]
    },
    "send2": {
      "name": "send2_name",
      "queue": "send2_queue",
      "exchange": "send2_exchange",
      "attributes": ["second", "publish"]
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
      filters:
        - metadata:
            - field-name: session_alias
              expected-value: some_alias_first
              operation: EQUAL

    - name: send2
      connection-type: mq
      attributes:
        - second
        - publish
      filters:
        - metadata:
            - field-name: session_alias
              expected-value: some_alias_second
              operation: EQUAL
```

## Changelog

### 7.0.0

+ Migrate to th2 transport protocol
+ Migrate to book and pages
  + Update common version to 5.2.1-dev

### 5.2.3
+ Fixed bug of sending AnyMessage without event_id setup

### 5.2.2
+ Update `common-j` from 3.31.6 to 3.41.1
+ Update `bom` from 3.1.0 to 4.0.2
+ Update `kotlin` from 1.5.31 to 1.6.21

### 5.2.1
+ Removed duplicate rule-removal event

### 5.2.0
+ Toolkit logic updated: all errors will be thrown as unexpected

### 5.1.0
+ Fixed bug of not throwing assertion error

### 5.0.0
+ Send event on rule message handling error
+ Updated text of rule creation event
+ Send event on default rule creation error
+ Migration to books/pages cradle 4.0.0
  + Update `common-j` to 4.0.0

### 4.1.0
+ Updated rule context, supports message groups and raw message as output of rule

### 4.0.0
+ Update `common-j` to 3.31.6
+ Removed session-alias as argument for publish pins. Please use filter instead

### 3.9.0
+ Added testFixtures as test utils for rules

### 3.8.0
+ Update `common-j` to 3.21.2

### v3.7.0
+ Added ability to schedule execution of arbitrary actions via `IRuleContext.execute` methods

### v3.6.0
+ Added `IRuleContext.removeRule()` method which allows a rule to remove itself