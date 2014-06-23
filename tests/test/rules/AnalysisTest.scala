package scala.tools.abide
package rules

abstract class AnalysisTest extends AbideTest {
  val analyzer = new DefaultAnalyzer(global).asInstanceOf[Analyzer { val global : AnalysisTest.this.global.type }]
}
