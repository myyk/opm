package com.gilt.opm.query

import com.mongodb.casbah.commons.MongoDBObject
import com.giltgroupe.service.commons.mongo.MongoHelper._

/**
 * Case class representing the logic to filter an array property that is contains the given value.
 *
 * @param valueTranslator: @see com.gilt.opm.query.OpmSearcher
 */
case class OpmPropertyContains(property: String, value: Any, valueTranslator: Option[(String, Any) => Any] = None) extends OpmPropertyQuery {
  override def isMatch(obj: Any): Boolean = obj.asInstanceOf[Iterable[Any]].find(_ == value).isDefined
  override def toMongoDBObject(prefix: String = "") = MongoDBObject("%s%s".format(prefix, property) -> toMongo(value, translate(property)))
}
