package org.shpytchuk.getaway.config;

import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.logging.AccessLog;
import reactor.netty.http.server.logging.AccessLogFactory;

@Configuration
public class AccessLogConfig {

    @Bean
    public NettyServerCustomizer accessLogCustomizer() {
        return server -> server.accessLog(true, AccessLogFactory.createFilter(
                request -> !path(request.uri()).startsWith("/actuator"),
                request -> AccessLog.create("{} {} -> {} in {} ms",
                        request.method(), path(request.uri()), request.status(), request.duration())));
    }

    static String path(CharSequence uri) {
        if (uri == null) {
            return "";
        }
        String value = uri.toString();
        int query = value.indexOf('?');
        return query < 0 ? value : value.substring(0, query);
    }
}
