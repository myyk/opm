package com.gilt.opm.query

import com.mongodb.casbah.commons.{MongoDBList, MongoDBObject}
import com.giltgroupe.service.commons.mongo.MongoHelper._

/**
 * Case class representing the logic to filter a property that is included the given array.
 *
 * @param valueTranslator: @see com.gilt.opm.query.OpmSearcher
 */
case class OpmPropertyIn(property: String, value: Iterable[Any], valueTranslator: Option[(String, Any) => Any] = None) extends OpmPropertyQuery {
  override def isMatch(obj: Any) = value.exists(_ == obj)
  override def toMongoDBObject(prefix: String = "") = MongoDBObject("%s%s".format(prefix, property) -> MongoDBObject("$in" -> MongoDBList(value.map(toMongo(_, translate(property))).toSeq: _*)))
}
