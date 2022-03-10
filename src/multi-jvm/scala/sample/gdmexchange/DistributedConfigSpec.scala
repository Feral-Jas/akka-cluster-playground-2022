package sample.gdmexchange

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
import sample.distributeddata.STMultiNodeSpec
import sample.gdmexchange.DistributedConfigSpec.{clusterNode1, clusterNode2, clusterNode3}

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
      "sample.gdmexchange.DistributedConfig$ConfigItem" = jackson-cbor
    }
    """))

}

class ReplicatedCacheSpecMultiJvmNode1 extends DistributedConfigSpec
class ReplicatedCacheSpecMultiJvmNode2 extends DistributedConfigSpec
class ReplicatedCacheSpecMultiJvmNode3 extends DistributedConfigSpec

class DistributedConfigSpec
    extends MultiNodeSpec(DistributedConfigSpec) with STMultiNodeSpec {
  override def initialParticipants: Int = roles.size
  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  val cluster = Cluster(typedSystem)
  private val distributedConfig: ActorRef[DistributedConfig.Command] =
    system.spawnAnonymous(DistributedConfig("ascendex"))
  private val clusterScheduler: ActorRef[Nothing] =
    system.spawnAnonymous(ClusterScheduler(distributedConfig))

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
}
