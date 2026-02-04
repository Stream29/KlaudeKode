package io.github.stream29.kode

import io.github.stream29.kode.scripting.eval
import io.github.stream29.kode.scripting.EvalResult
import kotlinx.coroutines.runBlocking

public fun main(): Unit = runBlocking {
    println("=== Kotlin 脚本运行器测试 ===\n")
    
    // 测试 1: 简单的 Hello World
    println("测试 1: Hello World")
    val script1 = """
        println("Hello from Kotlin Script!")
        "Script completed successfully"
    """.trimIndent()
    
    when (val result = eval(script1)) {
        is EvalResult.Success -> {
            println("✅ 成功!")
            println("输出: ${result.stdout}")
            println("返回值: ${result.returnValue}\n")
        }
        is EvalResult.Failure -> {
            println("❌ 失败: ${result.message}\n")
        }
    }
    
    // 测试 2: 数学计算
    println("测试 2: 数学计算")
    val script2 = """
        val numbers = listOf(1, 2, 3, 4, 5)
        val sum = numbers.sum()
        val average = numbers.average()
        println("数字: ${'$'}numbers")
        println("总和: ${'$'}sum")
        println("平均值: ${'$'}average")
        sum
    """.trimIndent()
    
    when (val result = eval(script2)) {
        is EvalResult.Success -> {
            println("✅ 成功!")
            println("输出: ${result.stdout}")
            println("返回值: ${result.returnValue}\n")
        }
        is EvalResult.Failure -> {
            println("❌ 失败: ${result.message}\n")
        }
    }
    
    // 测试 3: 数据类和集合操作
    println("测试 3: 数据类和集合操作")
    val script3 = """
        data class Person(val name: String, val age: Int)
        
        val people = listOf(
            Person("张三", 25),
            Person("李四", 30),
            Person("王五", 28)
        )
        
        println("人员列表:")
        people.forEach { println("  ${'$'}{it.name} - ${'$'}{it.age}岁") }
        
        val avgAge = people.map { it.age }.average()
        println("\n平均年龄: ${'$'}avgAge")
        
        avgAge
    """.trimIndent()
    
    when (val result = eval(script3)) {
        is EvalResult.Success -> {
            println("✅ 成功!")
            println("输出: ${result.stdout}")
            println("返回值: ${result.returnValue}\n")
        }
        is EvalResult.Failure -> {
            println("❌ 失败: ${result.message}\n")
        }
    }
    
    // 测试 4: 错误处理
    println("测试 4: 错误处理（故意出错）")
    val script4 = """
        val x = 10
        val y = 0
        x / y  // 除以零
    """.trimIndent()
    
    when (val result = eval(script4)) {
        is EvalResult.Success -> {
            println("✅ 成功!")
            println("输出: ${result.stdout}")
            println("返回值: ${result.returnValue}\n")
        }
        is EvalResult.Failure -> {
            println("❌ 预期的失败: ${result.message}\n")
        }
    }
    
    println("=== 所有测试完成 ===")
}
