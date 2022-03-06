package com.github.llfrometa89

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import com.github.llfrometa89.Message._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AckResult._
import dev.profunktor.fs2rabbit.model._
import fs2.{Pipe, Stream}
import io.circe._

import java.nio.charset.StandardCharsets.UTF_8

//https://fs2-rabbit.profunktor.dev/consumers/json.html

object ConsumerAsPerson extends IOApp.Simple {

  override def run: IO[Unit] =
    RabbitClient.resource[IO](RabbitConfig.config).use { implicit client =>
      val demo = new AutoAckConsumerDemo(client)
      demo.program
    }
}

class AutoAckConsumerDemo[F[_] : Async](R: RabbitClient[F]) {

  import RabbitConfig._

  implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def errorSink: Pipe[F, Error, Unit] = _.evalMap { error =>
    Async[F].delay(println(s"Error: $error")).map(_ => ())
  }

  def processorSink: Pipe[F, (Person, DeliveryTag), AckResult] = _.evalMap { case (person, deliveryTag) =>
    Async[F].delay(println(s"Consumed: $person / deliveryTag=$deliveryTag")).as(Ack(deliveryTag))
  }

  val program: F[Unit] = R.createConnectionChannel.use { implicit channel =>
    for {
      _ <- R.declareQueue(DeclarationQueueConfig.default(queueName))
      _ <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _ <- R.bindQueue(queueName, exchangeName, routingKey)
//      consumer <- R.createAutoAckConsumer[String](queueName)
      ackerConsumer    <- R.createAckerConsumer[String](queueName)
      (acker, consumer) = ackerConsumer
      _ <- new AutoAckFlow[F, String](consumer, acker, errorSink, processorSink).flow.compile.drain
    } yield ()
  }

}

class AutoAckFlow[F[_] : Async, A](
  consumer: Stream[F, AmqpEnvelope[String]],
  acker: AckResult => F[Unit],
  errorSink: Pipe[F, Error, Unit],
  processorSink: Pipe[F, (Person, DeliveryTag), AckResult]
) {
  import dev.profunktor.fs2rabbit.json.Fs2JsonDecoder
  import io.circe.generic.auto._

  object ioDecoder extends Fs2JsonDecoder

  val flow: Stream[F, Unit] = {
    import ioDecoder._

    consumer.map(jsonDecode[Person]).flatMap {
      case (Left(error), tag) => Stream.eval(Async[F].raiseError(error)).through(errorSink).as(NAck(tag)).evalMap(acker)
      case (Right(msg), tag)  => Stream.eval(Async[F].pure((msg, tag))).through(processorSink).evalMap(acker)
    }
  }

}
