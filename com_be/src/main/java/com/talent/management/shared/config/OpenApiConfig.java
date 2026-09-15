package com.talent.management.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI talentManagementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Talent Center Academic Operation Management API")
                        .description("Hệ thống Quản lý Vận hành Trung tâm Đào tạo Năng khiếu (Piano, Âm nhạc, Nghệ thuật) - Cung cấp RESTful API cho 4 phân hệ chính: Cơ sở vật chất & Xếp lớp, Nghỉ học & Học bù, Test đầu vào & Lộ trình, và Nghĩa vụ thanh toán.")
                        .version("1.0.0")
                        .contact(new Contact().name("Talent Management Team").email("contact@talentcenter.edu.vn")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components().addSecuritySchemes("Bearer Authentication",
                        new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
