package com.talent.management.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.talent.management.features.tuition_payment.exception.TuitionExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class GlobalExceptionCompatibilityTest {
    private final org.springframework.test.web.servlet.MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new OtherFeatureController())
                    .setControllerAdvice(new TuitionExceptionHandler(), new GlobalExceptionHandler())
                    .build();

    @RestController
    static class OtherFeatureController {
        record Input(@NotBlank(message = "Required reason") String reason) {}

        @PostMapping("/other/validate")
        public void validate(@Valid @RequestBody Input input) {}

        @GetMapping("/other/business")
        public void business() {
            throw new BusinessException("Original business message");
        }

        @GetMapping("/other/id/{id}")
        public void id(@PathVariable Long id) {}
    }

    @Test
    void otherFeatureFieldValidationKeepsOriginalContract() throws Exception {
        mvc.perform(post("/other/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details.reason").value("Required reason"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void otherFeatureBusinessFailureKeepsOriginalMessage() throws Exception {
        mvc.perform(get("/other/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Original business message"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void invalidParameterKeepsOriginalMinimalResponse() throws Exception {
        mvc.perform(get("/other/id/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }
}
