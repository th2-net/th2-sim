Example of Simulator component env variables:

GRPC_PORT=8080;
RABBITMQ_PASS=some_pass
RABBITMQ_HOST=some-host-name-or-ip
RABBITMQ_PORT=7777
RABBITMQ_VHOST=someVhost
RABBITMQ_USER=some_user


TH2_CONNECTIVITY_ADDRESSES={"connectivity_name1": {"host": "127.0.0.1", "port": 5555}, "connectivity_name2": {"host": "some-host-name", "port": 5454}}
ID=fix-server

or

EXCHANGE_NAME=demo_exchange;
IN_MSG_QUEUE=fix_server_in;
SEND_MSG_QUEUE=fix_server_to_send;
ID=fix-server