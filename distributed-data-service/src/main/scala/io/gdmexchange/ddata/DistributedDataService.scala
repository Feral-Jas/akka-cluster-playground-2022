package io.gdmexchange.ddata
import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.google.inject.{Guice, Inject}
import io.gdmexchange.ddata.module.UniversalModule
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.Loggable

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** @author Chenyu.Liu
 */
object DistributedDataService extends Loggable {
  def main(args: Array[String]): Unit = {
    //    if you are using Kamon
    //    Kamon.init()
    implicit val system: ActorSystem[Done] = ActorSystem(
      Behaviors.setup[Done] { ctx =>
        implicit val injector: ScalaInjector =
          Guice.createInjector(UniversalModule(ctx))
        val server                           = injector.instance[DistributedDataService]
        server.start(System.getenv("HTTP_PORT").toInt)
        Behaviors.same
      },
      "ddata"
    )
    try init
    catch {
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
class DistributedDataService @Inject()(implicit val injector: ScalaInjector)
  extends Loggable
    with UniversalModule.GlobalImplicits {
  private val apiDependencyWiring = new ApiDependencyWiring
  def start(port: Int): Unit      =
    Http()
      .newServerAt("0.0.0.0", port)
      .bind(apiDependencyWiring.externalApis)
      .onComplete {
        case Failure(exception) =>
          throw exception
        case Success(binding)   =>
          logger.info(
            " Serving in " + binding.localAddress.toString
          )
      }
}

