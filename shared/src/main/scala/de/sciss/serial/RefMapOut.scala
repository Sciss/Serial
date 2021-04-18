/*
 *  RefMapOut.scala
 *  (Serial)
 *
 * Copyright (c) 2011-2021 Hanns Holger Rutz. All rights reserved.
 *
 * This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.serial

import java.util.{IdentityHashMap => JIdentityHashMap}
import scala.collection.{Seq => CSeq, Map => CMap, Set => CSet, Iterable => CIterable}

/** Building block for serializing `Product` based type hierarchies.
  *
  * By default, the following type tags are supported:
  *
  * - 'B' Boolean
  * - 'D' Double
  * - 'F' Float
  * - 'I' Int
  * - 'L' Long
  * - 'M' Map[_, _]
  * - 'O' Option[_]
  * - 'P' Product
  * - '#' Product with type identifier
  * - 'S' String
  * - 'T' Set[_]
  * - 'X' Seq[_]
  * - 'U' Unit `()`
  * - '<' reference to previous object (as written by `RefMapOut`)
  * - '\u0000' null
  *
  * Sub-classes may want to patch into `writeCustomElem` and `writeElem` or `writeProduct`
  * to handle specific new types. They may want to override `isDefaultPackage` to
  * allow storing product prefixes instead of full class-names when unique identification is possible.
  *
  * @param  out0  the binary output to write to
  */
class RefMapOut(out0: DataOutput) {
  // we use an identity hash map, because we do _not_
  // want to alias objects in the serialization; the input
  // is an in-memory object graph.
  private val ref = new JIdentityHashMap[Product, Integer]

  final def out: DataOutput = out0

  protected def isDefaultPackage(pck: String): Boolean = false

  /** Override this to return a value different than `-1` to benefit
    *  from more efficient product serialization.
    */
  protected def productTypeId(name: String): Int = -1

  def writeProduct(p: Product): Unit = {
    val id0Ref = ref.get(p)
    if (id0Ref != null) {
      out.writeByte('<')
      out.writeInt(id0Ref)
      return
    }

    // `getPackage` not supported by Scala.js:
    // val pck     = p.getClass.getPackage.getName
    // Java 9+:
    // val pck     = p.getClass.getPackageName
    val cn    = p.getClass.getName
    val pck   = {
      val i = cn.lastIndexOf('.')
      if (i != -1) cn.substring(0, i) else ""
    }
    val prefix  = p.productPrefix
    val name    = if (isDefaultPackage(pck)) prefix else s"$pck.$prefix"
    val typeId  = productTypeId(name)
    if (typeId == -1) {
      out.writeByte('P')
      out.writeUTF(name)
    } else {
      out.writeByte('#')
      out.writeInt(typeId)
    }
    out.writeShort(p.productArity)
    writeIdentifiedProduct(p)

    val id = ref.size() // count
    ref.put(p, id)
    ()
  }

  protected def writeIdentifiedProduct(p: Product): Unit =
    p.productIterator.foreach(writeElem)

  final def writeVec[A](xs: CSeq[A], elem: A => Unit): Unit = {
    out.writeByte('X')
    out.writeInt(xs.size)
    xs.foreach(elem)
  }

  final def writeMap[K, V](m: CMap[K, V], key: K => Unit, value: V => Unit): Unit = {
    out.writeByte('M')
    out.writeInt(m.size)
    m.foreach { tup =>
      key   (tup._1)
      value (tup._2)
    }
  }

  final def writeSet[A](t: CSet[A], elem: A => Unit): Unit = {
    out.writeByte('T')
    out.writeInt(t.size)
    t.foreach(elem)
  }

  final def writeOption[A](x: Option[A], elem: A => Unit): Unit = {
    out.writeByte('O')
    val b = x.isDefined
    out.writeBoolean(b)
    if (b) elem(x.get)
  }

  final def writeInt(i: Int): Unit = {
    out.writeByte('I')
    out.writeInt(i)
  }

  final def writeLong(n: Long): Unit = {
    out.writeByte('L')
    out.writeLong(n)
  }

  final def writeString(s: String): Unit = {
    out.writeByte('S')
    out.writeUTF(s)
  }

  final def writeBoolean(b: Boolean): Unit = {
    out.writeByte('B')
    out.writeBoolean(b)
  }

  final def writeFloat(f: Float): Unit = {
    out.writeByte('F')
    out.writeFloat(f)
  }

  final def writeDouble(d: Double): Unit = {
    out.writeByte('D')
    out.writeDouble(d)
  }

  def writeElem(e: Any): Unit =
    e match {
      case o: Option[_] =>  // 'O'
        writeOption(o, writeElem)
      case xs: CIterable[_] =>  // first step to avoid unnecessary tests in the main pat-mat
        xs match {
          case sq: CSeq[_] =>    // 'X'. either indexed seq or var arg (e.g. wrapped array)
            writeVec(sq, writeElem)
          case m: CMap[_, _] => // 'M'
            writeMap(m, writeElem, writeElem)
          case t: CSet[_] =>    // 'T'
            writeSet(t, writeElem)
          case _ => throw new Exception(s"Unsupported collection $xs")
        }
      case p: Product =>
        writeProduct(p)     // 'P' or '<'
      case i: Int =>        // 'I'
        writeInt(i)
      case n: Long =>       // 'L'
        writeLong(n)
      case s: String =>     // 'S'
        writeString(s)
      case b: Boolean =>    // 'B'
        writeBoolean(b)
      case f: Float =>      // 'F'
        writeFloat(f)
      case d: Double =>     // 'D'
        writeDouble(d)
      case _: Unit =>
        out.writeByte('U')
      case null =>
        out.writeByte('\u0000')
      case _  => writeCustomElem(e)
    }

  protected def writeCustomElem(e: Any): Any =
    sys.error(s"Unexpected element to serialize: $e")
}
