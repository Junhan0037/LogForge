package com.logforge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LogForgeApplication

fun main(args: Array<String>) {
    runApplication<LogForgeApplication>(*args)
}
