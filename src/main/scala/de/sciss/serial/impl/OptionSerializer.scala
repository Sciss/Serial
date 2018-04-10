/*
 *  OptionSerializer.scala
 *  (Serial)
 *
 * Copyright (c) 2011-2018 Hanns Holger Rutz. All rights reserved.
 *
 * This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.serial
package impl

import scala.annotation.switch

final class OptionSerializer[Tx, Acc, A](peer: Serializer[Tx, Acc, A])
  extends Serializer[Tx, Acc, Option[A]] {

  def write(opt: Option[A], out: DataOutput): Unit =
    opt match {
      case Some(v)  => out.writeByte(1); peer.write(v, out)
      case _        => out.writeByte(0)
    }

  def read(in: DataInput, acc: Acc)(implicit tx: Tx): Option[A] = (in.readByte(): @switch) match {
    case 1 => Some(peer.read(in, acc))
    case 0 => None
  }
}

final class ImmutableOptionSerializer[A](peer: ImmutableSerializer[A])
  extends ImmutableSerializer[Option[A]] {

  def write(opt: Option[A], out: DataOutput): Unit =
    opt match {
      case Some(v)  => out.writeByte(1); peer.write(v, out)
      case _        => out.writeByte(0)
    }

  def read(in: DataInput): Option[A] = (in.readByte(): @switch) match {
    case 1 => Some(peer.read(in))
    case 0 => None
  }
}
