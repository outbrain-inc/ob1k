package com.outbrain.ob1k.concurrent;

import com.outbrain.ob1k.concurrent.handlers.ErrorHandler;
import com.outbrain.ob1k.concurrent.handlers.FutureErrorHandler;
import com.outbrain.ob1k.concurrent.handlers.FutureResultHandler;
import com.outbrain.ob1k.concurrent.handlers.FutureSuccessHandler;
import com.outbrain.ob1k.concurrent.handlers.ResultHandler;
import com.outbrain.ob1k.concurrent.handlers.SuccessHandler;

import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.outbrain.ob1k.concurrent.ComposableFutures.fromError;
import static com.outbrain.ob1k.concurrent.ComposableFutures.fromValue;
import static com.outbrain.ob1k.concurrent.ComposableFutures.schedule;

/**
 * <p>A base interface for all future implementation in the system.</p>
 * <p>
 * A composable future is a monadic construct that represents a (potentially long) computation that will eventually
 * create a value or an error.
 * </p>
 * <p>
 * Futures are created from a Producer that represents the long computation.
 * Futures can be either eager or lazy.
 * </p>
 * Eager futures activate the producer immediately and stores the result(or error) internally.
 * Lazy futures stores the producer and activate it upon consumption
 * </p>
 * <p>
 * Futures can be "continued" to create a flow of computations, each next step is activated upon the arrival of
 * the result on the previous future.
 * </p>
 * <p>
 * Futures can be activated by calling the consumed method that activates the future(in the case of a lazy one)
 * or just "waits"(using a callback) for the result(in case of an eager one)
 * </p>
 * <p>
 * For testing purposes or for usage inside a blocking context, the future result can be returned in a blocking
 * manner by calling the get method. however, in a non-blocking asynchronous environment
 * such as Ob1k <b>it should never be used.</b>
 * </p>
 *
 * @author marenzon, aronen
 */
public interface ComposableFuture<T> {

  /**
   * Continues a future with a handler that will be called only if the original future resulted with success
   * in case of an error the error is continued forward.
   *
   * @param mapper the continuation handler that returns a value or throws an exception.
   * @param <R>    the resulting future type.
   * @return return a new future that will produce the result either from the handler if successful or the original error.
   */
  <R> ComposableFuture<R> map(Function<? super T, ? extends R> mapper);

  /**
   * Continues a future with a handler that will be called only if the original future resulted with success
   * in case of an error the error is continues forward.
   *
   * @param mapper the continuation handler that returns a future
   * @param <R>    the resulting future type.
   * @return a new future that will produce the result either from the handler if successful or the original error.
   */
  <R> ComposableFuture<R> flatMap(Function<? super T, ? extends ComposableFuture<? extends R>> mapper);

  /**
   * Recovers future with a handler that will be called only if the original future failed
   * in case of success the original result is continued forward.
   *
   * @param recover the continuation handler that returns a value or throws an exception.
   * @return a new future that will produce the original successful value the the result of the handler.
   */
  ComposableFuture<T> recover(Function<Throwable, ? extends T> recover);

  /**
   * Recovers future with a handler that will be called only if the original future failed
   * in case of success the original result is continued forward.
   *
   * @param recover the continuation handler that returns a future
   * @return a new future that will produce the original successful value the the result of the handler.
   */
  ComposableFuture<T> recoverWith(Function<Throwable, ? extends ComposableFuture<? extends T>> recover);

  /**
   * Continues a future with a handler that will be called whether the future has resulted
   * in a successful value or an error.
   *
   * @param handler the continuation handler that returns a value or throws an exception.
   * @param <R>     the resulting future type.
   * @return a new future that will produce the result from the handler.
   */
  <R> ComposableFuture<R> always(Function<Try<T>, ? extends R> handler);

  /**
   * Continues a future with a handler that will be called whether the future has resulted
   * in a successful value or an error.
   *
   * @param handler the continuation handler that returns a future
   * @param <R>     the resulting future type.
   * @return a new future that will produce the result from the handler.
   */
  <R> ComposableFuture<R> alwaysWith(Function<Try<T>, ? extends ComposableFuture<? extends R>> handler);

