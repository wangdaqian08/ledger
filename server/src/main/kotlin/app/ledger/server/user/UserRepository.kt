package app.ledger.server.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByProviderAndSubject(provider: String, subject: String): UserEntity?
}
