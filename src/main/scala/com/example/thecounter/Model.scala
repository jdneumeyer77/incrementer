package com.example.thecounter

import zio._
import zio.json._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

object Model {
  case class Increment(key: String, value: Int) {
    def merge(other: Increment): Increment = {
      copy(value = this.value + other.value)
    }
  }

  implicit val decoder: JsonDecoder[Increment] = DeriveJsonDecoder.gen[Increment]
}
