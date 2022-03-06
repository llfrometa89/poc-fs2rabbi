# poc-fs2rabbi

## Run RabbitMQ doceker
```shell
docker run -d --hostname my-rabbit --name some-rabbit -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin -e RABBITMQ_DEFAULT_VHOST=my_vhost  -p 5672:5672 -p 5673:5673 -p 15672:15672 rabbitmq:3-management
```

## Resources
- https://fs2-rabbit.profunktor.dev/publishers/publisher.html
- http://localhost:15672/#/queues/
- https://fs2-rabbit.profunktor.dev/consumers/json.html
- https://github.com/profunktor/fs2-rabbit/blob/master/examples/src/main/scala/dev/profunktor/fs2rabbit/examples/AckerConsumerDemo.scala
- https://github.com/profunktor/fs2-rabbit/blob/master/examples/src/main/scala/dev/profunktor/fs2rabbit/examples/package.scala
