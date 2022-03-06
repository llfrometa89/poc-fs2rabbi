package com.github.llfrometa89

import cats.data.NonEmptyList
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}

import scala.concurrent.duration.DurationInt

object RabbitConfig {

  val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "my_vhost",
    nodes = NonEmptyList.one(
      Fs2RabbitNodeConfig(
        host = "127.0.0.1",
        port = 5672
      )
    ),
    username = Some("admin"),
    password = Some("admin"),
    ssl = false,
    connectionTimeout = 3.seconds,
    requeueOnNack = false,
    requeueOnReject = false,
    internalQueueSize = Some(500),
    requestedHeartbeat = 60.seconds,
    automaticRecovery = true
  )


  val queueName = QueueName("test.person.queue")
  val exchangeName = ExchangeName("events")
  val routingKey = RoutingKey("test.person.created")
}
