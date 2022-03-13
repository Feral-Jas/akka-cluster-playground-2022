package io.gdmexchange.ddata.module

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.util.Timeout
import com.google.inject.{AbstractModule, Provides, Singleton}
import io.gdmexchange.ddata.actor.ClusterScheduler
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import net.codingwell.scalaguice.ScalaModule
import sample.Loggable
import sample.gdmexchange.datamodel.{DataItemBase, TypedDataItem}
import sample.gdmexchange.DistributedDataActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** @author Chenyu.Liu
  */
case class UniversalModule(actorContext: ActorContext[_]) extends AbstractModule with ScalaModule with Loggable {
  override def configure(): Unit =
    bind[ActorContext[_]].toInstance(actorContext)

  @Provides
  @Singleton
  def actoySys: ActorSystem[_] = actorContext.system

  @Provides
  @Singleton
  def ec: ExecutionContext = actorContext.executionContext

  @Provides
  @Singleton
  def distributedDataActor(appName:String): ActorRef[DistributedDataActor.Command[DataItemBase]] = {
    val actorRef =
      actorContext.spawn(
        DistributedDataActor.apply[TypedDataItem](appName),
        "DistributedDataActor"
      )
    logger.info("++++++++++|Actor::DistributedDataActor spawned")
    actorRef
  }

  @Provides
  @Singleton
  def clusterScheduler(
      distributedConfig: ActorRef[DistributedDataActor.Command[DataItemBase]]
  ): ActorRef[ClusterScheduler.Task] = {
    implicit val system: ActorSystem[_]             = actoySys
    implicit val executionContext: ExecutionContext = ec
    val singletonManager                            = ClusterSingleton(actorContext.system)
    val actorRef: ActorRef[ClusterScheduler.Task]   =
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
  trait GlobalImplicits        {
    implicit val injector: ScalaInjector
    implicit val timeout: Timeout                   = 5.seconds
    implicit val typedSystem: ActorSystem[_]        = injector.instance[ActorSystem[_]]
    implicit val executionContext: ExecutionContext =
      injector.instance[ExecutionContext]
  }
  sealed trait ActorCollection {}
}
