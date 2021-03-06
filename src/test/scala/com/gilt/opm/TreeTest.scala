package com.gilt.opm

import org.scalatest.Matchers
import org.scalatest.FunSuite


object TreeModule {
  trait Node extends OpmObject {
    def children: Seq[Node]
  }
}

/**
 * Document Me.
 *
 * @author Eric Bowman
 * @since 8/20/12 8:36 AM
 */
class TreeTest extends FunSuite with Matchers {

  import TreeModule._
  import OpmFactory._

  test("constructing a tree should work as expected") {

    val tree = instance[Node]("").set(_.children).pruneTo {
      for (child <- Seq(instance[Node](""), instance[Node](""), instance[Node](""))) yield {
        child.set(_.children) := Seq(instance[Node](""), instance[Node](""), instance[Node](""))
      }
    }
    tree.toString should equal(
      "com.gilt.opm.TreeModule$Node(opmKey=," +
        "children=List(com.gilt.opm.TreeModule$Node(opmKey=," +
        "children=List(com.gilt.opm.TreeModule$Node(opmKey=,), " +
        "com.gilt.opm.TreeModule$Node(opmKey=,), " +
        "com.gilt.opm.TreeModule$Node(opmKey=,))), " +
        "com.gilt.opm.TreeModule$Node(opmKey=," +
        "children=List(com.gilt.opm.TreeModule$Node(opmKey=,), " +
        "com.gilt.opm.TreeModule$Node(opmKey=,), " +
        "com.gilt.opm.TreeModule$Node(opmKey=,))), " +
        "com.gilt.opm.TreeModule$Node(opmKey=," +
        "children=List(" +
        "com.gilt.opm.TreeModule$Node(opmKey=,), " +
        "com.gilt.opm.TreeModule$Node(opmKey=,), " +
        "com.gilt.opm.TreeModule$Node(opmKey=,)))))")
  }
}
