package com.gilt.opm

import com.gilt.gfc.logging.Loggable
import com.mongodb.casbah.MongoClient
import lock.LockManager

/**
 * Can mix into tests to provide a mongo database & collection to test against so that OpmMongoStorage is happy.
 *
 * @author Eric Bowman
 * @since 1/8/13 8:31 AM
 */
object CollectionHelper {
  val databaseName = "opm_mongo_tests"
}

trait CollectionHelper {
  self: LockManager with Loggable =>
  def collectionName: String
  override def waitMs = 2000L   // jenkins can be slow

  lazy val collection = {
    val col = MongoClient()(CollectionHelper.databaseName)(collectionName)
    col.drop()
    info("Dropped %s, size = %s".format(col, col.size))
    col
  }
  lazy val locks = {
    val col = MongoClient()(CollectionHelper.databaseName)(collectionName + "_locks")
    col.drop()
    info("Dropped %s, size = %s".format(col, col.size))
    col
  }
}
