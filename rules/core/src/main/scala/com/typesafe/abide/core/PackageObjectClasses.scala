package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class PackageObjectClasses(val context: Context) extends WarningRule {
  import context.universe._

  val name = "package-object-classes"

  case class Warning(val pos: Position, symbol: Symbol) extends RuleWarning {
    val message =
      s"""It is not recommended to define classes inside of package objects.
         |If possible, define ${symbol} in ${symbol.owner.owner} instead.""".stripMargin
  }

  def isPackageObjectMember(sym: Symbol) =
    sym.owner.isModuleClass && sym.owner.name == tpnme.PACKAGE && !sym.isSynthetic

  val step = optimize {
    case cd @ ClassDef(_, _, _, _) if isPackageObjectMember(cd.symbol) =>
      nok(Warning(cd.pos, cd.symbol))

    case md @ ModuleDef(_, _, _) if isPackageObjectMember(md.symbol) =>
      nok(Warning(md.pos, md.symbol))
  }
}
