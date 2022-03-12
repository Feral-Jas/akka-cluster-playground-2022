import akka.Done
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{onSuccess, path}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.google.inject.{Guice, Inject}
import com.typesafe.config.Config
import io.circe.syntax.EncoderOps
import io.gdmexchange.common.util.{AppSettings, Loggable}
import io.gdmexchange.webservercommon.route.BaseRoute
import kamon.Kamon
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.datamodel.DataItemBase
import sample.gdmexchange.{
  ClusterScheduler,
  DistributedDataActor,
  UniversalModule
}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

/** @author Chenyu.Liu
  */
object TestServer extends Loggable {
  def main(args: Array[String]): Unit = {
    val appSettings = AppSettings(args)
    val name = appSettings.appName
    val config = appSettings.config
    Kamon.init()
    implicit val system: ActorSystem[Done] = ActorSystem(
      Behaviors.setup[Done] { ctx =>
        implicit val injector: ScalaInjector =
          Guice.createInjector(UniversalModule(config, ctx))
        implicit val ec = ctx.executionContext
        val dConfigActor =
          injector
            .instance[ActorRef[DistributedDataActor.Command[DataItemBase]]]
        val cScheduler = injector.instance[ActorRef[ClusterScheduler.Task]]
        val server = injector.instance[TestServer]
        server.start.onComplete {
          case Failure(exception) =>
            Behaviors.stopped
          case Success(value) =>
        }
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
class TestServer @Inject() (config: Config, injector: ScalaInjector)
    extends BaseRoute {
  implicit val system: ActorSystem[_] = injector.instance[ActorSystem[_]]
  implicit val ec: ExecutionContext = injector.instance[ExecutionContext]
  private val distributedDataActor =
    injector.instance[ActorRef[DistributedDataActor.Command[DataItemBase]]]
  implicit val timeout: Timeout = 10.seconds
  override val env: String = "staging"
  override val instance: String = "1"
  def httpRoute = path("data") {
    val dataSetFut =
      distributedDataActor.ask(DistributedDataActor.GetAllData[DataItemBase])
    onSuccess(dataSetFut) { dataSet =>
      successWithDataString(dataSet.items.map(_.toString).asJson.noSpaces)
    }
  }
  def start = Http()
    .newServerAt("127.0.0.1", config.getInt("my-service.http-port"))
    .bind(httpRoute)

}
