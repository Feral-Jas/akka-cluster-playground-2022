package io.gdmexchange.webserverx

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.gdmexchange.webserverx.module.UniversalModule
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.datamodel.DataItemBase
import sample.gdmexchange.{ClusterScheduler, DistributedDataActor}

/** @author Chenyu.Liu
 */
class ApiDependencyWiring(implicit val injector: ScalaInjector) extends UniversalModule.GlobalImplicits {
  private val distributedDataActor =
    injector.instance[ActorRef[DistributedDataActor.Command[DataItemBase]]]
  injector.instance[ActorRef[ClusterScheduler.Task]]
  val externalApis: Route          = path("data") {
    val dataSetFut =
      distributedDataActor.ask(DistributedDataActor.GetAllData[DataItemBase])
    onSuccess(dataSetFut) { dataSet =>
      complete(dataSet.items.toString())
    } ~ (path("remove") & delete) {
      parameter('name.as[String]) { dataName =>
        distributedDataActor ! DistributedDataActor.RemoveData[DataItemBase](
          dataName
        )
        complete(s"key:$dataName removed in async")
      }
    } ~ (path("clear") & delete) {
      val dataSetFut =
        distributedDataActor.ask(DistributedDataActor.GetAllData[DataItemBase])
      onSuccess(dataSetFut) { dataSet =>
        dataSet.items.foreach { dataItem =>
          distributedDataActor ! DistributedDataActor.RemoveData[DataItemBase](
            dataItem.dataName
          )
        }
        complete("All distributed data cleared")
      }
    }
  }
}

