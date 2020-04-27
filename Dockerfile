FROM openjdk:12-alpine

ENV GRPC_PORT=8080 \
    RABBITMQ_HOST=host \
    RABBITMQ_PORT=7777 \
    RABBITMQ_VHOST=vhost \
    RABBITMQ_USER=user \
    RABBITMQ_PASS=password \
    TH2_CONNECTIVITY_ADDRESSES={}

WORKDIR /home
COPY ./ .
ENTRYPOINT ["/home/th2-simulator/bin/th2-simulator"]