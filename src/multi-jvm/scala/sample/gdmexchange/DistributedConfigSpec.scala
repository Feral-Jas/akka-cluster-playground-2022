package sample.gdmexchange

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.typesafe.config.ConfigFactory
import sample.distributeddata.{STMultiNodeSpec, ShoppingCartSpec}

object DistributedConfigSpec extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    akka.actor.serialization-bindings {
      "sample.gdmexchange.DistributedConfig$ConfigItem" = jackson-cbor
    }
    """))

}

//class ReplicatedCacheSpecMultiJvmNode1 extends DistributedConfigSpec
//class ReplicatedCacheSpecMultiJvmNode2 extends DistributedConfigSpec
//class ReplicatedCacheSpecMultiJvmNode3 extends DistributedConfigSpec

class DistributedConfigSpec
    extends MultiNodeSpec(ShoppingCartSpec) with STMultiNodeSpec {
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
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      awaitAssert {
        val probe = TestProbe[ReplicaCount]()
        DistributedData(typedSystem).replicator ! GetReplicaCount(probe.ref)
        probe.expectMessage(Replicator.ReplicaCount(roles.size))
      }
      enterBarrier("after-1")
    }
}
