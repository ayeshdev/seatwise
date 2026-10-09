package com.seatwise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatwise.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatwiseApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsAndHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // Proves springdoc is compatible with Boot 4 and the permitAll rule matches its path.
    @Test
    void openApiDocumentIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/openapi.json")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresAuthentication()throws Exception {
        mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
    }
}
