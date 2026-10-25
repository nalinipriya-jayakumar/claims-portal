package com.redrivermutual.claims.adapter.out.persistence;

import com.redrivermutual.claims.TestcontainersConfiguration;
import com.redrivermutual.claims.application.port.out.DuplicateClaimNumberException;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimNumber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.concurrent.ThreadLocalRandom;

import static com.redrivermutual.claims.ClaimFixtures.NOW;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Repository adapter against real PostgreSQL with the Flyway schema. Each test rolls back. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaClaimRepository.class})
class JpaClaimRepositoryTest {

    @Autowired
    private JpaClaimRepository repository;

    @Test
    void savedClaimIsReadBackUnchanged() {
        Claim claim = Claim.submit(uniqueNumber(), validDetails(), NOW);

        repository.save(claim);

        assertThat(repository.findByClaimNumber(claim.claimNumber())).contains(claim);
    }

    @Test
    void unknownClaimNumberIsEmpty() {
        assertThat(repository.findByClaimNumber(ClaimNumber.of(1999, 1))).isEmpty();
    }

    @Test
    void databaseUniqueConstraintRejectsADuplicateClaimNumber() {
        ClaimNumber number = uniqueNumber();
        repository.save(Claim.submit(number, validDetails(), NOW));

        assertThatThrownBy(() -> repository.save(Claim.submit(number, validDetails(), NOW)))
                .isInstanceOf(DuplicateClaimNumberException.class);
    }

    /** Year 2000 never collides with numbers issued by the sequence in other tests. */
    private static ClaimNumber uniqueNumber() {
        return ClaimNumber.of(2000, ThreadLocalRandom.current().nextLong(1, ClaimNumber.MAX_SEQUENCE));
    }
}
