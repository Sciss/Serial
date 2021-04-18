/*
 *  RefMapIn.scala
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

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.collection.mutable

/** Building block for deserializing `Product` based type hierarchies.
  *
  * By default, the following type tags are supported:
  *
  * - 'B' Boolean
  * - 'C' Const (freely definable type -- must implement `readIdentifiedConst`)
  * - 'D' Double
  * - 'F' Float
  * - 'I' Int
  * - 'L' Long
  * - 'M' Map[_, _]
  * - 'O' Option[_]
  * - 'P' Product (through registered `ProductReader` instances)
  * - '#' Product with type identifier
  * - 'S' String
  * - 'T' Set[_]
  * - 'X' Vec[_] (IndexedSeq)
  * - 'U' Unit `()`
  * - 'R' (freely definable type -- must implement `readIdentifiedR`)
  * - 'Y' (freely definable type -- must implement `readIdentifiedY`)
  * - 'u' (freely definable type -- must implement `readIdentifiedU`)
  * - 'E' (freely definable type -- must implement `readIdentifiedE`)
  * - '<' reference to previous object (as written by `RefMapOut`)
  * - '\u0000' null
  *
  * Sub-classes may want to patch into `readCustomElem` and `readCustomProduct`
  * to handle specific new types.
  *
  * @param  in0   the binary input to read from
  */
abstract class RefMapIn[Repr](in0: DataInput) {
  self: Repr =>

  // ---- abstract ----

  type Const <: Product
  type R
  type U
  type Y
  type E

  protected def readProductWithKey(key: String, arity: Int): Product

  protected def readProductWithId(typeId: Int): Product =
    throw new NotImplementedError()

  // ---- impl ----

  private[this] val map   = mutable.Map.empty[Int, Product]
  private[this] var count = 0

  final def in: DataInput = in0

  def readElem(): Any = {
    val cookie = in.readByte().toChar
    readElemWithCookie(cookie)
  }

  protected def readElemWithCookie(cookie: Char): Any =
    (cookie: @switch) match {
      case 'O' => if (in.readBoolean()) Some(readElem()) else None
      case 'X' => readIdentifiedVec(readElem())
      case 'M' => readIdentifiedMap(readElem(), readElem())
      case 'T' => readIdentifiedSet(readElem())
      case '#' | 'P' | '<' | 'C' => readProductWithCookie(cookie)
      case 'R' => readIdentifiedR()
      case 'I' => in.readInt()
      case 'L' => in.readLong()
      case 'S' => in.readUTF()
      case 'B' => in.readBoolean()
      case 'F' => in.readFloat()
      case 'D' => in.readDouble()
      case 'U' => ()
      case 'Y' => readIdentifiedY()
      case 'E' => readIdentifiedE()
      case 'u' => readIdentifiedU()
      case '\u0000' => null
      case _  => readCustomElem(cookie)
    }

  final protected def unexpectedCookie(cookie: Char): Nothing =
    sys.error(s"Unexpected cookie '$cookie'")

  final protected def unexpectedCookie(cookie: Char, expected: Char): Nothing =
    sys.error(s"Unexpected cookie '$cookie' is not '$expected'")

  protected def readCustomElem(cookie: Char): Any =
    unexpectedCookie(cookie)

  /** Like `readProduct` but casts the result (unsafe) */
  final def readProductT[A <: Product](): A =
    readProduct().asInstanceOf[A]

  def readProduct(): Product = {
    val cookie = in0.readByte().toChar
    readProductWithCookie(cookie)
  }

  protected def readProductWithCookie(cookie: Char): Product =
    (cookie: @switch) match {
      case 'C' => readIdentifiedConst()

      case '#' =>
        val typeId  = in0.readInt()
        val res     = readProductWithId(typeId)
        val id      = count
        map    += ((id, res))
        count   = id + 1
        res

      case 'P' =>
        val prefix0 = in0.readUTF()
        val nm      = prefix0.length - 1
        // we store prefixes now always without trailing `$` character, even for case objects
        val prefix  = if (prefix0.charAt(nm) == '$') prefix0.substring(0, nm) else prefix0
        val arity   = in.readShort().toInt
        val res     = readProductWithKey(prefix, arity)
        val id      = count
        map    += ((id, res))
        count   = id + 1
        res

      case '<' =>
        val id = in0.readInt()
        map(id)

      case _ =>
        readCustomProduct(cookie)
    }

