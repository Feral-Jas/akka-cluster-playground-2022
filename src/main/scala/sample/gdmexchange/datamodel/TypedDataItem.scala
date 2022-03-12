package sample.gdmexchange.datamodel

import java.time.{LocalDateTime, ZoneId}

/** @author Chenyu.Liu
  */
final case class TypedDataItem(
    override val dataName: String,
    `type`: TypedDataItem.DType = TypedDataItem.CONFIG,
    stringValueOpt: Option[String] = None,
    decimalValueOpt: Option[BigDecimal] = None,
    override val createdAt: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC"))
) extends DataItemBase

/** TypedDataItem
  * used to define item type stored in distributed data
  */
object TypedDataItem extends Enumeration {
  type DType = Value
  val CONFIG: DType = Value("config")
  val CACHE: DType = Value("cache")
}
