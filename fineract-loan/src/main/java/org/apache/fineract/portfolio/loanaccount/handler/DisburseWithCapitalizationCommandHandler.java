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

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.DataIntegrityErrorHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanTransactionValidator;
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
    private final LoanTransactionValidator loanTransactionValidator;
    private final FromJsonHelper fromApiJsonHelper;

    /**
     * Supported parameters for disburse with capitalization command. This includes all standard disbursement parameters
     * plus capitalizedIncomeAmount.
     */
    private static final Set<String> DISBURSE_WITH_CAPITALIZATION_PARAMETERS = new HashSet<>(
            Arrays.asList("actualDisbursementDate", "externalId", "note", "locale", "dateFormat", "paymentTypeId", "accountNumber",
                    "checkNumber", "routingCode", "receiptNumber", "bankNumber", "adjustRepaymentDate",
                    LoanApiConstants.principalDisbursedParameterName, LoanApiConstants.fixedEmiAmountParameterName,
                    LoanApiConstants.postDatedChecks, LoanApiConstants.disbursementNetDisbursalAmountParameterName,
                    LoanApiConstants.CAPITALIZED_INCOME_AMOUNT_PARAM, "transactionAmount" // transactionAmount as alias
            ));

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        final Long loanId = command.entityId();

        log.info("Processing disburseWithCapitalization for loan {}", loanId);

        try {
            // STEP 0: Validate parameters first
            log.debug("Step 0: Validating parameters for loan {}", loanId);
            validateDisburseWithCapitalization(command, loanId);

            // Step 1: Disburse the loan
            log.debug("Step 1: Disbursing loan {}", loanId);
            CommandProcessingResult disbursementResult = loanWritePlatformService.disburseLoan(loanId, command, false);

            log.info("Loan {} disbursed successfully, resourceId: {}", loanId, disbursementResult.getResourceId());

            // Step 2: Add capitalized income if amount is specified
            BigDecimal capitalizedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.CAPITALIZED_INCOME_AMOUNT_PARAM);

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

    /**
     * Validates disburse with capitalization command parameters.
     */
    private void validateDisburseWithCapitalization(final JsonCommand command, final Long loanId) {
        final String json = command.json();

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        // Check for unsupported parameters
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, DISBURSE_WITH_CAPITALIZATION_PARAMETERS);

        // Now call the standard disbursement validation
        // (which will validate actualDisbursementDate, amounts, etc.)
        loanTransactionValidator.validateDisbursement(command, false, loanId);

        // Additional validation for capitalizedIncomeAmount if present
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
        final BigDecimal capitalizedAmount = this.fromApiJsonHelper.extractBigDecimalNamed(
                LoanApiConstants.CAPITALIZED_INCOME_AMOUNT_PARAM, element, locale);

        if (capitalizedAmount != null) {
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                    .resource("loan.disburse.with.capitalization");

            baseDataValidator.reset().parameter(LoanApiConstants.CAPITALIZED_INCOME_AMOUNT_PARAM).value(capitalizedAmount)
                    .zeroOrPositiveAmount();

            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                        dataValidationErrors);
            }
        }
    }
}
