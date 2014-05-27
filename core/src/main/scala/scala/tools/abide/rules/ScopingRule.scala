package scala.tools.abide
package rules

import traversal._

trait ScopingRule extends TraversalRule {
  import traverser._
  import global._

  type RuleState = ScopingState

  def emptyState = new ScopingState(Nil, Nil)

  case class ScopedWarning(rule: Rule, pos: Position) extends Warning {
    override def toString : String = pos.toString
  }

  class ScopingState(symbols : List[Symbol], _warnings : List[Warning]) extends State {
    def warnings = _warnings
    def warn(tree : Tree) : ScopingState =
      new ScopingState(symbols, _warnings :+ ScopedWarning(ScopingRule.this, tree.pos))

    def enter(symbol : Symbol) : ScopingState = new ScopingState(symbol :: symbols, _warnings)
    def leave : ScopingState = new ScopingState(symbols.tail, _warnings)

    def in(symbol : Symbol) : Boolean = symbols.nonEmpty && symbol == symbols.head
  }

  def enter(symbol : Symbol) : TraversalStep[Tree, ScopingState] = new TraversalStep[Tree, ScopingState] {
    val enter = (state : ScopingState) => state enter symbol
    val leave = Some((state : ScopingState) => state.leave)
  }

  def warn(tree : Tree) : TraversalStep[Tree, ScopingState] = new SimpleStep[Tree, ScopingState] {
    val enter = (state : ScopingState) => state warn tree
  }

}
