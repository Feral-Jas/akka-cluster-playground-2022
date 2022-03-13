package sample.gdmexchange

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}
import akka.cluster.ddata.{LWWMap, LWWMapKey, ReplicatedData, SelfUniqueAddress}
import sample.Loggable
import sample.gdmexchange.datamodel.DataItemBase

import scala.concurrent.duration.DurationInt

object DistributedDataActor extends Loggable {
  sealed trait Command[T <: DataItemBase]
  final case class GetAllData[T <: DataItemBase](replyTo: ActorRef[DataSet[T]]) extends Command[DataItemBase]
  final case class AddData[T <: DataItemBase](dataItem: DataItemBase)           extends Command[DataItemBase]
  final case class RemoveData[T <: DataItemBase](dataName: String)              extends Command[DataItemBase]

  final case class DataSet[T <: DataItemBase](items: Set[T])

  private sealed trait InternalCommand[T <: DataItemBase] extends Command[T]
  private case class InternalGetResponse[T <: DataItemBase](
      replyTo: ActorRef[DataSet[DataItemBase]],
      rsp: GetResponse[LWWMap[String, DataItemBase]]
  )                                                       extends InternalCommand[DataItemBase]
  private case class InternalUpdateResponse[A <: ReplicatedData](
      rsp: UpdateResponse[A]
  )                                                       extends InternalCommand[DataItemBase]
  private case class InternalRemoveItem[T <: DataItemBase](
      productId: String,
      getResponse: GetResponse[LWWMap[String, DataItemBase]]
  )                                                       extends InternalCommand[DataItemBase]

  private val timeout                = 3.seconds
  private val readMajority           = ReadMajority(timeout)
  private val writeMajority          = WriteMajority(timeout)
  def apply[T <: DataItemBase](
      serviceName: String
  ): Behavior[Command[DataItemBase]] =
    Behaviors.setup { context =>
      DistributedData
        .withReplicatorMessageAdapter[Command[DataItemBase], LWWMap[
          String,
          DataItemBase
        ]] { replicator =>
          implicit val node: SelfUniqueAddress =
            DistributedData(context.system).selfUniqueAddress

          val DataKey =
            LWWMapKey[String, DataItemBase]("ddata-" + serviceName)

          def behavior = Behaviors.receiveMessagePartial(
            receiveGetCart
              .orElse(receiveAddItem)
              .orElse(receiveRemoveItem)
              .orElse(receiveOther)
          )

          def receiveGetCart: PartialFunction[Command[DataItemBase], Behavior[
            Command[DataItemBase]
          ]] = {
            case GetAllData(replyTo) =>
              replicator.askGet(
                askReplyTo => Get(DataKey, readMajority, askReplyTo),
                rsp => InternalGetResponse(replyTo, rsp)
              )
              Behaviors.same

            case InternalGetResponse(replyTo, g @ GetSuccess(DataKey, _)) =>
              val data = g.get(DataKey)
              val cart = DataSet(data.entries.values.toSet)
              replyTo ! cart
              Behaviors.same

            case InternalGetResponse(replyTo, NotFound(DataKey, _)) =>
              replyTo ! DataSet(Set.empty)
              Behaviors.same

            case InternalGetResponse(replyTo, GetFailure(DataKey, _)) =>
              // ReadMajority failure, try again with local read
              replicator.askGet(
                askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
                rsp => InternalGetResponse(replyTo, rsp)
              )

              Behaviors.same
          }

          def receiveAddItem: PartialFunction[Command[DataItemBase], Behavior[
            Command[DataItemBase]
          ]] = { case AddData(item) =>
            replicator.askUpdate(
              askReplyTo =>
                Update(
                  DataKey,
                  LWWMap.empty[String, DataItemBase],
                  writeMajority,
                  askReplyTo
                ) { dataSet =>
                  updateData(dataSet, item)
                },
              InternalUpdateResponse.apply
            )

            Behaviors.same
          }

          def updateData(
              data: LWWMap[String, DataItemBase],
              item: DataItemBase
          ): LWWMap[String, DataItemBase] =
            data :+ (item.dataName -> item)

          def receiveRemoveItem: PartialFunction[Command[
            DataItemBase
          ], Behavior[Command[DataItemBase]]] = {
            case RemoveData(productId) =>
              // Try to fetch latest from a majority of nodes first, since ORMap
              // remove must have seen the item to be able to remove it.
              replicator.askGet(
                askReplyTo => Get(DataKey, readMajority, askReplyTo),
                rsp => InternalRemoveItem(productId, rsp)
              )

              Behaviors.same

            case InternalRemoveItem(productId, GetSuccess(DataKey, _)) =>
              removeItem(productId)
              Behaviors.same

            case InternalRemoveItem(productId, GetFailure(DataKey, _)) =>
              // ReadMajority failed, fall back to best effort local value
              removeItem(productId)
              Behaviors.same

            case InternalRemoveItem(_, NotFound(DataKey, _)) =>
              // nothing to remove
              Behaviors.same
          }

          def removeItem(productId: String): Unit =
            replicator.askUpdate(
              askReplyTo =>
                Update(
                  DataKey,
                  LWWMap.empty[String, DataItemBase],
                  writeMajority,
                  askReplyTo
                ) {
                  _.remove(node, productId)
                },
              InternalUpdateResponse.apply
            )

          def receiveOther: PartialFunction[Command[DataItemBase], Behavior[
            Command[DataItemBase]
          ]] = {
            case InternalUpdateResponse(_: UpdateSuccess[_]) => Behaviors.same
            case InternalUpdateResponse(_: UpdateTimeout[_]) => Behaviors.same
            // UpdateTimeout, will eventually be replicated
            case InternalUpdateResponse(e: UpdateFailure[_]) =>
              throw new IllegalStateException("Unexpected failure: " + e)
          }
          behavior
        }
    }
}
