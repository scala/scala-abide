package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class PackageObjectClasses(val context: Context) extends WarningRule with SimpleWarnings {
  import context.universe._

  val warning = w"""It is not recommended to define classes inside of package objects.
                   |If possible, define ${tree.symbol} in ${tree.symbol.owner.owner} instead.""".stripMargin

  def isPackageObjectMember(sym: Symbol) =
    sym.owner.isModuleClass && sym.owner.name == tpnme.PACKAGE && !sym.isSynthetic

  val step = optimize {
    case cd @ ClassDef(_, _, _, _) if isPackageObjectMember(cd.symbol) =>
      nok(cd)

    case md @ ModuleDef(_, _, _) if isPackageObjectMember(md.symbol) =>
      nok(md)
  }
}
