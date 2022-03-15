package com.cisco.dsb.proxy.util;

import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a function that accepts two arguments and produces a result. This is the three-arity
 * specialization of {@link Function}.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a> whose functional method is
 * {@link #apply(Object, Object)}.
 *
 * @param <S> the type of the first argument to the function
 * @param <T> the type of the second argument to the function
 * @param <U> the type of the third argument to the function
 * @param <R> the type of the result of the function
 * @see Function
 * @since 1.8
 */
@FunctionalInterface
public interface QuadFunction<S, T, U, V, R> {

  /**
   * Applies this function to the given arguments.
   *
   * @param s the first function argument
   * @param t the second function argument
   * @param u the third function argument
   * @param v the third function argument
   * @return the function result
   */
  R apply(S s, T t, U u, V v) throws InternalProxyErrorException;

  /**
   * Returns a composed function that first applies this function to its input, and then applies the
   * {@code after} function to the result. If evaluation of either function throws an exception, it
   * is relayed to the caller of the composed function.
   *
   * @param <V> the type of output of the {@code after} function, and of the composed function
   * @param after the function to apply after this function is applied
   * @return a composed function that first applies this function and then applies the {@code after}
   *     function
   * @throws NullPointerException if after is null
   */
  default <W> QuadFunction<S, T, U, V, W> andThen(Function<? super R, ? extends W> after) {
    Objects.requireNonNull(after);
    return (S s, T t, U u, V v) -> after.apply(apply(s, t, u, v));
  }
}