  /**
   * Applies the side-effecting function to the result of the current future,
   * and returns a new future with same value.
   *
   * @param consumer the result consumer for the current future result
   * @return a future with same value
   */
  ComposableFuture<T> andThen(Consumer<? super T> consumer);

  /**
   * On success of the current future, applies the side-effecting function to its result.
   * In case of an error the error is continued forward.
   * Returns a new future with the same value.
   *
   * @param consumer the result consumer for the current future result
   * @return a future with same value
   */
  ComposableFuture<T> peek(Consumer<? super T> consumer);

  /**
   * Transforms current future into a successful one regardless of its status, with a {@link Try} to represent
   * computation status (failure/success).
   * <p>
   * ComposableFuture[T](success/failure) => ComposableFuture[Try[T]](success)
   *
   * @return a new future of {@link Try[T]}, either Success or Failure depends on computation result.
   */
  default ComposableFuture<Try<T>> successful() {
    return always(__ -> __);
  }

  /**
   * Creates delayed future of current one, by provided duration.
   * Applied on successful result.
   *
   * @param duration duration to delay
   * @param unit time unit
   * @return delayed future
   */
  default ComposableFuture<T> delay(final long duration, final TimeUnit unit) {
    return flatMap(result -> schedule(() -> result, duration, unit));
  }

  /**
   * Ensures that the (successful) result of the current future satisfies the given predicate,
   * or fails with the given value.
   *
   * @param predicate the predicate for the result
   * @return new future with same value if predicate returns true, else new future with a failure
   */
  default ComposableFuture<T> ensure(final Predicate<? super T> predicate) {
    return flatMap(result -> {
      if (predicate.test(result)) {
        return fromValue(result);
      }
      return fromError(new NoSuchElementException("predicate is not satisfied"));
    });
  }

  /**
   * Consumes the value(or error) of the future into a consumer.
   * if the future is lazy the value will be reproduced on each consumption.
   * if the future is eager the consumer will be served from the cached result.
   *
   * @param consumer the consumer.
   */
  void consume(Consumer<? super T> consumer);

  /**
   * Blocks until a value is available for consumption and then return it.
   * in case of an error the exception is wrapped inside an ExecutionException and thrown.
   * <p>
   * DO NOT use in non-blocking environment.
   *
   * @return the future value if successful
   * @throws InterruptedException if the thread has been interrupted
   * @throws ExecutionException   if the future return an error.
   */
  default T get() throws InterruptedException, ExecutionException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Try<T>> resultBox = new AtomicReference<>();
    consume(result -> {
      resultBox.set(result);
      latch.countDown();
    });

    latch.await();
    final Try<T> result = resultBox.get();

    if (result == null) {
      throw new ExecutionException(new NullPointerException("no result"));
    }

    if (result.isSuccess()) {
      return result.getValue();
    }

