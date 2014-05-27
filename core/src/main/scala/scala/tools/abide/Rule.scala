package scala.tools.abide

trait Rule {
  val name : String
}

trait State {
  def warnings : List[Warning]
}

trait Warning