  protected def readCustomProduct(cookie: Char): Product =
    unexpectedCookie(cookie)

  protected def readIdentifiedConst (): Const  = throw new NotImplementedError()

  protected def readIdentifiedU(): U  = throw new NotImplementedError()
  protected def readIdentifiedR(): R  = throw new NotImplementedError()
  protected def readIdentifiedY(): Y  = throw new NotImplementedError()
  protected def readIdentifiedE(): E  = throw new NotImplementedError()

  final def readVec[A](elem: => A): Vec[A] = {
    val cookie = in0.readByte().toChar
    if (cookie != 'X') unexpectedCookie(cookie, 'X')
    readIdentifiedVec(elem)
  }

  private def readIdentifiedVec[A](elem: => A): Vec[A] = {
    val size = in.readInt()
    Vector.fill(size)(elem)
  }

  final def readSet[A](elem: => A): Set[A] = {
    val cookie = in0.readByte().toChar
    if (cookie != 'T') unexpectedCookie(cookie, 'T')
    readIdentifiedSet(elem)
  }

  private def readIdentifiedSet[A](elem: => A): Set[A] = {
    val size = in.readInt()
    val b = Set.newBuilder[A] // Set does not have `fill` in Scala 2.12
    b.sizeHint(size)
    var i = 0
    while (i < size) {
      b += elem
      i += 1
    }
    b.result()
  }

  final def readMap[K, V](key: => K, value: => V): Map[K, V] = {
    val cookie = in0.readByte().toChar
    if (cookie != 'M') unexpectedCookie(cookie, 'M')
    readIdentifiedMap(key, value)
  }

  private def readIdentifiedMap[K, V](key: => K, value: => V): Map[K, V] = {
    val num = in.readInt()
    val b   = Map.newBuilder[K, V]
    b.sizeHint(num)
    var rem = num
    while (rem > 0) {
      val k = key
      val v = value
      b += k -> v
      rem -= 1
    }
    b.result()
  }

  final def readInt(): Int = {
    val cookie = in0.readByte().toChar
    if (cookie != 'I') unexpectedCookie(cookie, 'I')
    in0.readInt()
  }

  final def readIntVec(): Vec[Int] = readVec(readInt())

  final def readLong(): Long = {
    val cookie = in0.readByte().toChar
    if (cookie != 'L') unexpectedCookie(cookie, 'L')
    in0.readLong()
  }

  final def readBoolean(): Boolean = {
    val cookie = in0.readByte().toChar
    if (cookie != 'B') unexpectedCookie(cookie, 'B')
    in0.readBoolean()
  }

  final def readString(): String = {
    val cookie = in0.readByte().toChar
    if (cookie != 'S') unexpectedCookie(cookie, 'S')
    in0.readUTF()
  }

  final def readOption[A](elem: => A): Option[A] = {
    val cookie = in0.readByte().toChar
    if (cookie != 'O') unexpectedCookie(cookie, 'O')
    val defined = in.readBoolean()
    if (defined) Some(elem) else None
  }

  final def readStringOption(): Option[String] = readOption(readString())

  final def readFloat(): Float = {
    val cookie = in0.readByte().toChar
    if (cookie != 'F') unexpectedCookie(cookie, 'F')
    in.readFloat()
  }

  final def readFloatVec (): Vec[Float  ] = readVec(readFloat ())

  final def readDouble(): Double = {
    val cookie = in0.readByte().toChar
    if (cookie != 'D') unexpectedCookie(cookie, 'D')
    in.readDouble()
  }

  final def readDoubleVec(): Vec[Double ] = readVec(readDouble())
}
