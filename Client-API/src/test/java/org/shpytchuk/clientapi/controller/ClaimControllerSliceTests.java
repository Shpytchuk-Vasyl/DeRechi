package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.config.GraphQlConfig;
import org.shpytchuk.clientapi.config.GraphQlExceptionResolver;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.exeption.ContactErrorType;
import org.shpytchuk.clientapi.service.ReturnService;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockService;
import org.shpytchuk.clientapi.validator.DisposableEmailDomains;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(controllers = ClaimController.class)
@Import({GraphQlConfig.class, GraphQlExceptionResolver.class})
class ClaimControllerSliceTests {

    private static final String CLAIM_LOST = """
            mutation Claim($id: ID!, $contact: ContactInfoInput!) {
              claimLostItem(id: $id, contact: $contact) { id repeated }
            }
            """;

    @Autowired
    private GraphQlTester tester;

    @MockitoBean
    private LostClaimService lostClaimService;

    @MockitoBean
    private FoundClaimService foundClaimService;

    @MockitoBean
    private ReturnService returnService;

    @MockitoBean
    private ClaimUnlockService unlockService;

    @MockitoBean
    private FourthwallProperties fourthwall;

    @MockitoBean
    private DisposableEmailDomains disposableEmailDomains;

    @Test
    void rejectsAThrowawayEmailAsDisposableAndClaimsNothing() {
        when(disposableEmailDomains.isDisposable("olena@mailinator.com")).thenReturn(true);

        claim("Olena@Mailinator.com")
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ContactErrorType.DISPOSABLE_EMAIL);
                    assertThat(errors.getFirst().getMessage()).contains("email");
                });

        verify(lostClaimService, never()).claim(anyLong(), any());
    }

    @Test
    void passesAnOrdinaryEmail() {
        when(lostClaimService.claim(anyLong(), any())).thenReturn(new ClaimDto(7L, false, null, false, false));

        claim("olena@example.com")
                .path("claimLostItem.id").entity(Long.class).isEqualTo(7L);
    }

    private GraphQlTester.Response claim(String email) {
        return tester.document(CLAIM_LOST)
                .variable("id", 1)
                .variable("contact", Map.of("phone", "+380671234567", "email", email))
                .execute();
    }
}
