package sample.gdmexchange

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.Config
import io.gdmexchange.common.util.Loggable
import net.codingwell.scalaguice.ScalaModule
import sample.gdmexchange.datamodel.{DataItemBase, TypedDataItem}

import scala.concurrent.ExecutionContext

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
  def actoySys: ActorSystem[_] = actorContext.system

  @Provides
  @Singleton
  def ec: ExecutionContext = actorContext.executionContext

  @Provides
  @Singleton
  def distributedConfig
      : ActorRef[DistributedDataActor.Command[DataItemBase]] = {
    val actorRef =
      actorContext.spawn(
        DistributedDataActor.apply[TypedDataItem]("fp-api-server"),
        "ddata"
      )
    logger.info("++++++++++|Actor::DistributedConfig spawned")
    actorRef
  }

  @Provides
  @Singleton
  def clusterScheduler(
      distributedConfig: ActorRef[DistributedDataActor.Command[DataItemBase]]
  ): ActorRef[ClusterScheduler.Task] = {
    implicit val system = actoySys
    implicit val executionContext = ec
    val singletonManager = ClusterSingleton(actorContext.system)
    val actorRef: ActorRef[ClusterScheduler.Task] =
      singletonManager.init(
        SingletonActor(
          Behaviors
            .supervise(ClusterScheduler(distributedConfig))
            .onFailure[Exception](SupervisorStrategy.restart),
          "ClusterScheduler"
        )
      )
    logger.info("++++++++++|Actor::ClusterScheduler spawned")
    actorRef
  }
}
