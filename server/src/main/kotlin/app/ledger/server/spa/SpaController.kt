package app.ledger.server.spa

import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * The SPA's screen routes, answered by the server because the history-mode router owns the address
 * bar and the bundle rides inside the jar (spec §4). Each forwards to the bundle's index.html,
 * which the static-resource handler serves — deliberately an explicit list, not a catch-all: a
 * mistyped URL or crawler probe gets an honest 404, never a 200 of app shell.
 *
 * `no-cache` because the shell names the content-hashed bundle: a heuristically cached shell would
 * pin a browser to a bundle the next deploy deletes. The assets themselves are immutable by name
 * and cache freely.
 */
@Controller
class SpaController {
    @GetMapping("/", "/signin", "/trips/{tripId}", "/join/{tripId}")
    fun app(response: HttpServletResponse): String {
        response.setHeader("Cache-Control", "no-cache")
        return "forward:/index.html"
    }
}
