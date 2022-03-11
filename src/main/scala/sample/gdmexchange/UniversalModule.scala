package sample.gdmexchange

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.Config
import io.gdmexchange.common.util.{Loggable, UuidUtil}
import net.codingwell.scalaguice.ScalaModule

case class UniversalModule(config:Config,actorContext: ActorContext[_]) extends AbstractModule with ScalaModule with Loggable{
  override def configure(): Unit = {
    bind[ActorContext[_]].toInstance(actorContext)
    bind[Config].toInstance(config)
  }


  @Provides
  @Singleton
  def distributedConfig:ActorRef[DistributedConfig.Command] = {
    val actorName = "ddata-" + UuidUtil.newId(4)
    val actorRef = actorContext.spawn(DistributedConfig("fp-api-server"), actorName)
    logger.info("SPAWNING DISTRIBUED CONFIG ========== "+actorName)
    actorRef
  }

  @Provides
  @Singleton
  def clusterScheduler:ActorRef[ClusterScheduler.Task] = actorContext.spawn(ClusterScheduler(distributedConfig),"cluster-scheduler")
}
