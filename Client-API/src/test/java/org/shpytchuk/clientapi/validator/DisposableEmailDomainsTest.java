package org.shpytchuk.clientapi.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DisposableEmailDomainsTest {

    private static final String URL = "https://lists.example/disposable_email_blocklist.conf";

    private MockRestServiceServer server;
    private DisposableEmailDomains domains;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        domains = new DisposableEmailDomains(builder.build(), URL);
    }

    @Test
    void knowsNothingUntilTheListIsLoaded() {
        assertThat(domains.isDisposable("olena@mailinator.com")).isFalse();
    }

    @Test
    void rejectsADomainFromTheListAndItsSubdomainsInAnyCase() {
        load("mailinator.com\n\n  10minutemail.com \n");

        assertThat(domains.isDisposable("olena@mailinator.com")).isTrue();
        assertThat(domains.isDisposable("olena@inbox.Mailinator.COM")).isTrue();
        assertThat(domains.isDisposable("olena@10minutemail.com")).isTrue();
        assertThat(domains.isDisposable("olena@gmail.com")).isFalse();
        assertThat(domains.isDisposable("olena@notmailinator.com")).isFalse();
    }

    @Test
    void keepsThePreviousListWhenADownloadFails() {
        load("mailinator.com\n");
        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        domains.refresh();

        assertThat(domains.isDisposable("olena@mailinator.com")).isTrue();
    }

    @Test
    void keepsThePreviousListWhenTheDownloadIsEmpty() {
        load("mailinator.com\n");
        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        domains.refresh();

        assertThat(domains.isDisposable("olena@mailinator.com")).isTrue();
    }

    @Test
    void doesNothingWithoutAUrl() {
        DisposableEmailDomains off = new DisposableEmailDomains(RestClient.create(), "");

        off.refresh();

        assertThat(off.isDisposable("olena@mailinator.com")).isFalse();
    }

    @Test
    void ignoresAnAddressItCannotParse() {
        load("mailinator.com\n");

        assertThat(domains.isDisposable("not-an-email")).isFalse();
    }

    private void load(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
        domains.refresh();
        server.verify();
    }
}
