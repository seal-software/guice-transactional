package com.hubspot.guice.transactional;

public interface TransactionSynchronization {

    enum Status {
        COMMITED, ROLLED_BACK;

        public boolean isCommited() {
            return equals(COMMITED);
        };

        public boolean isRolledBack() {
            return equals(ROLLED_BACK);
        };
    }

    void afterCompletion(Status status);

}
