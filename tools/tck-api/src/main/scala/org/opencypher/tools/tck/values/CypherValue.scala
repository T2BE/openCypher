/*
 * Copyright (c) 2015-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Attribution Notice under the terms of the Apache License 2.0
 *
 * This work was created by the collective efforts of the openCypher community.
 * Without limiting the terms of Section 6, any Derivative Work that is not
 * approved by the public consensus process of the openCypher Implementers Group
 * should not be described as “Cypher” (and Cypher® is a registered trademark of
 * Neo4j Inc.) or as "openCypher". Extensions by implementers or prototypes or
 * proposals for change that have been documented or implemented should only be
 * described as "implementation extensions to Cypher" or as "proposed changes to
 * Cypher that are not yet approved by the openCypher community".
 */
package org.opencypher.tools.tck.values

import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}
import org.opencypher.tools.tck.parsing.generated.{FeatureResultsLexer, FeatureResultsParser}

import scala.util.hashing.MurmurHash3

object CypherValue {

  def apply(s: String, orderedLists: Boolean = true): CypherValue = {
    val stream = CharStreams.fromString(s)
    val tokenStream = new FeatureResultsLexer(stream)
    val tokens = new CommonTokenStream(tokenStream)
    val parser = new FeatureResultsParser(tokens)
    val featureResultsContext = parser.value
    val visitor = CypherValueVisitor(orderedLists)
    val value = visitor.visit(featureResultsContext)
    value
  }

  implicit val ordering: Ordering[CypherValue] = new Ordering[CypherValue] {
    override def compare(x: CypherValue, y: CypherValue): Int = {
      val stringOrdering = implicitly[Ordering[String]]
      stringOrdering.compare(x.toString, y.toString)
    }
  }

}

sealed trait CypherValue

case class CypherNode(labels: Set[String] = Set.empty, properties: CypherPropertyMap = CypherPropertyMap())
  extends CypherValue {

  override def toString: String = {
    val lbls = if (labels.isEmpty) "" else labels.mkString(":", ":", "")
    val props = if (properties.properties.isEmpty) "" else properties.toString
    Seq(lbls, props).filter(_.nonEmpty).mkString("(", " ", ")")
  }
}

case class CypherRelationship(relType: String, properties: CypherPropertyMap = CypherPropertyMap()) extends CypherValue {

  override def toString: String = s"[:$relType $properties]"
}

case class CypherString(s: String) extends CypherValue {
  override def toString: String = s"'$s'"
}

case class CypherInteger(value: Long) extends CypherValue {
  override def toString: String = s"$value"
}

case class CypherFloat(value: Double) extends CypherValue {
  override def toString: String = s"$value"
}

case class CypherBoolean(value: Boolean) extends CypherValue {
  override def toString: String = s"$value"
}

case class CypherProperty(key: String, value: CypherValue) extends CypherValue {
  override def toString: String = s"$key: $value"
}

case class CypherPropertyMap(properties: Map[String, CypherValue] = Map.empty)
  extends CypherValue {
  override def toString: String = s"{${properties.map {
    case (k, v) => s"$k: $v"
  }.mkString(", ")}}"
}

trait CypherList extends CypherValue {
  def elements: List[CypherValue]
}

case class CypherOrderedList(elements: List[CypherValue] = List.empty) extends CypherList {
  override def toString: String = s"[${elements.mkString(", ")}]"

  override def equals(obj: scala.Any): Boolean = obj match {
    case null => false
    case other: CypherOrderedList => elements == other.elements
    case o: CypherUnorderedList => o == this
    case _ => false
  }

  override def hashCode(): Int = MurmurHash3.productHash(this)
}

/**
  * Enables comparisons between lists without enforcing order of elements.
  * Requires the input list to be sorted by the CypherValue default ordering.
  */
private[tck] case class CypherUnorderedList(elements: List[CypherValue] = List.empty) extends CypherList {

  override def toString: String = s"[${elements.mkString(", ")}]"

  override def equals(obj: scala.Any): Boolean = obj match {
    case null => false
    case other: CypherOrderedList =>
      other.elements.sorted(CypherValue.ordering) == elements
    case other: CypherUnorderedList =>
      other.elements == elements
    case _ => false
  }

  override def hashCode(): Int = MurmurHash3.productHash(this)
}

case object CypherNull extends CypherValue {
  override def toString: String = "null"
}

case class CypherPath(startingNode: CypherNode, connections: List[Connection] = List.empty) extends CypherValue {
  override def toString: String = s"<$startingNode${connections.mkString("")}>"
}

trait Connection {
  def n: CypherNode
  def r: CypherRelationship
}

case class Forward(r: CypherRelationship, n: CypherNode) extends Connection {
  override def toString: String = s"-$r->$n"
}

case class Backward(r: CypherRelationship, n: CypherNode) extends Connection {
  override def toString: String = s"<-$r-$n"
}
