package sample.gdmexchange

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import com.colofabrix.scala.figlet4s.unsafe.{FIGureOps, Figlet4s, OptionsBuilderOps}
import io.gdmexchange.common.util.Loggable
import sample.CborSerializable
import sample.gdmexchange.DistributedConfig.ConfigItem

import scala.concurrent.duration.DurationInt

object ClusterScheduler extends Loggable {
  sealed trait Task
  case object ReloadConfigFromDBTask extends Task with CborSerializable
  case object ReloadVaultTask extends Task with CborSerializable
  case object SimpleLoggerTask extends Task with CborSerializable
  def apply(distributedConfig: ActorRef[DistributedConfig.Command]) = {
    Behaviors.withTimers[ClusterScheduler.Task] { timer =>
//      timer.startTimerWithFixedDelay(ReloadVaultTask, 5.seconds)
//      timer.startTimerWithFixedDelay(ReloadConfigFromDBTask, 5.seconds)
      timer.startTimerWithFixedDelay(SimpleLoggerTask, 5.seconds)
      Behaviors.receiveMessage[Task] {
        case ReloadConfigFromDBTask =>
          //load from  db
          logger.info("<<<< Reload config from database")
          doReloadConfigFromDb(distributedConfig)
          Behaviors.same
        case ReloadVaultTask =>
          //load from vault
          logger.info("<<<< Reload config from vaultMgr")
          doReloadFromVault(distributedConfig)
          Behaviors.same
        case SimpleLoggerTask =>
          Figlet4s
            .builder("Hello")
            .render()
            .asSeq()
            .zipWithIndex
            .foreach { case (line, i) =>
              logger.info(line)
            }
          Behaviors.same
      }
    }
  }
  def doReloadConfigFromDb(
      distributedConfig: ActorRef[DistributedConfig.Command]
  ) = {
    distributedConfig ! DistributedConfig.AddConfig(
      ConfigItem("key1", Some("value1"))
    )
    distributedConfig ! DistributedConfig.AddConfig(
      ConfigItem("key2", Some("value2"))
    )
  }

  def doReloadFromVault(
      distributedConfig: ActorRef[DistributedConfig.Command]
  ) = {
    distributedConfig ! DistributedConfig.AddConfig(
      ConfigItem("key3", None, Some(3))
    )
    distributedConfig ! DistributedConfig.AddConfig(
      ConfigItem("key4", None, Some(4))
    )
  }
}
