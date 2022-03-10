package sample.gdmexchange

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}
import akka.cluster.ddata.{LWWMap, LWWMapKey, ReplicatedData, SelfUniqueAddress}
import sample.CborSerializable

import scala.concurrent.duration.DurationInt

object DistributedConfig {
  sealed trait Command
  final case class GetAllConfig(replyTo: ActorRef[ConfigSet]) extends Command
  final case class AddConfig(configItem: ConfigItem) extends Command
  final case class RemoveConfig(configName: String) extends Command

  final case class ConfigSet(items: Set[ConfigItem])
  final case class ConfigItem(configName: String, stringValueOpt: Option[String] = None, decimalValueOpt: Option[BigDecimal] = None)extends CborSerializable

  private sealed trait InternalCommand extends Command
  private case class InternalGetResponse(replyTo: ActorRef[ConfigSet], rsp: GetResponse[LWWMap[String, ConfigItem]]) extends InternalCommand
  private case class InternalUpdateResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalRemoveItem(productId: String, getResponse: GetResponse[LWWMap[String, ConfigItem]]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)
  def apply(serviceName: String): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, ConfigItem]] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      val DataKey = LWWMapKey[String, ConfigItem]("config-" + serviceName)

      def behavior = Behaviors.receiveMessagePartial(
        receiveGetCart
          .orElse(receiveAddItem)
          .orElse(receiveRemoveItem)
          .orElse(receiveOther)
      )

      def receiveGetCart: PartialFunction[Command, Behavior[Command]] = {
        case GetAllConfig(replyTo) =>
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalGetResponse(replyTo, rsp))

          Behaviors.same

        case InternalGetResponse(replyTo, g @ GetSuccess(DataKey, _)) =>
          val data = g.get(DataKey)
          val cart = ConfigSet(data.entries.values.toSet)
          replyTo ! cart
          Behaviors.same

        case InternalGetResponse(replyTo, NotFound(DataKey, _)) =>
          replyTo ! ConfigSet(Set.empty)
          Behaviors.same

        case InternalGetResponse(replyTo, GetFailure(DataKey, _)) =>
          // ReadMajority failure, try again with local read
          replicator.askGet(
            askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
            rsp => InternalGetResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveAddItem: PartialFunction[Command, Behavior[Command]] = {
        case AddConfig(item) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, LWWMap.empty[String, ConfigItem], writeMajority, askReplyTo) {
              configSet => updateConfig(configSet, item)
            },
            InternalUpdateResponse.apply)

          Behaviors.same
      }

      def updateConfig(data: LWWMap[String, ConfigItem], item: ConfigItem): LWWMap[String, ConfigItem] = {
        data :+ (item.configName -> item)
      }

      def receiveRemoveItem: PartialFunction[Command, Behavior[Command]] = {
        case RemoveConfig(productId) =>
          // Try to fetch latest from a majority of nodes first, since ORMap
          // remove must have seen the item to be able to remove it.
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalRemoveItem(productId, rsp))

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

      def removeItem(productId: String): Unit = {
        replicator.askUpdate(
          askReplyTo => Update(DataKey, LWWMap.empty[String, ConfigItem], writeMajority, askReplyTo) {
            _.remove(node, productId)
          },
          InternalUpdateResponse.apply)
      }

      def receiveOther: PartialFunction[Command, Behavior[Command]] = {
        case InternalUpdateResponse(_: UpdateSuccess[_]) => Behaviors.same
        case InternalUpdateResponse(_: UpdateTimeout[_]) => Behaviors.same
        // UpdateTimeout, will eventually be replicated
        case InternalUpdateResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)
      }

      behavior
    }
  }
}
