package com.github.llfrometa89

import cats.effect.Async

class Main {


}

trait Event

trait TenantEvent {
  val tenantId: String
}

case class TenantCreatedEvent(tenantId: String) extends TenantEvent

case class TenantUpdatedEvent(tenantId: String) extends TenantEvent

case class TenantDeletedEvent(tenantId: String) extends TenantEvent

trait Consumer {

  def receive[E <: Event](event: E): Unit
}

class TenantConsumer[F[_] : Async] extends Consumer {

  override def receive[E <: Event](event: E): Unit = event match {
    case e: TenantCreatedEvent => println(s"event: $e")
    case e: TenantUpdatedEvent => println(s"event: $e")
    case e: TenantDeletedEvent => println(s"event: $e")
  }
}