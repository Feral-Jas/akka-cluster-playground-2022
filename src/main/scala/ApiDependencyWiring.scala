import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.circe.syntax.EncoderOps
import io.gdmexchange.webservercommon.route.BaseRoute
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.datamodel.DataItemBase
import sample.gdmexchange.{ClusterScheduler, DistributedDataActor, UniversalModule}

/** @author Chenyu.Liu
  */
class ApiDependencyWiring(implicit val injector: ScalaInjector)
    extends BaseRoute
    with UniversalModule.GlobalImplicits {
  private val distributedDataActor =
    injector.instance[ActorRef[DistributedDataActor.Command[DataItemBase]]]
  private val clusterScheduler =
    injector.instance[ActorRef[ClusterScheduler.Task]]
  val externalApis: Route = path("data") {
    val dataSetFut =
      distributedDataActor.ask(DistributedDataActor.GetAllData[DataItemBase])
    onSuccess(dataSetFut) { dataSet =>
      successWithDataString(dataSet.items.map(_.toString).asJson.noSpaces)
    } ~ (path("remove") & delete) {
      parameter('name.as[String]) { dataName =>
        distributedDataActor ! DistributedDataActor.RemoveData[DataItemBase](
          dataName
        )
        successWithDataString(s"key:$dataName removed in async")
      }
    } ~ (path("clear") & delete) {
      val dataSetFut =
        distributedDataActor.ask(DistributedDataActor.GetAllData[DataItemBase])
      onSuccess(dataSetFut) { dataSet =>
        dataSet.items.foreach(dataItem => {
          distributedDataActor ! DistributedDataActor.RemoveData[DataItemBase](
            dataItem.dataName
          )
        })
        successWithDataString("All distributed data cleared")
      }
    }
  }
}
