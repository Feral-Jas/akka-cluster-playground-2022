package sample.gdmexchange

import sample.CborSerializable

import java.time.LocalDateTime

/**
 * @author Chenyu.Liu
 */
trait DataItem extends CborSerializable{
  val dataName:String
  val createdAt:LocalDateTime
}

