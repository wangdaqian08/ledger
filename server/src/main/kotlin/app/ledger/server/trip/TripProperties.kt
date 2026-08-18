package app.ledger.server.trip

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ledger.trips")
data class TripProperties(
    /**
     * How long a deleted trip can still be brought back before the sweep destroys it. Thirty days:
     * a mistaken delete is usually noticed within a week, and a month is long enough to survive
     * the trip's last stragglers settling up without keeping a graveyard nobody asked for.
     */
    val restoreWindow: Duration = Duration.ofDays(30),
)
