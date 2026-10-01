package app.foreway.content

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ContentServiceApplication

fun main(args: Array<String>) {
    runApplication<ContentServiceApplication>(*args)
}
