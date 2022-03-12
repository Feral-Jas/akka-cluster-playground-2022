import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import io.circe.syntax.EncoderOps
import io.gdmexchange.webservercommon.route.BaseRoute
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.DistributedDataActor
import sample.gdmexchange.datamodel.DataItemBase

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/**
 * @author Chenyu.Liu
 */
class ApiDependencyWiring()(implicit injector:ScalaInjector)extends BaseRoute{
  implicit val system: ActorSystem[_] = injector.instance[ActorSystem[_]]
  implicit val ec: ExecutionContext = injector.instance[ExecutionContext]
  private val distributedDataActor= injector.instance[ActorRef[DistributedDataActor.Command[DataItemBase]]]
  implicit val timeout:Timeout = 10.seconds
  val externalApis = path("data"){
    val dataSetFut = distributedDataActor.ask(DistributedDataActor.GetAllData[DataItemBase])
    onSuccess(dataSetFut){
      dataSet => successWithDataString(dataSet.items.map(_.toString).asJson.noSpaces)
    }
  }
  override val env: String = "staging"
  override val instance: String = "1"
}
