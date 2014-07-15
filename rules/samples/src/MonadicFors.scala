package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class MonadicFors(val context : Context) extends WarningRule {
  import context.universe._

  val name = "monadic-fors"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = "Monad doesn't depend on last binding, should use Applicative (see http://www.haskell.org/haskellwiki/Typeclassopedia)"
  }

  def check(enums : List[Tree]) {
    val (syms, values) = enums.flatMap {
      case fq"$bind <- $rhs" => Some(bind.symbol -> rhs)
      case fq"$bind = $rhs" => Some(bind.symbol -> rhs)
      case fq"if $cond" => None
    }.unzip

    for ((rhs, bound) <- values.tail zip syms.init if !rhs.exists(_.symbol == bound)) {
      nok(Warning(rhs))
    }
  }

  val step = optimize {
    case t @ q"for (..$enums) yield $body" => check(enums)
    case t @ q"for (..$enums) $body"       => check(enums)
  }
}

