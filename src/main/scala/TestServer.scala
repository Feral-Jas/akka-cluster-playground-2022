import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.google.inject.Guice
import io.gdmexchange.common.util.{AppSettings, Loggable}
import kamon.Kamon
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.{ClusterScheduler, DistributedConfig, UniversalModule}

import scala.util.control.NonFatal

/** @author Chenyu.Liu
  */
object TestServer extends Loggable {
  def main(args: Array[String]): Unit = {
    val appSettings = AppSettings(args)
    val name = appSettings.appName
    val config = appSettings.config
    Kamon.init()
    implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { ctx =>
        val injector = Guice.createInjector(UniversalModule(config, ctx))
        val dConfigActor =
          injector.instance[ActorRef[DistributedConfig.Command]]
        val cScheduler = injector.instance[ActorRef[ClusterScheduler.Task]]
        Behaviors.same
      },
      name
    )

    try {
      init
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(implicit system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
  }
}
