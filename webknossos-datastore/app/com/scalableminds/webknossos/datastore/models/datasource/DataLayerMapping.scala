package com.scalableminds.webknossos.datastore.models.datasource

import play.api.libs.json._

/*
Note: This case class is not (de)serialized to/from JSON using the build-in JSON library
      but instead uses the dedicated MappingParser class for performance reasons.
      Whenever this data class is changed, the parser needs to be modified accordingly.
*/

case class DataLayerMapping(name: String, mapping: Map[Long, Long])
