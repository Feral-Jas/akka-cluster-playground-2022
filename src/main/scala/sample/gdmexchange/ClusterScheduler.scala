package sample.gdmexchange

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import sample.gdmexchange.DistributedConfig.ConfigItem

object ClusterScheduler {
  sealed trait Task
  case object ReloadConfigFromDBTask extends Task
  case object ReloadVaultTask extends Task

  def apply(distributedConfig: ActorRef[DistributedConfig.Command]) = {
    Behaviors.receiveMessage[Task] {
      case ReloadConfigFromDBTask =>
        //load from  db
        distributedConfig ! DistributedConfig.AddConfig(
          ConfigItem("key1", Some("value1"))
        )
        distributedConfig ! DistributedConfig.AddConfig(
          ConfigItem("key2", Some("value2"))
        )
        Behaviors.same
      case ReloadVaultTask =>
        //load from vault
        distributedConfig ! DistributedConfig.AddConfig(
          ConfigItem("key3", None, Some(3))
        )
        distributedConfig ! DistributedConfig.AddConfig(
          ConfigItem("key4", None, Some(4))
        )
        Behaviors.same
    }

  }

}
