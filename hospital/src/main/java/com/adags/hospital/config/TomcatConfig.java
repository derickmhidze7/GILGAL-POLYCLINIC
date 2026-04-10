package com.adags.hospital.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat-level customizations for the embedded server.
 */
@Configuration
public class TomcatConfig {

    /**
     * Switches Tomcat's connector from the default Http11NioProtocol (which uses
     * java.nio.channels.Selector / PipeImpl) to Http11Nio2Protocol (which uses
     * AsynchronousChannelGroup — no Selector, no PipeImpl).
     *
     * This fixes a Windows 11 + Java 21 incompatibility where PipeImpl tries to
     * create an internal wakeup pipe via Unix Domain Sockets (WEPollSelectorImpl),
     * which fails with SocketException "Invalid argument: connect" on certain
     * Windows networking configurations.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2ProtocolCustomizer() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }

    /**
     * Raises Tomcat 11's per-request multipart part limit.  In Tomcat 11.0.x,
     * Request.parseParts() sets upload.setFileCountMax(min(maxParameterCount, maxPartCount)).
     * Spring Boot 4.0.2 defaults server.tomcat.max-part-count = 10, which is too low for
     * forms with many fields.  We also set it here as a hard backstop in case the
     * application.properties value is ever removed.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartPartCountCustomizer() {
        return factory -> factory.addConnectorCustomizers(
            connector -> connector.setMaxPartCount(1_000)
        );
    }
}
