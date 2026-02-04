#!/usr/bin/env kotlin

println("Hello from Kotlin Script!")
println("Kotlin version: ${KotlinVersion.CURRENT}")

// 简单的计算
val numbers = listOf(1, 2, 3, 4, 5)
val sum = numbers.sum()
val average = numbers.average()

println("\n数字列表: $numbers")
println("总和: $sum")
println("平均值: $average")

// 使用高阶函数
println("\n偶数:")
numbers.filter { it % 2 == 0 }.forEach { println("  $it") }

// 数据类
data class Person(val name: String, val age: Int)

val people = listOf(
    Person("张三", 25),
    Person("李四", 30),
    Person("王五", 28)
)

println("\n人员信息:")
people.forEach { println("  ${it.name} - ${it.age}岁") }

println("\n✅ Kotlin 脚本运行成功!")
