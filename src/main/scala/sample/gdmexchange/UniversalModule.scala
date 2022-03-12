package sample.gdmexchange

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.util.Timeout
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.Config
import io.gdmexchange.common.util.{Loggable, Settings}
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import net.codingwell.scalaguice.ScalaModule
import sample.gdmexchange.datamodel.{DataItemBase, TypedDataItem}

import scala.concurrent.ExecutionContext

case class UniversalModule(settings: Settings, actorContext: ActorContext[_])
    extends AbstractModule
    with ScalaModule
    with Loggable {
  override def configure(): Unit = {
    bind[ActorContext[_]].toInstance(actorContext)
    bind[Settings].toInstance(settings)
  }

  @Provides
  @Singleton
  def config: Config = settings.config

  @Provides
  @Singleton
  @Named("env")
  def env: String = settings.env

  @Provides
  @Singleton
  @Named("instance")
  def instance: String = settings.instance

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
object UniversalModule {
  trait GlobalImplicits {
    implicit val injector: ScalaInjector
    implicit val settings: Settings = injector.instance[Settings]
    implicit val env: String = settings.env
    implicit val instance: String = settings.instance
    implicit val config: Config = injector.instance[Config]
    implicit val timeout: Timeout =
      Timeout.create(config.getDuration("global-implicits.ask-timeout"))
    implicit val typedSystem: ActorSystem[_] = injector.instance[ActorSystem[_]]
    implicit val executionContext: ExecutionContext =
      injector.instance[ExecutionContext]
  }
  sealed trait ActorCollection {}
}
