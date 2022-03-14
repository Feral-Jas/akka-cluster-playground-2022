package sample

import org.slf4j.{Logger, LoggerFactory}

/** @author Chenyu.Liu
  */
trait Loggable {
  val logger: Logger = LoggerFactory.getLogger(this.getClass.getSimpleName)
}
