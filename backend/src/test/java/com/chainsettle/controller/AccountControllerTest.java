package com.chainsettle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsettle.exception.GlobalExceptionHandler;
import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.service.AccountService;
import com.chainsettle.service.OrgIdentityService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class)
@Import({OrgIdentityService.class, GlobalExceptionHandler.class})
class AccountControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @SpyBean
    private OrgIdentityService orgIdentityService;

    @Test
    void createAccountUsesValidatedOrgHeader() throws Exception {
        when(accountService.createAccount(eq("BankAlpha"), any())).thenReturn(new AccountResponse(
            "ACC-BANKALPHA-USD",
            "BankAlpha",
            "USD",
            new BigDecimal("1000.00"),
            "ACTIVE",
            "CASH",
            null,
            "2026-03-18T10:00:00Z",
            "2026-03-18T10:00:00Z"
        ));

        mockMvc.perform(post("/api/v1/accounts")
                .header("X-ChainSettle-Org", "BankAlpha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": "ACC-BANKALPHA-USD",
                      "orgName": "BankAlpha",
                      "currency": "USD",
                      "initialBalance": 1000.00,
                      "accountType": "CASH"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").value("ACC-BANKALPHA-USD"));

        verify(orgIdentityService).requireOrg("BankAlpha");
        verify(accountService).createAccount(eq("BankAlpha"), any());
    }

    @Test
    void createAccountRejectsInvalidOrganizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .header("X-ChainSettle-Org", "BadOrg")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": "ACC-BANKALPHA-USD",
                      "orgName": "BankAlpha",
                      "currency": "USD",
                      "initialBalance": 1000.00,
                      "accountType": "CASH"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unknown organization: BadOrg"));
    }

    @Test
    void deactivateAccountRequiresOrganizationHeader() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/ACC-BANKALPHA-USD"))
            .andExpect(status().isBadRequest());
    }
}
