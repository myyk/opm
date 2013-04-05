package com.gilt.opm

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import java.util.concurrent.TimeUnit
import com.gilt.opm.OpmFactory.PendingOpmValueException
import java.util.NoSuchElementException

/**
 * Document Me.
 *
 * @author Eric Bowman
 * @since 7/9/12 1:58 PM
 */

object OpmTest {

  trait Named extends OpmObject {
    def name: String
    def optName: Option[String] = None
  }

  trait Foo extends Named {
    def id: Long

    def bar: Option[Bar]
  }

  trait SubFoo extends Foo {}

  trait Bar extends Named {
    def things: Set[String]
  }

  trait MixOfOptionalNonOptional extends Named {
    def value: Option[String]
  }

  trait MixOfOptionalNonOptionalWithMethod extends Named {
    def value: Option[String]

    override def equals(obj: Any) = obj match {
      case (o: MixOfOptionalNonOptionalWithMethod) => {
        (this.name == o.name) &&
        (this.value == o.value)
      }
      case _ => false
    }
  }
}

class OpmTest extends FunSuite with ShouldMatchers {

  import OpmTest.{Foo, Bar, SubFoo, MixOfOptionalNonOptional, MixOfOptionalNonOptionalWithMethod, Named}
  import OpmFactory._

  var time = 0l

  test("basics") {
    val foo = instance[Foo]("")
    val foo2 = foo.set(_.name) := "test"
    assert(foo2.name === "test")

    val foo3 = foo2.set(_.bar).to(Some(instance[Bar]("")))
    val foo4 = foo3.set(_.bar.get.name).to("a bar")

    assert(foo4.bar.get.name === "a bar")

    val foo5 = foo4.set(_.bar.get.things) to (Set("a,b,c".split(","): _*))
    assert(foo5.bar.get.things === Set("a", "b", "c"))
  }

  test("set id") {
    var foo = instance[Foo]("")
    foo = foo.set(_.id) := 7L
    assert(foo.id === 7L)
  }

  test("prune") {
    val empty = instance[Foo]("")
    val init = (empty set (_.name) := "init")
    val pruned = init.set(_.name).to("ready").prune
    assert(pruned.timeline.size === 1)
  }

  test("forceCurrent") {
    val a = instance[Foo]("").set(_.name).to("a").prune
    val b = a.set(_.name) := "b"
    val c = b.set(_.name) := "c"
    val bp = b.forceAfter(c)
    assert(bp === b)
    assert(bp.timeline.head === bp)
    assert(bp.timeline.drop(2).take(1).head === b)

    val cp = c :: bp
    assert(cp.timeline.take(3).toList === List(cp, c, bp))
  }

  test("timestamp") {
    val before = OpmFactory.clock()
    val a = instance[Foo]("").set(_.name) := "a"
    val after = OpmFactory.clock()
    assert(a.opmTimestamp >= before && a.opmTimestamp <= after)
  }

  test("branching") {
    val a = instance[Foo]("").set(_.name).to("a").prune
    val b = a.set(_.name) := "b"
    val c = b.set(_.name) := "c"
    val d = c.set(_.name) := "d"
    val e = d.set(_.name) := "e"

    val f = c.set(_.name) := "f"
    val g = f.set(_.name) := "g"

    assert(e.timeline.toList === List(e, d, c, b, a))
    assert(g.timeline.toList === List(g, f, c, b, a))
  }

  test("searching history") {
    var foo = instance[Foo]("").set(_.name).to("a").prune
    foo = foo.set(_.name) := "b"
    val beforeC = clock()
    foo = foo.set(_.name) := "c"
    foo = foo.set(_.name) := "d"

    val b = foo.timeline.dropWhile(_.opmTimestamp >= beforeC)
    assert(b.head === instance[Foo]("").set(_.name).to("b"))
  }

