package sample.gdmexchange

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.colofabrix.scala.figlet4s.unsafe.{FIGureOps, Figlet4s, OptionsBuilderOps}
import sample.gdmexchange.datamodel.{DataItemBase, TypedDataItem}
import sample.{CborSerializable, Loggable}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Random, Success}

object ClusterScheduler extends Loggable {
  sealed trait Task
  case object ReloadConfigFromDBTask extends Task with CborSerializable
  case object ReloadVaultTask        extends Task with CborSerializable
  case object SimpleLoggerTask       extends Task with CborSerializable
  //should be remove because AskPattern is not recommended inside an actor
  implicit private val timeout: Timeout = 5.seconds
  def apply(
      distributedDataActor: ActorRef[DistributedDataActor.Command[DataItemBase]]
  )(implicit
      actorSystem: ActorSystem[_],
      ec: ExecutionContext
  ): Behavior[Task]                     =
    Behaviors.withTimers[ClusterScheduler.Task] { timer =>
      timer.startTimerWithFixedDelay(SimpleLoggerTask, 5.seconds)
      timer.startTimerWithFixedDelay(ReloadConfigFromDBTask, 10.seconds)
      Behaviors.receiveMessage[Task] {
        case ReloadConfigFromDBTask =>
          //load from db
          logger.info("<<<< Reload config from database")
          doReloadConfigFromDb(distributedDataActor)
          //remove ask later
          distributedDataActor
            .ask(
              DistributedDataActor.GetAllData[DataItemBase]
            )
            .onComplete {
              case Failure(exception) =>
                logger.error(exception.getMessage)
                throw exception
              case Success(dataSet)   =>
                logger.info("===============DATA SUMMARY===============")
                logger.info(s"total size = ${dataSet.items.size}")
                logger.info(
                  s"youngest record = ${dataSet.items.toList.minBy(_.createdAt.getSecond).toString}"
                )
                logger.info(
                  s"oldest record = ${dataSet.items.toList.maxBy(_.createdAt.getSecond).toString}"
                )
                logger.info("==========================================")
            }
          Behaviors.same
        case SimpleLoggerTask       =>
          Figlet4s
            .builder("BEEP")
            .render()
            .asSeq()
            .zipWithIndex
            .foreach { case (line, i) =>
              logger.info(line)
            }
          Behaviors.same
        case _                      =>
          throw new Exception("unknown message type")
          Behaviors.same
      }
    }
  def doReloadConfigFromDb(
      distributedConfig: ActorRef[DistributedDataActor.Command[DataItemBase]]
  ) = {
    distributedConfig ! DistributedDataActor.AddData(
      TypedDataItem(
        "key" + Random.nextInt(100),
        `type` = TypedDataItem.CONFIG,
        stringValueOpt = Some("value" + Random.nextInt(100))
      )
    )
    distributedConfig ! DistributedDataActor.AddData(
      TypedDataItem(
        "key" + Random.nextInt(100),
        `type` = TypedDataItem.CONFIG,
        stringValueOpt = Some("value" + Random.nextInt(100))
      )
    )
  }

}
