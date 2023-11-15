package org.restheart.graphql.instrumentation;


import org.restheart.graphql.GraphQLQueryTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.ExecutionResult;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;

/**
 * Aborts the execution if the query time is greater than the specified queryTimeLimit.
 */
public class MaxQueryTimeInstrumentation extends SimplePerformantInstrumentation {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaxQueryTimeInstrumentation.class);

    private final int queryTimeLimit; // in ms

    /**
     * Creates a new instrumentation that aborts the execution if the query time is greater than the specified queryTimeLimit
     *
     * @param queryTimeLimit max allowed query time, otherwise execution will be aborted. Set queryTimeLimit <= 0 to disable it.
     */
    public MaxQueryTimeInstrumentation(int queryTimeLimit) {
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
            throw mkAbortException(this.queryTimeLimit);
        } else {
            LOGGER.trace("Fetching data for field '{}' initiated. Current elapsed query time: {}.", parameters.getExecutionStepInfo().getPath(), elapsed);
            return noOp();
        }
    }

    /**
     * Generate the exception with error message
     *
     * @param maxTime the maximum time allowed
     *
     * @return an instance of AbortExecutionException
     */
    protected AbortExecutionException mkAbortException(long maxTime) {
        return new GraphQLQueryTimeoutException("Maximum query time limit of " + maxTime + "ms exceeded");
    }

    private static record State(long startTime) implements InstrumentationState {
    }
}