    throw new ExecutionException(result.getError());
  }

  /**
   * Blocks until a value is available for consumption or until a timeout occurs, and then return the result or error.
   * in case of an error the exception is wrapped inside an ExecutionException and thrown.
   * <p>
   * DO NOT use in non-blocking environment.
   *
   * @param timeout max wait time for result.
   * @param unit    a time unit for the timeout duration
   * @return the result if successful
   * @throws InterruptedException if the thread has been interrupted
   * @throws ExecutionException   if the future return an error
   * @throws TimeoutException     if result(or error) haven't arrived in the specified time-span.
   */
  default T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final AtomicReference<Try<T>> resultBox = new AtomicReference<>();
    consume(result -> {
      resultBox.set(result);
      countDownLatch.countDown();
    });

    if (countDownLatch.await(timeout, unit)) {
      final Try<T> result = resultBox.get();

      if (result == null) {
        throw new ExecutionException(new NullPointerException("No result"));
      }

      if (result.isSuccess()) {
        return result.getValue();
      }

      throw new ExecutionException(result.getError());
    }

    throw new TimeoutException("Timeout occurred while waiting for a value");
  }

  /**
   * Turns the current future into an eager one.
   *
   * @return the new eager future.
   */
  ComposableFuture<T> materialize();

  /**
   * Caps the max time for producing a value(or error) for this future.
   * the returned future will return the original result if available within the specified time or a TimeoutException.
   *
   * @param duration max wait time for a result before producing a timeout
   * @param unit     the duration timeout.
   * @return the future with a caped time.
   */
  ComposableFuture<T> withTimeout(long duration, TimeUnit unit);

  /**
   * Caps the max time for producing a value(or error) for this future.
   * the returned future will return the original result if available within the specified time or a TimeoutException.
   *
   * @param scheduler scheduler to schedule timeout on
   * @param duration  max wait time for a result before producing a timeout
   * @param unit      the duration timeout.
   * @return the future with a caped time.
   */
  ComposableFuture<T> withTimeout(Scheduler scheduler, long duration, TimeUnit unit);

  /**
   * Caps the max time for producing a value(or error) for this future.
   * the returned future will return the original result if available within the specified time or a TimeoutException.
   *
   * @param duration        max wait time for a result before producing a timeout
   * @param unit            the duration timeout.
   * @param taskDescription a description that will be added to the timeout error message that will help identify
   *                        the context of the timeout
   * @return the future with a caped time.
   */
  ComposableFuture<T> withTimeout(long duration, TimeUnit unit, String taskDescription);

  /**
   * Caps the max time for producing a value(or error) for this future.
   * the returned future will return the original result if available within the specified time or a TimeoutException.
   *
   * @param scheduler       scheduler to schedule timeout on
   * @param duration        max wait time for a result before producing a timeout
   * @param unit            the duration timeout.
   * @param taskDescription a description that will be added to the timeout error message that will help identify
   *                        the context of the timeout
   * @return the future with a caped time.
   */
  ComposableFuture<T> withTimeout(Scheduler scheduler, long duration, TimeUnit unit, String taskDescription);






  /*
    -----------------------
    OLD API - DEPRECATED. PLEASE USE NEW ONE
    -----------------------
   */


  /**
   * Continues a future with a handler that will be called whether the future has resulted in a successful value or an error.
   *
   * @param handler the continuation handler that returns a future
   * @param <R>     the resulting future type.
   * @return a new future that will produce the result from the handler.
   */
  @Deprecated
  <R> ComposableFuture<R> continueWith(FutureResultHandler<T, R> handler);

  /**
   * continues a future with a handler that will be called whether the future has resulted in a successful value or an error.
   *
   * @param handler the continuation handler that returns a value or throws an exception.
   * @param <R>     the resulting future type.
   * @return a new future that will produce the result from the handler.
   */
  @Deprecated
  <R> ComposableFuture<R> continueWith(ResultHandler<T, R> handler);

  /**
   * continues a future with a handler that will be called only if the original future resulted with success
   * in case of an error the error is continues forward.
   *
   * @param handler the continuation handler that returns a future(a.k.a flatMap)
   * @param <R>     the resulting future type.
   * @return a new future that will produce the result either from the handler if successful or the original error.
   */
  @Deprecated
  <R> ComposableFuture<R> continueOnSuccess(FutureSuccessHandler<? super T, R> handler);

  /**
   * continues a future with a handler that will be called only if the original future resulted with success
   * in case of an error the error is continued forward.
   *
   * @param handler the continuation handler that returns a future(a.k.a map)
   * @param <R>     the resulting future type.
   * @return return a new future that will produce the result either from the handler if successful or the original error.
   */
  @Deprecated
  <R> ComposableFuture<R> continueOnSuccess(SuccessHandler<? super T, ? extends R> handler);

  /**
   * continues a future with a handler that will be called only if the original future failed
   * in case of success the original result is continued forward.
   *
   * @param handler the continuation handler that returns a future
   * @return a new future that will produce the original successful value the the result of the handler.
   */
  @Deprecated
  ComposableFuture<T> continueOnError(FutureErrorHandler<T> handler);

  /**
   * continues a future with a handler that will be called only if the original future failed
   * in case of success the original result is continued forward.
   *
   * @param handler the continuation handler that returns a value or throws an exception.
   * @return a new future that will produce the original successful value the the result of the handler.
   */
  @Deprecated
  ComposableFuture<T> continueOnError(ErrorHandler<? extends T> handler);

  @Deprecated
  <R> ComposableFuture<R> transform(com.google.common.base.Function<? super T, ? extends R> function);
}
