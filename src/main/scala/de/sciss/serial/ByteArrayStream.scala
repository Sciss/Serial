/*
 *  ByteArrayStream.scala
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

trait ByteArrayStream {
  def reset(): Unit
  def toByteArray: Array[Byte]
  def buffer: Array[Byte]
}