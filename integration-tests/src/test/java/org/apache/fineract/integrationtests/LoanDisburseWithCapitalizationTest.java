/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for atomic disburse with capitalization command.
 */
public class LoanDisburseWithCapitalizationTest extends BaseLoanIntegrationTest {

    @BeforeAll
    public void setup() {
        // Setup test data - business steps can be added here if needed
    }

    @Test
    public void testDisburseWithCapitalization_Success() {
        runAt("01 January 2024", () -> {
            // Create client
            PostClientsResponse client = clientHelper.createClient(ClientHelper.defaultClientCreationRequest());

            // Create loan product with capitalization enabled
            PostLoanProductsResponse loanProduct = loanProductHelper.createLoanProduct(create4IProgressive()
                    .enableIncomeCapitalization(true)
                    .capitalizedIncomeCalculationType(PostLoanProductsRequest.CapitalizedIncomeCalculationTypeEnum.FLAT)
                    .capitalizedIncomeStrategy(PostLoanProductsRequest.CapitalizedIncomeStrategyEnum.EQUAL_AMORTIZATION)
                    .deferredIncomeLiabilityAccountId(deferredIncomeLiabilityAccount.getAccountID().longValue())
                    .incomeFromCapitalizationAccountId(feeIncomeAccount.getAccountID().longValue())
                    .capitalizedIncomeType(PostLoanProductsRequest.CapitalizedIncomeTypeEnum.FEE));

            // Apply and approve loan for 1250 (gross = net 1000 + fees 250)
            Long loanId = applyAndApproveProgressiveLoan(client.getClientId(), loanProduct.getResourceId(), "01 January 2024", 1250.0, // Gross
                                                                                                                                          // principal
                    7.0, // Interest rate
                    6, // Number of repayments
                    null);

            // Disburse with capitalization - single atomic call
            PostLoansLoanIdResponse response = loanTransactionHelper.disburseWithCapitalization(loanId, BigDecimal.valueOf(1000), // Net
                                                                                                                                   // to
                                                                                                                                   // client
                    BigDecimal.valueOf(250), // Fees to capitalize
                    "01 January 2024");

            assertNotNull(response);

            // Verify loan state
            GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoan(requestSpec, responseSpec, loanId);

            // Should have 2 transactions: Disbursement + Capitalized Income
            assertEquals(2, loanDetails.getTransactions().size());

            // Verify disbursement transaction
            assertEquals("Disbursement", loanDetails.getTransactions().get(0).getType().getValue());
            assertEquals(1000.0, loanDetails.getTransactions().get(0).getAmount());

            // Verify capitalized income transaction
            assertEquals("Capitalized Income", loanDetails.getTransactions().get(1).getType().getValue());
            assertEquals(250.0, loanDetails.getTransactions().get(1).getAmount());

            // Verify principal now includes capitalized fees
            assertEquals(1250.0, loanDetails.getPrincipal());
        });
    }

    @Test
    public void testDisburseWithCapitalization_NoCapitalizationAmount() {
        runAt("01 January 2024", () -> {
            // When capitalizedIncomeAmount is 0 or not provided,
            // it should just do a normal disbursement

            PostClientsResponse client = clientHelper.createClient(ClientHelper.defaultClientCreationRequest());

            PostLoanProductsResponse loanProduct = loanProductHelper.createLoanProduct(create4IProgressive()
                    .enableIncomeCapitalization(true)
                    .capitalizedIncomeCalculationType(PostLoanProductsRequest.CapitalizedIncomeCalculationTypeEnum.FLAT)
                    .capitalizedIncomeStrategy(PostLoanProductsRequest.CapitalizedIncomeStrategyEnum.EQUAL_AMORTIZATION)
                    .deferredIncomeLiabilityAccountId(deferredIncomeLiabilityAccount.getAccountID().longValue())
                    .incomeFromCapitalizationAccountId(feeIncomeAccount.getAccountID().longValue())
                    .capitalizedIncomeType(PostLoanProductsRequest.CapitalizedIncomeTypeEnum.FEE));

            Long loanId = applyAndApproveProgressiveLoan(client.getClientId(), loanProduct.getResourceId(), "01 January 2024", 1000.0,
                    7.0, 6, null);

            // Disburse without capitalization amount
            loanTransactionHelper.disburseWithCapitalization(loanId, BigDecimal.valueOf(1000), BigDecimal.ZERO, // No
                                                                                                                 // capitalization
                    "01 January 2024");

            // Verify only disbursement transaction exists
            GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoan(requestSpec, responseSpec, loanId);
            assertEquals(1, loanDetails.getTransactions().size());
            assertEquals("Disbursement", loanDetails.getTransactions().get(0).getType().getValue());
        });
    }

    @Test
    public void testDisburseWithCapitalization_WithNullCapitalizationAmount() {
        runAt("01 January 2024", () -> {
            // When capitalizedIncomeAmount is null (not provided),
            // it should just do a normal disbursement

            PostClientsResponse client = clientHelper.createClient(ClientHelper.defaultClientCreationRequest());

            PostLoanProductsResponse loanProduct = loanProductHelper.createLoanProduct(create4IProgressive()
                    .enableIncomeCapitalization(true)
                    .capitalizedIncomeCalculationType(PostLoanProductsRequest.CapitalizedIncomeCalculationTypeEnum.FLAT)
                    .capitalizedIncomeStrategy(PostLoanProductsRequest.CapitalizedIncomeStrategyEnum.EQUAL_AMORTIZATION)
                    .deferredIncomeLiabilityAccountId(deferredIncomeLiabilityAccount.getAccountID().longValue())
                    .incomeFromCapitalizationAccountId(feeIncomeAccount.getAccountID().longValue())
                    .capitalizedIncomeType(PostLoanProductsRequest.CapitalizedIncomeTypeEnum.FEE));

            Long loanId = applyAndApproveProgressiveLoan(client.getClientId(), loanProduct.getResourceId(), "01 January 2024", 1000.0,
                    7.0, 6, null);

            // Disburse with null capitalization amount
            loanTransactionHelper.disburseWithCapitalization(loanId, BigDecimal.valueOf(1000), null, // null
                                                                                                      // capitalization
                    "01 January 2024");

            // Verify only disbursement transaction exists
            GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoan(requestSpec, responseSpec, loanId);
            assertEquals(1, loanDetails.getTransactions().size());
            assertEquals("Disbursement", loanDetails.getTransactions().get(0).getType().getValue());
        });
    }
}
