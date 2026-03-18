package com.chainsettle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsettle.exception.GlobalExceptionHandler;
import com.chainsettle.model.dto.DvPRequest;
import com.chainsettle.model.dto.SettlementResponse;
import com.chainsettle.service.OrgIdentityService;
import com.chainsettle.service.SettlementService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SettlementController.class)
@Import({OrgIdentityService.class, GlobalExceptionHandler.class})
class SettlementControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettlementService settlementService;

    @SpyBean
    private OrgIdentityService orgIdentityService;

    @Test
    void initiateUsesValidatedOrgHeader() throws Exception {
        when(settlementService.initiate(eq("BankAlpha"), any())).thenReturn(new SettlementResponse(
            "DVP-1",
            "REQ-DVP",
            new DvPRequest.SettlementLegRequest("ACC-A", "ACC-B", new BigDecimal("100.00"), "USD", "CASH"),
            new DvPRequest.SettlementLegRequest("ACC-BOND-B", "ACC-BOND-A", new BigDecimal("5.00"), "UNITS", "BOND-US10Y"),
            "PENDING",
            "BankAlpha",
            "2026-03-18T10:00:00Z",
            null,
            null
        ));

        mockMvc.perform(post("/api/v1/settlements/dvp")
                .header("X-ChainSettle-Org", "BankAlpha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cashLeg": {
                        "fromAccountId": "ACC-A",
                        "toAccountId": "ACC-B",
                        "amount": 100.00,
                        "currency": "USD",
                        "asset": "CASH"
                      },
                      "assetLeg": {
                        "fromAccountId": "ACC-BOND-B",
                        "toAccountId": "ACC-BOND-A",
                        "amount": 5.00,
                        "currency": "UNITS",
                        "asset": "BOND-US10Y"
                      }
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.settlementId").value("DVP-1"));

        verify(orgIdentityService).requireOrg("BankAlpha");
        verify(settlementService).initiate(eq("BankAlpha"), any());
    }

    @Test
    void executeRejectsInvalidOrganizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/dvp/DVP-1/execute")
                .header("X-ChainSettle-Org", "Nope"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unknown organization: Nope"));
    }

    @Test
    void cancelRequiresOrganizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/dvp/DVP-1/cancel"))
            .andExpect(status().isBadRequest());
    }
}
