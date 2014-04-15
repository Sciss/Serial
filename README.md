# Serial

## statement

Serial provides a simple serialization layer for the Scala programming language. It is based on readers and writers of byte array or file backed up data streams.

Serial is (C)opyright 2011&ndash;2014 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU Lesser General Public License](https://raw.github.com/Sciss/Serial/master/LICENSE) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`

## requirements / installation

The project builds with sbt 0.13 against Scala 2.10.

## linking

The following dependency is necessary:

    "de.sciss" %% "serial" % v

The current version `v` is `"1.0.1+`".

## example

```scala

    case class Person(name: String, age: Int)

    implicit object PersonSerializer extends ImmutableSerializer[Person] {
      def write(v: Person, out: DataOutput) {
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
    val ser = implicitly[ImmutableSerializer[Person]]
    ser.write(p, out)
    val bin = out.toByteArray

    val in  = DataInput(bin)
    val q   = ser.read(in)
    println(q)
    assert(p == q)
```

There are serializers included for the standard primitive types and common extensions such as `Option`, `Either`, `Tuple2`, `List` etc.