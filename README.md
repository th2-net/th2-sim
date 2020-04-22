Example of Simulator component env variables:

RABBITMQ_PASS=some_pass
RABBITMQ_HOST=some_host_name_or_ip
RABBITMQ_PORT=7777
RABBITMQ_VHOST=someVhost
RABBITMQ_USER=some_user
TH2_SIMULATOR_GRPC_PORT=8080


TH2_CONNECTIVITY_ADDRESSES={"connectivity_name1": {"host": "127.0.0.1", "port": 5555}, "connectivity_name2": {"host": "some-host-name", "port": 5454}}
TH2_SIMULATOR_CONNECTIVITY_ID=connectivity_name1

or

TH2_SIMULATOR_EXCHANGE_NAME=exchange_name
TH2_SIMULATOR_IN_MSG_QUEUE=service_in_queue
TH2_SIMULATOR_SEND_MSG_QUEUE=service_to_send_queue
TH2_SIMULATOR_CONNECTIVITY_ID=connectivity_name1