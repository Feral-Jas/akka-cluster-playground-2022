import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.google.inject.{Guice, Inject}
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.Loggable
import sample.gdmexchange.UniversalModule

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** @author Chenyu.Liu
  */
object TestServer extends Loggable {
  def main(args: Array[String]): Unit = {
//    if you are using Kamon
//    Kamon.init()
    implicit val system: ActorSystem[Done] = ActorSystem(
      Behaviors.setup[Done] { ctx =>
        implicit val injector: ScalaInjector =
          Guice.createInjector(UniversalModule(ctx))
        val server = injector.instance[TestServer]
        server.start(args.last.toInt)
        Behaviors.same
      },
      "fp-api-server"
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
class TestServer @Inject() (implicit val injector: ScalaInjector)
    extends Loggable
    with UniversalModule.GlobalImplicits {
  private val apiDependencyWiring = new ApiDependencyWiring
  def start(port: Int): Unit = {
    Http()
      .newServerAt("0.0.0.0", port)
      .bind(apiDependencyWiring.externalApis)
      .onComplete {
        case Failure(exception) =>
          throw exception
        case Success(binding) =>
          logger.info(
            " Serving in " + binding.localAddress.toString
          )
      }
  }
}
