package sample.gdmexchange.datamodel

import sample.CborSerializable

import java.time.LocalDateTime

/** @author Chenyu.Liu
  */
trait DataItemBase extends CborSerializable {
  val dataName: String
  val createdAt: LocalDateTime
}
