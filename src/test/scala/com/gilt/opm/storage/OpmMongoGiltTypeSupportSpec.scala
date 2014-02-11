package com.gilt.opm.storage

import com.gilt.opm.OpmFactory._
import com.gilt.opm.{OpmMongoStorage, CollectionHelper, OpmObject}
import com.giltgroupe.util.Timestamp
import com.giltgroupe.typed.Guid
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

object OpmMongoGiltTypeSupportSpec {
  trait GiltTypes extends OpmObject {
    def typedGuid: Guid[GiltTypes]
    def seqTypedGuid: Seq[Guid[GiltTypes]]
    def timestamp: Timestamp
    def optTimestamp: Option[Timestamp]
  }
}
import OpmMongoGiltTypeSupportSpec._

class OpmMongoGiltTypeSupportSpec
extends FlatSpec with ShouldMatchers with OpmMongoStorage[GiltTypes] with OpmMongoGiltTypeSupport with CollectionHelper {
  val collectionName = "gilt_types"

  "OpmMongoGiltTypeSupport" should "allow extra gilt types to be stored and loaded" in {
    val gt = instance[GiltTypes]("key")
      .set(_.typedGuid).to(Guid.randomGuid[GiltTypes])
      .set(_.seqTypedGuid).to(Seq(Guid.randomGuid[GiltTypes]))
      .set(_.timestamp).to(new Timestamp())
      .set(_.optTimestamp).to(Some(new Timestamp()))
    put(gt)
    val loaded = get("key")
    assert(loaded.isDefined)
    assert(gt === loaded.get)
  }
}
