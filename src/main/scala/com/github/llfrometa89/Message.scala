package com.github.llfrometa89

object Message {

  case class Address(number: Int, streetName: String)

  case class Person(id: Long, name: String, address: Address)
}
