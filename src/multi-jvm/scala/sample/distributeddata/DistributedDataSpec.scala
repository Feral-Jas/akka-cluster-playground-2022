package sample.distributeddata

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.ddata.Replicator
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{GetReplicaCount, ReplicaCount}
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper}
import sample.distributeddata.DistributedDataSpec.{clusterNode1, clusterNode2, clusterNode3}
import sample.gdmexchange.DistributedDataActor
import sample.gdmexchange.datamodel.{DataItemBase, TypedDataItem}

import scala.concurrent.duration.DurationInt

object DistributedDataSpec extends MultiNodeConfig {
  val clusterNode1 = role("node-1")
  val clusterNode2 = role("node-2")
  val clusterNode3 = role("node-3")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    akka.actor.serialization-bindings {
      "sample.CborSerializable" = jackson-cbor
      "sample.gdmexchange.datamodel.TypedDataItem" = jackson-cbor
    }
    """))

}

class DistributedDataSpecMultiJvmNode1 extends DistributedDataSpec
class DistributedDataSpecMultiJvmNode2 extends DistributedDataSpec
class DistributedDataSpecMultiJvmNode3 extends DistributedDataSpec

class DistributedDataSpec
  extends MultiNodeSpec(DistributedDataSpec) with STMultiNodeSpec {
  override def initialParticipants: Int = roles.size
  implicit val typedSystem: ActorSystem[_] = system.toTyped
  implicit val excutionContext = typedSystem.executionContext
  val cluster = Cluster(typedSystem)
  private val distributedDataActor: ActorRef[DistributedDataActor.Command[DataItemBase]] =
    system.spawnAnonymous(DistributedDataActor("ascendex"))
//  val singletonManager = ClusterSingleton(typedSystem)
//  private val clusterScheduler: ActorRef[ClusterScheduler.Task] =
//   singletonManager.init(
//      SingletonActor(Behaviors.supervise(ClusterScheduler(distributedDataActor)).onFailure[Exception](SupervisorStrategy.restart), "ClusterScheduler"))

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }

  "Demo of a replicated shopping cart" must {
    "join cluster" in within(20.seconds) {
      join(clusterNode1, clusterNode1)
      join(clusterNode2, clusterNode1)
      join(clusterNode3, clusterNode1)

      awaitAssert {
        val probe = TestProbe[ReplicaCount]()
        DistributedData(typedSystem).replicator ! GetReplicaCount(probe.ref)
        probe.expectMessage(Replicator.ReplicaCount(roles.size))
      }
      enterBarrier("after-1")
    }
  }
  "Test AddData" in within(5.seconds) {
    runOn(clusterNode2) {
      distributedDataActor ! DistributedDataActor.AddData(TypedDataItem("key1"))
      distributedDataActor ! DistributedDataActor.AddData(TypedDataItem("key2"))
    }
    enterBarrier("updates-done")

    awaitAssert {
      val probe = TestProbe[DistributedDataActor.DataSet[DataItemBase]]()
      distributedDataActor ! DistributedDataActor.GetAllData(probe.ref)
      val configSet = probe.expectMessageType[DistributedDataActor.DataSet[DataItemBase]]
      val KEY_1 = configSet.items.find(_.dataName == "key1")
      val KEY_2 = configSet.items.find(_.dataName == "key2")
      KEY_1 shouldBe a[Some[_]]
      KEY_1.get shouldBe a[TypedDataItem]
      KEY_2 shouldBe a[Some[_]]
      KEY_2.get shouldBe a[TypedDataItem]
    }

    enterBarrier("after-2")
  }
}
