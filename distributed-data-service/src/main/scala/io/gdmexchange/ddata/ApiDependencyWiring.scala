package io.gdmexchange.ddata

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.gdmexchange.ddata.actor.ClusterScheduler
import io.gdmexchange.ddata.module.UniversalModule
import net.codingwell.scalaguice.InjectorExtensions.ScalaInjector
import sample.gdmexchange.DistributedDataActor
import sample.gdmexchange.DistributedDataActor.DataSet
import sample.gdmexchange.datamodel.{DataItemBase, TypedDataItem}

import java.util.UUID
import scala.util.Random

/** @author Chenyu.Liu
  */
class ApiDependencyWiring(implicit val injector: ScalaInjector) extends UniversalModule.GlobalImplicits {
  private val distributedDataActor                   =
    injector.instance[ActorRef[DistributedDataActor.Command[DataItemBase]]]
  private val value: ActorRef[ClusterScheduler.Task] = injector.instance[ActorRef[ClusterScheduler.Task]]
  val externalApis: Route                            = pathPrefix("data") {
    get {
      val dataSetFut =
        distributedDataActor.ask[DataSet[DataItemBase]](DistributedDataActor.GetAllData[DataItemBase](_))
      onSuccess(dataSetFut) { dataSet =>
        complete(dataSet.items.toString())
      }
    } ~ post {
      distributedDataActor ! DistributedDataActor.AddData(
        TypedDataItem(
          "key" + Random.nextInt(100),
          TypedDataItem.CACHE,
          stringValueOpt = Some(UUID.randomUUID().toString),
          Some(Random.nextInt(100))
        )
      )
      complete("added")
    } ~ (path("remove") & delete) {
      parameter('name.as[String]) { dataName =>
        distributedDataActor ! DistributedDataActor.RemoveData[DataItemBase](
          dataName
        )
        complete(s"key:$dataName removed in async")
      }
    } ~ (path("clear") & delete) {
      val dataSetFut =
        distributedDataActor.ask[DataSet[DataItemBase]](DistributedDataActor.GetAllData[DataItemBase](_))
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
