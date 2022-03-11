package sample.gdmexchange

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.Config
import io.gdmexchange.common.util.Loggable
import net.codingwell.scalaguice.ScalaModule

case class UniversalModule(config: Config, actorContext: ActorContext[_])
    extends AbstractModule
    with ScalaModule
    with Loggable {
  override def configure(): Unit = {
    bind[ActorContext[_]].toInstance(actorContext)
    bind[Config].toInstance(config)
  }

  @Provides
  @Singleton
  def distributedConfig: ActorRef[DistributedConfig.Command] = {
    val actorRef =
      actorContext.spawn(DistributedConfig("fp-api-server"), "ddata")
    logger.info("++++++++++|Actor::DistributedConfig spawned")
    actorRef
  }

  @Provides
  @Singleton
  def clusterScheduler(
      distributedConfig: ActorRef[DistributedConfig.Command]
  ): ActorRef[ClusterScheduler.Task] = {
    val singletonManager = ClusterSingleton(actorContext.system)
    val actorRef: ActorRef[ClusterScheduler.Task] =
      singletonManager.init(
        SingletonActor(Behaviors.supervise(ClusterScheduler(distributedConfig)).onFailure[Exception](SupervisorStrategy.restart), "ClusterScheduler"))
    logger.info("++++++++++|Actor::ClusterScheduler spawned")
    actorRef
  }
}
