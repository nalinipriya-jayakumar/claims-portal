package com.redrivermutual.claims.adapter.in.web;

import com.redrivermutual.claims.adapter.in.web.model.FieldError;
import com.redrivermutual.claims.domain.ClaimStatus;
import com.redrivermutual.claims.domain.LossType;
import com.redrivermutual.claims.domain.Province;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The domain and the OpenAPI contract define some of the same vocabularies. The generated
 * enums are the contract's; these tests fail the build as soon as the two disagree, in either
 * direction, instead of at runtime when a value fails to map.
 */
class ContractConformanceTest {

    @Test
    void lossTypesMatchTheContract() {
        assertSameValues(LossType.values(), Enum::name,
                com.redrivermutual.claims.adapter.in.web.model.LossType.values(),
                com.redrivermutual.claims.adapter.in.web.model.LossType::getValue);
    }

    @Test
    void provincesMatchTheContract() {
        assertSameValues(Province.values(), Enum::name,
                com.redrivermutual.claims.adapter.in.web.model.Province.values(),
                com.redrivermutual.claims.adapter.in.web.model.Province::getValue);
    }

    @Test
    void claimStatusesMatchTheContract() {
        assertSameValues(ClaimStatus.values(), Enum::name,
                com.redrivermutual.claims.adapter.in.web.model.ClaimStatus.values(),
                com.redrivermutual.claims.adapter.in.web.model.ClaimStatus::getValue);
    }

    /** Every code the API can emit is one the contract documents for clients, and vice versa. */
    @Test
    void errorCodesMatchTheContract() {
        assertSameValues(ErrorCode.values(), Enum::name, FieldError.CodeEnum.values(), FieldError.CodeEnum::getValue);
    }

    private static <D, C> void assertSameValues(D[] domain, Function<D, String> domainValue,
                                                C[] contract, Function<C, String> contractValue) {
        assertThat(Arrays.stream(domain).map(domainValue))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(contract).map(contractValue).toList());
    }
}
