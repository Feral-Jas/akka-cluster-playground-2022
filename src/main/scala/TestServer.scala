import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.google.inject.{Guice, Inject}
import io.gdmexchange.common.util.{AppSettings, Loggable}
import io.gdmexchange.webservercommon.route.BaseRoute
import kamon.Kamon
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.UniversalModule

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** @author Chenyu.Liu
  */
object TestServer extends Loggable {
  def main(args: Array[String]): Unit = {
    val appSettings = AppSettings(args)
    Kamon.init()
    implicit val system: ActorSystem[Done] = ActorSystem(
      Behaviors.setup[Done] { ctx =>
        implicit val injector: ScalaInjector =
          Guice.createInjector(UniversalModule(appSettings, ctx))
        implicit val ec = ctx.executionContext
        val server = injector.instance[TestServer]
        server.start()
        Behaviors.same
      },
      appSettings.appName
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
    extends BaseRoute with Loggable
    with UniversalModule.GlobalImplicits {
  private val apiDependencyWiring = new ApiDependencyWiring
  def start():Unit = {
    Http()
      .newServerAt("0.0.0.0", config.getInt("global-implicits.http-port"))
      .bind(apiDependencyWiring.externalApis).onComplete {
      case Failure(exception) =>
        throw exception
      case Success(binding) =>
        logger.info(
          settings.appName + " Serving in " + binding.localAddress.toString
        )
    }
  }
}
