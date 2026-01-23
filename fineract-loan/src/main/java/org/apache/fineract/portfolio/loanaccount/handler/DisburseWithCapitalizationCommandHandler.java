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
package org.apache.fineract.portfolio.loanaccount.handler;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.DataIntegrityErrorHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.loanaccount.service.CapitalizedIncomePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler for atomic disbursement with fee capitalization.
 * 
 * This handler ensures that both disbursement and capitalized income are processed in a single transaction. If either
 * fails, both are rolled back automatically.
 * 
 * Usage: POST /loans/{loanId}?command=disburseWithCapitalization { "actualDisbursementDate": "01 January 2024",
 * "transactionAmount": 1000, "capitalizedIncomeAmount": 250, "note": "Disbursement with fee capitalization",
 * "dateFormat": "dd MMMM yyyy", "locale": "en" }
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CommandType(entity = "LOAN", action = "DISBURSEWITHCAPITALIZATION")
public class DisburseWithCapitalizationCommandHandler implements NewCommandSourceHandler {

    private final LoanWritePlatformService loanWritePlatformService;
    private final CapitalizedIncomePlatformService capitalizedIncomeService;
    private final DataIntegrityErrorHandler dataIntegrityErrorHandler;

    public static final String CAPITALIZED_INCOME_AMOUNT_PARAM = "capitalizedIncomeAmount";

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        final Long loanId = command.entityId();

        log.info("Processing disburseWithCapitalization for loan {}", loanId);

        try {
            // Step 1: Disburse the loan
            log.debug("Step 1: Disbursing loan {}", loanId);
            CommandProcessingResult disbursementResult = loanWritePlatformService.disburseLoan(loanId, command, false);

            log.info("Loan {} disbursed successfully, resourceId: {}", loanId, disbursementResult.getResourceId());

            // Step 2: Add capitalized income if amount is specified
            BigDecimal capitalizedAmount = command.bigDecimalValueOfParameterNamed(CAPITALIZED_INCOME_AMOUNT_PARAM);

            if (capitalizedAmount != null && capitalizedAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("Step 2: Adding capitalized income of {} for loan {}", capitalizedAmount, loanId);

                CommandProcessingResult capitalizationResult = capitalizedIncomeService.addCapitalizedIncome(loanId, command);

                log.info("Capitalized income added for loan {}, transaction: {}", loanId, capitalizationResult.getResourceId());
            } else {
                log.debug("No capitalizedIncomeAmount specified, skipping capitalization for loan {}", loanId);
            }

            // Both operations succeeded - transaction will commit
            log.info("DisburseWithCapitalization completed successfully for loan {}", loanId);

            return disbursementResult;

        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            // Log and handle data integrity issues
            log.error("Data integrity error during disburseWithCapitalization for loan {}", loanId, dve);
            dataIntegrityErrorHandler.handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve,
                    "loan.disbursement.with.capitalization", "DisburseWithCapitalization");
            return CommandProcessingResult.empty();
        } catch (final Exception e) {
            // Any exception will cause automatic rollback due to @Transactional
            log.error("Error during disburseWithCapitalization for loan {}, transaction will be rolled back", loanId, e);
            throw e;
        }
    }
}
