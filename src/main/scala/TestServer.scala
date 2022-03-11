import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import io.gdmexchange.common.util.{AppSettings, Loggable}

import scala.util.control.NonFatal

/**
 * @author Chenyu.Liu
 */
object TestServer extends Loggable{
  def main(args: Array[String]): Unit = {
    val name = AppSettings(args).appName
    val system = ActorSystem[Nothing](Behaviors.empty, name)

    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
  }
}
