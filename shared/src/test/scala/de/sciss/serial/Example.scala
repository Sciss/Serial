package de.sciss.serial

trait Example {
  case class Person(name: String, age: Int)

  implicit object PersonFormat$ extends ConstFormat[Person] {
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
}