package sample.distributeddata

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.cluster.ddata.Replicator
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{GetReplicaCount, ReplicaCount}
import akka.cluster.typed.{Cluster, ClusterSingleton, Join, SingletonActor}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.typesafe.config.ConfigFactory
import sample.gdmexchange.DistributedConfig.{ConfigItem, ConfigSet}
import sample.distributeddata.DistributedConfigSpec.{clusterNode1, clusterNode2, clusterNode3}
import sample.gdmexchange.{ClusterScheduler, DistributedConfig}

import scala.concurrent.duration.DurationInt

object DistributedConfigSpec extends MultiNodeConfig {
  val clusterNode1 = role("node-1")
  val clusterNode2 = role("node-2")
  val clusterNode3 = role("node-3")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    akka.actor.serialization-bindings {
      "sample.CborSerializable" = jackson-cbor
    }
    akka.actor.allow-java-serialization = on
    """))

}

class DistributedConfigSpecMultiJvmNode1 extends DistributedConfigSpec
class DistributedConfigSpecMultiJvmNode2 extends DistributedConfigSpec
class DistributedConfigSpecMultiJvmNode3 extends DistributedConfigSpec

class DistributedConfigSpec
  extends MultiNodeSpec(DistributedConfigSpec) with STMultiNodeSpec {
  override def initialParticipants: Int = roles.size
  implicit val typedSystem: ActorSystem[_] = system.toTyped
  val cluster = Cluster(typedSystem)
  private val distributedConfig: ActorRef[DistributedConfig.Command] =
    system.spawnAnonymous(DistributedConfig("ascendex"))
  val singletonManager = ClusterSingleton(typedSystem)
  private val clusterScheduler: ActorRef[ClusterScheduler.Task] =
   singletonManager.init(
      SingletonActor(Behaviors.supervise(ClusterScheduler(distributedConfig)).onFailure[Exception](SupervisorStrategy.restart), "ClusterScheduler"))

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
  "Test ReloadDBTask" in within(10.seconds) {
    runOn(clusterNode2) {
      clusterScheduler ! ClusterScheduler.ReloadConfigFromDBTask
    }
    enterBarrier("updates-done")

    awaitAssert {
      val probe = TestProbe[DistributedConfig.ConfigSet]()
      distributedConfig ! DistributedConfig.GetAllConfig(probe.ref)
      val configSet = probe.expectMessageType[ConfigSet]
      val KEY_1 = configSet.items.find(_.configName == "key1")
      val KEY_2 = configSet.items.find(_.configName == "key2")
      KEY_1 shouldBe a[Some[ConfigItem]]
      KEY_1.get.stringValueOpt shouldEqual Some("value1")
      KEY_2 shouldBe a[Some[ConfigItem]]
      KEY_2.get.stringValueOpt shouldEqual Some("value2")
    }

    enterBarrier("after-2")
  }
  "Test ReloadVaultTask" in within(10.seconds) {
    runOn(clusterNode2) {
      clusterScheduler ! ClusterScheduler.ReloadVaultTask
    }
    enterBarrier("updates-done")

    awaitAssert {
      val probe = TestProbe[DistributedConfig.ConfigSet]()
      distributedConfig ! DistributedConfig.GetAllConfig(probe.ref)
      val configSet = probe.expectMessageType[ConfigSet]
      val KEY_3 = configSet.items.find(_.configName == "key3")
      val KEY_4 = configSet.items.find(_.configName == "key4")
      KEY_3 shouldBe a[Some[ConfigItem]]
      KEY_3.get.decimalValueOpt shouldEqual Some(3)
      KEY_4 shouldBe a[Some[ConfigItem]]
      KEY_4.get.decimalValueOpt shouldEqual Some(4)
    }

    enterBarrier("after-3")
  }
}
