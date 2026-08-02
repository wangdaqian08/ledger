package app.ledger.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
@ConfigurationPropertiesScan
class LedgerApplication {
    /**
     * Explicit rather than relying on Kotlin's default argument being honoured during constructor
     * injection. Anything that reads the clock takes it from here, so time can be moved in a test
     * instead of being waited for.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<LedgerApplication>(*args)
}
