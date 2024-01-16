/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */

package org.restheart.graphql.instrumentation;

import org.restheart.graphql.GraphQLQueryTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;

/**
 * Aborts the execution if the query time is greater than the specified queryTimeLimit.
 */
public class MaxQueryTimeInstrumentation implements Instrumentation {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaxQueryTimeInstrumentation.class);

    private final long queryTimeLimit; // in ms

    /**
     * Creates a new instrumentation that aborts the execution if the query time is greater than the specified queryTimeLimit
     *
     * @param queryTimeLimit max allowed query time, otherwise execution will be aborted. Set queryTimeLimit <= 0 to disable it.
     */
    public MaxQueryTimeInstrumentation(long queryTimeLimit) {
        this.queryTimeLimit = queryTimeLimit;
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new State(System.currentTimeMillis());
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState rawState) {
        if (this.queryTimeLimit <= 0) {
            return noOp();
        }

        State state = InstrumentationState.ofState(rawState);
        var elapsed = System.currentTimeMillis() - state.startTime;

        if (elapsed > this.queryTimeLimit) {
            LOGGER.debug("Exceeded query time limit of {} while attempting to fetch field '{}'. Current query elapsed time: {}.", this.queryTimeLimit, parameters.getExecutionStepInfo().getPath(), elapsed);
            throw new GraphQLQueryTimeoutException("Maximum query time limit of " + this.queryTimeLimit + "ms exceeded");
        } else {
            LOGGER.trace("Fetching data for field '{}' initiated. Current elapsed query time: {}.", parameters.getExecutionStepInfo().getPath(), elapsed);
            return noOp();
        }
    }

    private static record State(long startTime) implements InstrumentationState {
    }
}