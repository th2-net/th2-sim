# Simulator
## Description
The Simulator is service for simulate different logic.
All logic contains in a Rule. 
You can turn on/off rules for different connections or some rules for one connection
This project is java framework for creating custom the Simulator 
## Interfaces
### ISimulator
Main interface of simulator, which contains logic for managing rules and handle on message 
### IAdapter
Interface for connection's messages' source
### ISimulatorServer
Interface for managing gRPC server
### ISimulatorPart
Interface for gRPC services for creating Rules
## Settings
Simulator using environment variables for settings
#### GRPC_PORT
Simulator's gRPC server's port
#### RABBITMQ_HOST
RabbitMQ host \
(Example: localhost)
#### RABBITMQ_PORT
RabbitMQ port \
(Example: 8080)
#### RABBITMQ_VHOST
RabbitMQ virtual host \
(Example: vh)
#### RABBITMQ_USER
RabbitMQ user \
(Example: guest)
#### RABBITMQ_PASS
RabbitMQ password \
(Example: guest)
#### TH2_CONNECTIVITY_QUEUE_NAMES
RabbitMQ queues of connectivity \
(Example: {"fix_client": {"exchangeName":"demo_exchange", "toSendQueueName":"client_to_send", "toSendRawQueueName":"client_to_send_raw", "inQueueName": "fix_codec_out_client", "inRawQueueName": "client_in_raw", "outQueueName": "client_out" , "outRawQueueName": "client_out_raw"  }, "fix_server": {"exchangeName":"demo_exchange", "toSendQueueName":"server_to_send", "toSendRawQueueName":"server_to_send_raw", "inQueueName": "fix_codec_out_server", "inRawQueueName": "server_in_raw", "outQueueName": "server_out" , "outRawQueueName": "server_out_raw"  }})