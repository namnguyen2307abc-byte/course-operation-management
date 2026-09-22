package com.talent.management.shared.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Cấu hình tự động định vị Document Root cho JSP khi chạy trên IntelliJ IDEA hoặc terminal.
 * Giải quyết triệt để lỗi 404 "JSP file [/WEB-INF/views/...] not found"
 * khi Working Directory của IntelliJ là thư mục gốc thay vì thư mục com_be.
 */
@Configuration
public class TomcatJspConfiguration {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatJspCustomizer() {
        return factory -> {
            // Thử các vị trí có thể chứa webapp:
            File[] candidatePaths = new File[]{
                    new File("com_be/src/main/webapp"),
                    new File("src/main/webapp"),
                    new File("com_be/src/main/resources/META-INF/resources"),
                    new File("src/main/resources/META-INF/resources")
            };

            for (File candidate : candidatePaths) {
                if (candidate.exists() && candidate.isDirectory()) {
                    File testView = new File(candidate, "WEB-INF/views/pages/absence-makeup.jsp");
                    if (testView.exists()) {
                        factory.setDocumentRoot(candidate);
                        System.out.println(">>> [TomcatJspConfiguration] Đã cấu hình Document Root JSP tại: " + candidate.getAbsolutePath());
                        return;
                    }
                }
            }
        };
    }
}
