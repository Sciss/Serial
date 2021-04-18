# Serial

[![Build Status](https://github.com/Sciss/Serial/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/Sciss/Serial/actions?query=workflow%3A%22Scala+CI%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/serial_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/serial_2.13)

## statement

Serial provides a simple serialization layer for the Scala programming language. It is based on readers and writers 
of byte array or file backed up data streams.

Serial is (C)opyright 2011&ndash;2021 by Hanns Holger Rutz. All rights reserved. It is released under 
the [GNU Lesser General Public License](https://raw.github.com/Sciss/Serial/main/LICENSE) and comes with 
absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`.

## requirements / installation

The project builds with sbt against Scala 2.13, 2.12, Dotty (JVM) and Scala 2.13 (JS). 
The last version to support Scala 2.11 was v1.1.1.

## linking

The following dependency is necessary:

    "de.sciss" %% "serial" % v

The current version `v` is `"2.0.1`".

## example

In most cases, you want to serialize immutable objects such as instances of `case` classes. For those, the
`ConstFormat` is used.

```scala
import de.sciss.serial._

case class Person(name: String, age: Int)

implicit object PersonSerializer extends ConstFormat[Person] {
  def write(v: Person, out: DataOutput): Unit = {
    out.writeUTF(v.name)
    out.writeInt(v.age)
  }

  def read(in: DataInput): Person = {
    val name  = in.readUTF()
    val age   = in.readInt()
    Person(name, age)
  }
}

val p   = Person("Nelson", 94)
val out = DataOutput()
val ser = implicitly[ConstFormat[Person]]
ser.write(p, out)
val bin = out.toByteArray

val in  = DataInput(bin)
val q   = ser.read(in)
println(q)
assert(p == q)
```

There are serializers included for the standard primitive types and common extensions such
as `Option`, `Either`, `Tuple2`, `List` etc.

## RefMapIn, RefMapOut

These are helper classes that can be used to implement serialization for open-ended type hierarchies,
typically involving `Product` types (such as case classes). They use an initial `Char` element to distinguish
between multiple types, such as primitives, composites (options, sequences), and products. Traditionally,
products are serialized using their "name", derived from `productPrefix` and a default package, but they can
also be implemented using an `Int` typeId registry.

## Transactional provision

The library is mainly used in conjunction with the [Lucre](https://github.com/Sciss/Lucre) framework, where 
transactional context is needed. Here the types are `TReader`, `TWriter` and `TFormat` which provide an additional
type parameter for the implicitly passed transaction. Since `ConstReader[A]` and `ConstFormat[A]` extend
 `TReader[Any A]` and `TFormat[Any, A]`, the latter can be used throughout.
