package com.logforge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LogForgeApplication

fun main(args: Array<String>) {
    runApplication<LogForgeApplication>(*args)
}
