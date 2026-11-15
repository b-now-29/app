package com.b.now._29.app.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 두 요청의 트랜잭션 경합을 재현하는 테스트 도우미.
 *
 * <p>{@link #holdFirst}로 먼저 시작한 트랜잭션은 작업을 마친 뒤 커밋하지 않고 {@link #releaseFirst}까지 기다린다. 그동안 {@link #submit}으로
 * 보낸 요청이 블록되는지 {@link #assertBlocked}로 확인한다. 테스트 클래스의 필드로 두고 {@code @AfterEach}에서 {@link #close}한다.
 */
public final class TransactionRace implements AutoCloseable {

    /** 테스트가 멈추지 않도록 모든 대기에 두는 상한. */
    public static final long TIMEOUT_SECONDS = 10;

    /** 뒤에 시작한 요청이 이 시간 안에 끝나면 블록되지 않은 것으로 본다. */
    private static final long BLOCKED_MILLIS = 500;

    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    public TransactionRace(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    /** 한 트랜잭션 안에서 작업을 마친 뒤, 커밋하지 않고 {@link #releaseFirst}까지 기다린다. 작업을 마칠 때까지 기다렸다가 돌아온다. */
    public Future<?> holdFirst(Runnable work) throws Exception {
        Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            work.run();
            firstDone.countDown();
            awaitRelease();
        }));
        if (!firstDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new AssertionError("먼저 시작한 트랜잭션이 작업을 마치지 못했다");
        }
        return first;
    }

    /** 다른 스레드에서 작업을 시작한다. */
    public <T> Future<T> submit(Callable<T> work) {
        return executor.submit(work);
    }

    /** 다른 스레드에서 결과가 없는 작업을 시작한다. */
    public Future<?> submit(Runnable work) {
        return executor.submit(work);
    }

    /** {@link #holdFirst}로 붙잡아 둔 트랜잭션을 커밋하게 한다. */
    public void releaseFirst() {
        release.countDown();
    }

    /** 먼저 시작한 트랜잭션이 커밋하기 전이라 {@code later}가 끝나지 않았는지 확인한다. */
    public void assertBlocked(Future<?> later) {
        assertThatThrownBy(() -> later.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS))
                .as("먼저 시작한 트랜잭션이 커밋하기 전에는 끝나지 않아야 한다")
                .isInstanceOf(TimeoutException.class);
    }

    /** 붙잡아 둔 트랜잭션을 놓아주고 스레드를 정리한다. */
    @Override
    public void close() throws InterruptedException {
        releaseFirst();
        executor.shutdown();
        if (!executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private void awaitRelease() {
        try {
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
