package org.puti.gift.app.support.account;

import java.util.List;

record BatchExecutionResult(
        List<BatchSuccess> successes,
        List<BatchFailure> failures
) {
    static BatchExecutionResult allSuccess(List<BatchSuccess> successes) {
        return new BatchExecutionResult(successes, List.of());
    }
}

record BatchSuccess(
        String requestId,
        String orderNo
) {
}

record BatchFailure(
        String requestId,
        String reason
) {
}
