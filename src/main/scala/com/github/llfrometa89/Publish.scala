package com.github.llfrometa89

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.StringVal
import dev.profunktor.fs2rabbit.model._
import fs2.{Pipe, Pure, Stream}
import io.circe.Encoder

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime

object Publish extends IOApp.Simple {

  override def run: IO[Unit] =
    RabbitClient.resource[IO](RabbitConfig.config).use { implicit client =>
      val demo = new AutoAckPublishDemo(client)
      demo.program
    }
}

class AutoAckPublishDemo[F[_] : Async](R: RabbitClient[F]) {
  import RabbitConfig._

  implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])


  val program: F[Unit] = R.createConnectionChannel.use { implicit channel =>
    for {
      _ <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _ <- R.bindQueue(queueName, exchangeName, routingKey)
      publisher <- R.createPublisher[AmqpMessage[String]](exchangeName, routingKey)
      _ <- new PublishFlow[F, String](publisher).flow.compile.drain
    } yield ()
  }

}

class PublishFlow[F[_] : Async, A](publisher: AmqpMessage[String] => F[Unit]) {

  import Message._
  import io.circe.generic.auto._

  private def jsonEncoder = new Fs2JsonEncoder

  def encoderPipe[T: Encoder]: Pipe[F, AmqpMessage[T], AmqpMessage[String]] =
    _.map(jsonEncoder.jsonEncode[T])

  def loggerPipe: Pipe[F, AmqpMessage[String], AmqpMessage[String]] = _.evalMap { amqpMsg =>
    Async[F].delay(println(s"Publish: $amqpMsg")).as(amqpMsg)
  }

  def putStrLn[F[_]: Sync, A](a: A): F[Unit] = Sync[F].delay(println(a))

  val headers: Map[String, AmqpFieldValue] = Map(
    Headers.XMessageID -> StringVal(java.util.UUID.randomUUID().toString),
    Headers.XTenantID -> StringVal("my-tenant"),
    Headers.XApplicationID -> StringVal("my-app"),
    Headers.XOccurredOn -> StringVal(LocalDateTime.now().toString),
  )

  val message = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties(headers = headers))

  val flow: Stream[F, Unit] =
    Stream(
      Stream(message).covary[F]
        .through(encoderPipe[Person])
        .through(loggerPipe)
        .evalMap(publisher)
    ).parJoin(3)
}
