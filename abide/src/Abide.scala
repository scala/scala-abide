package scala.tools.abide

import scala.tools.nsc._
import scala.reflect.runtime.{universe => ru}

object Abide {
  def apply(global : Global, className : String = "scala.tools.abide.DefaultAnalyzer") : Analyzer = {
    val mirror = ru.runtimeMirror(getClass.getClassLoader)

    val classSymbol = mirror.staticClass(className)
    val classMirror = mirror.reflectClass(classSymbol)

    val constructorSymbol = classSymbol.typeSignature.member(ru.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = classMirror.reflectConstructor(constructorSymbol)

    constructorMirror(global).asInstanceOf[Analyzer]
  }
}