  test("basic diff & evolution") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "barA", "things" -> Set("keg", "glass", "stool")))))
    val b = instance[Foo]("", Map("name" -> "b", "id" -> 1l, "bar" -> instance[Bar]("", Map("name" -> "barB", "things" -> Set("ken", "malibu cruiser")))))

    val fromAtoB = b diff a
    val bp = a evolve fromAtoB
    assert(bp === b)
  }

  test("basic diff & evolution, direct call") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "barA", "things" -> Set("keg", "glass", "stool")))))
    val b = instance[Foo]("", Map("name" -> "b", "id" -> 1l, "bar" -> instance[Bar]("", Map("name" -> "barB", "things" -> Set("ken", "malibu cruiser")))))

    val bp = a evolve b
    assert(bp === b)
  }

  test("diff from builder object, then evolution") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "barA", "things" -> Set("keg", "glass", "stool")))))
    val b = instance[Foo]().set(_.id).to(1l)

    val bp = a evolve b
    assert(bp.id === 1l)
    assert(bp.name === "a")
  }

  test("evolve removing a field") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "bar", "things" -> Set("pub")))))
    val b = instance[Foo]("", Map("name" -> "b", "id" -> 1l))

    val fromAtoB = b diff a
    val bp = a evolve fromAtoB
    assert(bp === b)
  }

  test("evolve adding a field") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l))
    val b = instance[Foo]("", Map("name" -> "b", "id" -> 1l, "bar" -> instance[Bar]("", Map("name" -> "bar", "things" -> Set("pub")))))

    val fromAtoB = b diff a
    val bp = a evolve fromAtoB
    assert(bp === b)
  }

  test("identical evolution") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l))
    val b = a

    val fromAtoB = b diff a
    val bp = a evolve fromAtoB
    assert(bp === b)

  }

  test("empty evolution") {
    val a = instance[Foo]("")
    val b = instance[Foo]("")

    val fromAtoB = b diff a
    val bp = a evolve fromAtoB
    assert(bp === b)
  }

  // test diff across types fails
  test("diff type constraints") {
    evaluating {
      instance[Foo]("") diff
        new Bar {
          def name = null

          def things = Set.empty[String]
        }.asInstanceOf[Foo]
    } should produce[RuntimeException]

    evaluating {
      instance[Foo]("") diff instance[Bar]("")
    } should produce[RuntimeException]
  }

  // test diff across sub-types should not fail
  test("diff sub-type is possible") {
    instance[Foo]("") diff instance[SubFoo]("")
  }

  test("evolve from trait only") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "barA", "things" -> Set("keg", "glass", "stool")))))
    val b = instance[Named]().set(_.name).to("b")

    val bp = a evolve b
    assert(bp.id === 0l)
    assert(bp.name === "b")
  }

  test("required fields present") {
    instance[MixOfOptionalNonOptional]("", Map("name" -> "eric"))
    instance[MixOfOptionalNonOptional]("", Map("name" -> "eric", "value" -> Some("skibum") ))
    evaluating {
      instance[MixOfOptionalNonOptional]("", Map("value" -> Some("skibum") ))
    } should produce[IllegalArgumentException]
  }

  test("required fields ignores methods") {
    instance[MixOfOptionalNonOptionalWithMethod]("", Map("name" -> "eric"))
    instance[MixOfOptionalNonOptionalWithMethod]("", Map("name" -> "eric", "value" -> Some("skibum") ))
    evaluating {
      instance[MixOfOptionalNonOptionalWithMethod]("", Map("value" -> Some("skibum") ))
    } should produce[IllegalArgumentException]
  }

  test("nested option"){
    val foo = instance[Foo]("fake").set(_.bar).to(None)
    assert(foo.bar === None)
    val foo2 = instance[Foo]("fake").set(_.bar).to(Option(null))
    assert(foo2.bar === None)
  }

  test("set pending") {
    val a = instance[Foo]().
      set(_.id).to(1L).
      set(_.name).toPending(500, TimeUnit.MILLISECONDS)
    val caught = evaluating {
      a.name
    } should produce[RuntimeException]
    assert(caught match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    assert(a.isPending(_.name))
  }

  test("remove pending") {
    val a = instance[Foo]().
      set(_.id).to(1L).
      set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    assert(a.isPending(_.name))
    val b = a.set(_.name).toNotPending()
    assert(!b.isPending(_.name))
  }

  test("remove pending on set field does nothing") {
    val a = instance[Foo]().
      set(_.id).to(1L).
      set(_.name).to("name1")
    assert(!a.isPending(_.name))
    val b = a.set(_.name).toNotPending()
    assert(!b.isPending(_.name))
  }

  test("set pending when previous value is None") {
    val a = instance[Foo]().
      set(_.id).to(1L).
      set(_.optName).to(None).
      set(_.optName).toPending(500, TimeUnit.MILLISECONDS)
    val caught = evaluating {
      a.optName
    } should produce[RuntimeException]
    assert(caught match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    assert(a.isPending(_.optName))
  }

  test("pending should expire") {
    val a = instance[Foo]().
      set(_.id).to(1L).
      set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    Thread.sleep(101)
    val caught = evaluating {
      a.name
    } should produce[RuntimeException]
    assert(caught match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
    assert(!a.isPending(_.name))
  }

  test("pending should clear when value is set") {
    val a = instance[Foo]().
      set(_.id).to(1L).
      set(_.name).toPending(1000, TimeUnit.MILLISECONDS)
    val caught = evaluating {
      a.name
    } should produce[RuntimeException]
    assert(caught match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    val b = a.set(_.name).to("name1")
    assert(b.name === "name1")
    assert(!b.isPending(_.name))
  }

  test("don't set pending on field that has already been set to a value") {
    val a = instance[Foo]().
      set(_.name).to("a")
    val b = a.set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    assert(b.name === "a")
    assert(a.opmTimestamp == b.opmTimestamp)
    assert(!b.isPending(_.name))
  }

  test("don't set pending on field that has already been set to pending") {
    val a = instance[Foo]().
      set(_.name).toPending(500, TimeUnit.MILLISECONDS)
    Thread.sleep(10)
    val b = a.set(_.name).toPending(10, TimeUnit.SECONDS)
    assert(a.opmTimestamp == b.opmTimestamp)
    val caught1 = evaluating {
      b.name
    } should produce[RuntimeException]
    assert(caught1 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    Thread.sleep(500)
    val caught2 = evaluating {
      b.name
    } should produce[RuntimeException]
    assert(caught2 match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
  }

  test("set pending on field on which previous pending has expired") {
    val a = instance[Foo]().
      set(_.name).toPending(500, TimeUnit.MILLISECONDS)
    Thread.sleep(10)
    val b = a.set(_.name).toPending(10, TimeUnit.SECONDS)
    assert(a.opmTimestamp == b.opmTimestamp)
    val caught1 = evaluating {
      b.name
    } should produce[RuntimeException]
    assert(caught1 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    Thread.sleep(500)
    val caught2 = evaluating {
      b.name
    } should produce[RuntimeException]
    assert(caught2 match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
    val c = a.set(_.name).toPending(500, TimeUnit.MILLISECONDS)
    assert(a.opmTimestamp != c.opmTimestamp)
    val caught3 = evaluating {
      c.name
    } should produce[RuntimeException]
    assert(caught3 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    val d = b.set(_.name).toPending(500, TimeUnit.MILLISECONDS)
    assert(b.opmTimestamp != d.opmTimestamp)
    val caught4 = evaluating {
      d.name
    } should produce[RuntimeException]
    assert(caught4 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
  }

  test("don't set pending on field that has already been set to pending then set a value") {
    val a = instance[Foo]().
      set(_.name).toPending(1000, TimeUnit.MILLISECONDS)
    val caught = evaluating {
      a.name
    } should produce[RuntimeException]
    assert(caught match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    val b = a.set(_.name).to("name1")
    assert(b.name === "name1")
    val c = b.set(_.name).toPending(1000, TimeUnit.MILLISECONDS)
    assert(c == b)
    assert(c.opmTimestamp == b.opmTimestamp)
  }

  test("evolve from pending trait") {
    val a = instance[Foo]().set(_.id).to(0l)
    val b = instance[Named]().set(_.name).toPending(100, TimeUnit.MILLISECONDS)

    val bp = a evolve b
    assert(bp.id === 0l)

    val caught1 = evaluating {
      bp.name
    } should produce[RuntimeException]
    assert(caught1 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    assert(bp.isPending(_.name))
    Thread.sleep(100)
    val caught2 = evaluating {
      bp.name
    } should produce[RuntimeException]
    assert(caught2 match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
    assert(!bp.isPending(_.name))
  }

  test("evolve from pending optional trait set to None") {
    val a = instance[Foo]().set(_.id).to(0l).set(_.optName).to(None)
    val b = instance[Named]().set(_.optName).toPending(100, TimeUnit.MILLISECONDS)

    val bp = a evolve b
    assert(bp.id === 0l)

    val caught1 = evaluating {
      bp.optName
    } should produce[RuntimeException]
    assert(caught1 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    Thread.sleep(100)
    val caught2 = evaluating {
      bp.optName
    } should produce[RuntimeException]
    assert(caught2 match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
  }

  test("evolve from pending sets value when set") {
    val a = instance[Foo]().set(_.id).to(0l).set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    val b = instance[Named]().set(_.name).to("name1")

    val caught = evaluating {
      a.name
    } should produce[RuntimeException]
    assert(caught match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    val bp = a evolve b
    assert(bp.id === 0l)
    assert(bp.name === "name1")
    assert(bp.opmTimestamp != a.opmTimestamp)
    assert(!bp.isPending(_.name))
  }

  test("evolve from pending trait does nothing when already set") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "barA", "things" -> Set("keg", "glass", "stool")))))
    val b = instance[Named]().set(_.name).toPending(100, TimeUnit.MILLISECONDS)

    val bp = a evolve b
    assert(bp.id === 0l)
    assert(bp.name === "a")
    assert(bp.opmTimestamp == a.opmTimestamp)
    assert(!bp.isPending(_.name))
  }

  test("evolve to not pending") {
    val a = instance[Foo]().set(_.id).to(0l).set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    val b = instance[Named]().set(_.name).toNotPending()

    val bp = a evolve b
    assert(bp.id === 0l)
    assert(bp.opmTimestamp != a.opmTimestamp)
    assert(!bp.isPending(_.name))
  }

  test("evolve to not pending does nothing when already set") {
    val a = instance[Foo]("", Map("name" -> "a", "id" -> 0l, "bar" -> instance[Bar]("", Map("name" -> "barA", "things" -> Set("keg", "glass", "stool")))))
    val b = instance[Named]().set(_.name).toNotPending()

    val bp = a evolve b
    assert(bp.id === 0l)
    assert(bp.name === "a")
    assert(bp.opmTimestamp == a.opmTimestamp)
    assert(!bp.isPending(_.name))
  }

  test("evolve from pending trait does nothing when already pending") {
    val a = instance[Foo]().set(_.id).to(0l).set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    val b = instance[Named]().set(_.name).toPending(10, TimeUnit.SECONDS)

    val bp = a evolve b
    assert(bp.id === 0l)
    assert(bp.opmTimestamp == a.opmTimestamp)
    val caught1 = evaluating {
      bp.name
    } should produce[RuntimeException]
    assert(caught1 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    Thread.sleep(100)
    val caught2 = evaluating {
      bp.name
    } should produce[RuntimeException]
    assert(caught2 match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
  }

  test("evolve from pending trait sets pending when previous pending has expired") {
    val a = instance[Foo]().set(_.id).to(0l).set(_.name).toPending(100, TimeUnit.MILLISECONDS)
    val b = instance[Named]().set(_.name).toPending(10, TimeUnit.SECONDS)

    val c = a evolve b
    assert(c.id === 0l)
    assert(c.opmTimestamp == a.opmTimestamp)
    val caught1 = evaluating {
      c.name
    } should produce[RuntimeException]
    assert(caught1 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    Thread.sleep(100)
    val caught2 = evaluating {
      c.name
    } should produce[RuntimeException]
    assert(caught2 match {
      case PendingOpmValueException(message) => false
      case e: NoSuchElementException => true
    })
    val d = a evolve b
    assert(d.opmTimestamp != a.opmTimestamp)
    val caught3 = evaluating {
      d.name
    } should produce[RuntimeException]
    assert(caught3 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
    val e = c evolve b
    assert(e.opmTimestamp != c.opmTimestamp)
    val caught4 = evaluating {
      e.name
    } should produce[RuntimeException]
    assert(caught4 match {
      case PendingOpmValueException(message) => true
      case e: NoSuchElementException => false
    })
  }
}
