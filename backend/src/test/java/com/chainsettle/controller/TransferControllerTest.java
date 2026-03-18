package com.chainsettle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsettle.exception.GlobalExceptionHandler;
import com.chainsettle.model.dto.TransferResponse;
import com.chainsettle.service.OrgIdentityService;
import com.chainsettle.service.TransferService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransferController.class)
@Import({OrgIdentityService.class, GlobalExceptionHandler.class})
class TransferControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @SpyBean
    private OrgIdentityService orgIdentityService;

    @Test
    void transferEndpointReturnsCreatedResponse() throws Exception {
        when(transferService.transfer(eq("BankAlpha"), any())).thenReturn(new TransferResponse(
            "TXN-1",
            "SETTLED",
            "TRANSFER",
            "ACC-A",
            "ACC-B",
            "BankAlpha",
            "BankBeta",
            new BigDecimal("100.00"),
            "USD",
            "2026-03-18T10:00:00Z",
            5L,
            "fabric-1",
            null,
            Map.of("purpose", "Trade settlement")
        ));

        mockMvc.perform(post("/api/v1/transfers")
                .header("X-ChainSettle-Org", "BankAlpha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fromAccountId": "ACC-A",
                      "toAccountId": "ACC-B",
                      "amount": 100.00,
                      "currency": "USD",
                      "metadata": {
                        "purpose": "Trade settlement"
                      }
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.txId").value("TXN-1"))
            .andExpect(jsonPath("$.status").value("SETTLED"));

        verify(orgIdentityService).requireOrg("BankAlpha");
        verify(transferService).transfer(eq("BankAlpha"), any());
    }

    @Test
    void transferEndpointRejectsInvalidOrganizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                .header("X-ChainSettle-Org", "NotABank")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fromAccountId": "ACC-A",
                      "toAccountId": "ACC-B",
                      "amount": 100.00,
                      "currency": "USD",
                      "metadata": {
                        "purpose": "Trade settlement"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unknown organization: NotABank"));
    }

    @Test
    void transferEndpointRequiresOrganizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fromAccountId": "ACC-A",
                      "toAccountId": "ACC-B",
                      "amount": 100.00,
                      "currency": "USD",
                      "metadata": {
                        "purpose": "Trade settlement"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
