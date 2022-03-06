package com.github.llfrometa89

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model._
import fs2.{Pipe, Stream}

import java.nio.charset.StandardCharsets.UTF_8
import Message._

object ConsumerAsString extends IOApp.Simple {

  override def run: IO[Unit] =
    RabbitClient.resource[IO](RabbitConfig.config).use { implicit client =>
      val demo = new AutoAckConsumerAsStringDemo(client)
      demo.program
    }
}

class AutoAckConsumerAsStringDemo[F[_] : Async](R: RabbitClient[F]) {

  import RabbitConfig._

  implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =>
    Sync[F].delay(println(s"Consumed: $amqpMsg")).map(_ => Ack(amqpMsg.deliveryTag))
  }

  val program: F[Unit] = R.createConnectionChannel.use { implicit channel =>
    for {
      _ <- R.declareQueue(DeclarationQueueConfig.default(queueName))
      _ <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _ <- R.bindQueue(queueName, exchangeName, routingKey)
      consumer <- R.createAutoAckConsumer[String](queueName)
      _ <- new AutoAckAsStringFlow[F, String](consumer, logPipe).flow.compile.drain
    } yield ()
  }

}

class AutoAckAsStringFlow[F[_] : Async, A](
  consumer: Stream[F, AmqpEnvelope[A]],
  logger: Pipe[F, AmqpEnvelope[A], AckResult]
) {

  val flow: Stream[F, Unit] =
    Stream(
      consumer.through(logger).through(_.evalMap(f => Sync[F].delay(println(s"f: $f"))))
    ).parJoin(3)

}
