package com.github.llfrometa89

import cats.data.Kleisli
import cats.effect.{Async, IO, IOApp, Sync}
import cats.implicits._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model._
import fs2.{Pipe, Stream}

import java.nio.charset.StandardCharsets.UTF_8

object Main extends IOApp.Simple {

  override def run: IO[Unit] =
    RabbitClient.resource[IO](RabbitConfig.config).use { implicit client =>
      program[IO](client)
    }


  def program[F[_] : Async](R: RabbitClient[F]): F[Unit] = {

    val handlers: Seq[Handler] = Seq(
      Handler(classOf[TenantConsumer[F]]) events Seq(classOf[TenantCreatedEvent]) routingKey "test.person.created" queue "test.person.queue",
    )

    val subscriber = new ConsumerSubscriber(R)
    subscriber.subscribe(handlers)
  }

}

case class Handler(eventHandler: Class[_ <: Consumer], eventValues: Seq[Class[_ <: Event]] = Seq(), routingKeyValue: String = "", queueValue: String = "") {

  def events(e: Seq[Class[_ <: Event]]): Handler = {
    Handler(eventHandler, eventValues ++: e, routingKeyValue)
  }

  def routingKey(routingKey: String): Handler = Handler(eventHandler, eventValues, routingKey, queueValue)

  def queue(queue: String): Handler = Handler(eventHandler, eventValues, routingKeyValue, queue)

}

class ConsumerSubscriber[F[_] : Async](R: RabbitClient[F]) {

  implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def bind(exchange: ExchangeName, handler: Handler)(implicit channel: AMQPChannel): F[Stream[F, AmqpEnvelope[String]]] = for {
    _ <- R.declareQueue(DeclarationQueueConfig.default(QueueName(handler.queueValue)))
    _ <- R.bindQueue(QueueName(handler.queueValue), exchange, RoutingKey(handler.routingKeyValue))
    consumer <- R.createAutoAckConsumer[String](QueueName(handler.queueValue))
  } yield consumer

  def subscribe(handlers: Seq[Handler]): F[Unit] =
    R.createConnectionChannel.use { implicit channel =>
      for {
        _ <- Async[F].pure(println(s"subscribe: $handlers"))
        _ <- R.declareExchange(RabbitConfig.exchangeName, ExchangeType.Topic)
        consumers <- handlers.traverse(h => bind(RabbitConfig.exchangeName, h))
        _ <- multipleConsumers(consumers).compile.drain
      } yield ()
    }

  def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =>
    Sync[F].delay(println(s"Consumed: $amqpMsg")).map(_ => Ack(amqpMsg.deliveryTag))
  }

  def putStrLn[F[_]: Sync, A](a: A): F[Unit] = Sync[F].delay(println(a))

  def multipleConsumers(consumers: Seq[Stream[F, AmqpEnvelope[String]]]): Stream[F, Unit] = {
    Stream(
      consumers.map(_.through(logPipe).through(_.evalMap(putStrLn(_)))): _*
    ).parJoin(consumers.size)
  }
}

trait Event

trait TenantEvent extends Event {
  val tenantId: String
}

case class TenantCreatedEvent(tenantId: String) extends TenantEvent

case class TenantUpdatedEvent(tenantId: String) extends TenantEvent

case class TenantDeletedEvent(tenantId: String) extends TenantEvent

trait Consumer {
  type Receive = PartialFunction[Event, Unit]

  def receive: Receive
}

class TenantConsumer[F[_] : Sync] extends Consumer {

  override def receive: Receive = {
    case e: TenantCreatedEvent => Sync[F].pure(println(s"event: $e"))
    case e: TenantUpdatedEvent => Sync[F].pure(println(s"event: $e"))
    case e: TenantDeletedEvent => Sync[F].pure(println(s"event: $e"))
  }
}