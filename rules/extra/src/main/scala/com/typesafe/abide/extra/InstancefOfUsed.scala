package com.typesafe.abide.extra

import scala.tools.abide._
import scala.tools.abide.traversal._

class InstancefOfUsed(val context: Context) extends WarningRule with SimpleWarnings {
  import context.universe._

  val warning = w"Use of ${tree.asInstanceOf[Select].name} is discouraged, consider using pattern matching instead"

  private val AsInstanceOf = TermName("asInstanceOf")
  private val IsInstanceOf = TermName("isInstanceOf")

  val step = optimize {
    case appl @ Select(_, AsInstanceOf) =>
      nok(appl)

    case appl @ Select(_, IsInstanceOf) =>
      nok(appl)
  }

}
