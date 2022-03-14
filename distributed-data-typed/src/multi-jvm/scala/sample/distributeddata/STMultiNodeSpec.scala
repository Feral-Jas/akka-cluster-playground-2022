package sample.distributeddata

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

/** Hooks up MultiNodeSpec with ScalaTest
  */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()
}
