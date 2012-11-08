package com.gilt.opm.query

import com.mongodb.casbah.commons.MongoDBObject
import com.giltgroupe.service.commons.mongo.MongoHelper.toMongo

/**
 * Case class representing the logic to filter a property that is greater than or equal to the given value.
 *
 * @author: Ryan Martin
 * @since: 11/6/12 1:24 PM
 */
case class OpmPropertyGreaterThanOrEqual[T <% Ordered[T]](property: String, value: T) extends OpmPropertyQuery {
  override def isMatch(obj: Any): Boolean = obj.asInstanceOf[T] >= value
  override def toMongoDBObject(prefix: String = "") = MongoDBObject("%s%s".format(prefix, property) -> MongoDBObject("$gte" -> toMongo(value)))
}