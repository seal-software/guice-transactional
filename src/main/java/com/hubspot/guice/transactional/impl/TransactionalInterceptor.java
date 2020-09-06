package com.hubspot.guice.transactional.impl;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.InvalidTransactionException;
import javax.transaction.TransactionRequiredException;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.transaction.TransactionalException;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hubspot.guice.transactional.TransactionSynchronization;

public class TransactionalInterceptor implements MethodInterceptor {
  private static final Log logger = LogFactory.getLog(TransactionalInterceptor.class);
  private static final ThreadLocal<TransactionalConnection> TRANSACTION_HOLDER = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> IN_TRANSACTION = new ThreadLocal<Boolean>() {

    @Override
    protected Boolean initialValue() {
      return false;
    }
  };

  private static final ThreadLocal<List<TransactionSynchronization>> SYNCHRONIZATIONS = ThreadLocal.withInitial(() -> new ArrayList<>());

  public static boolean inTransaction() {
    return IN_TRANSACTION.get();
  }

  public static TransactionalConnection getTransaction() {
    return TRANSACTION_HOLDER.get();
  }

  public static void setTransaction(TransactionalConnection transaction) {
    TRANSACTION_HOLDER.set(transaction);
  }

  public static void registerSynchronization(TransactionSynchronization synchronization) {
      SYNCHRONIZATIONS.get().add(synchronization);
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Transactional annotation = invocation.getMethod().getAnnotation(Transactional.class);
    TxType transactionType = annotation.value();

    boolean oldInTransaction = IN_TRANSACTION.get();
    TransactionalConnection oldTransaction = TRANSACTION_HOLDER.get();
    List<TransactionSynchronization> oldSyncronizations = SYNCHRONIZATIONS.get();
    boolean completeTransaction = false;

    if (IN_TRANSACTION.get()) {
      switch (transactionType) {
        case REQUIRES_NEW:
          TRANSACTION_HOLDER.set(null);
          SYNCHRONIZATIONS.set(new ArrayList<>());
          completeTransaction = true;
          break;
        case NOT_SUPPORTED:
          IN_TRANSACTION.set(false);
          TRANSACTION_HOLDER.set(null);
          SYNCHRONIZATIONS.set(new ArrayList<>());
          completeTransaction = true;
          break;
        case NEVER:
          throw new TransactionalException("Transaction is not allowed", new InvalidTransactionException());
      }
    } else {
      switch (transactionType) {
        case REQUIRED:
        case REQUIRES_NEW:
          IN_TRANSACTION.set(true);
          completeTransaction = true;
          break;
        case MANDATORY:
          throw new TransactionalException("Transaction is required", new TransactionRequiredException());
      }
    }

    if (!completeTransaction) {
      return invocation.proceed();
    } else {
      try {
        Object returnValue = invocation.proceed();
        TransactionalConnection transaction = TRANSACTION_HOLDER.get();
        if (transaction != null) {
          transaction.commit();
          invokeAfterCompletion(TransactionSynchronization.Status.COMMITED);
        }
        return returnValue;
      } catch (Throwable t) {
        TransactionalConnection transaction = TRANSACTION_HOLDER.get();
        if (transaction != null) {
          if (shouldRollback(annotation, t)) {
            transaction.rollback();
            invokeAfterCompletion(TransactionSynchronization.Status.ROLLED_BACK);
          } else {
            transaction.commit();
            invokeAfterCompletion(TransactionSynchronization.Status.COMMITED);
          }
        }
        throw t;
      } finally {
        try {
          TransactionalConnection transaction = TRANSACTION_HOLDER.get();
          if (transaction != null) {
            transaction.reallyClose();
          }
        } finally {
          IN_TRANSACTION.set(oldInTransaction);
          TRANSACTION_HOLDER.set(oldTransaction);
          SYNCHRONIZATIONS.set(oldSyncronizations);
        }
      }
    }
  }

  private boolean shouldRollback(Transactional annotation, Throwable t) {
    for (Class<?> dontRollback : annotation.dontRollbackOn()) {
      if (dontRollback.isAssignableFrom(t.getClass())) {
        return false;
      }
    }

    return true;
  }

  private void invokeAfterCompletion(TransactionSynchronization.Status status) {
      invokeAfterCompletion(SYNCHRONIZATIONS.get(), status);
  }

  private void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, TransactionSynchronization.Status status) {
      if (synchronizations != null) {
          for (TransactionSynchronization synchronization : synchronizations) {
              try {
                  synchronization.afterCompletion(status);
              } catch (Throwable tsex) {
                  logger.error("TransactionSynchronization.afterCompletion threw exception", tsex);
              }
          }
      }
  }
}
