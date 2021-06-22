package com.cisco.dsb.common.messaging;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class DhruvaResult<E,A> {

    private static class Success<E,A> extends DhruvaResult<E,A> {
        private final A value;
        private Success(A value){
            this.value = value;
        }

        @Override
        public <B> DhruvaResult<E, B> map(Function<A, B> f) {
            return new Success<>(f.apply(value));
        }

        @Override
        public <B> DhruvaResult<E, B> flatMap(Function<A, DhruvaResult<E, B>> f) {
            return f.apply(value);
        }

        @Override
        public A getOrElse(A defaultValue) {
            return value;
        }

        @Override
        public A getOrElse(Supplier<A> defaultValue) {
            return value;
        }


    }

    private static class Failure<E,A> extends DhruvaResult<E,A> {
        private final E value;
        private Failure(E value){
            this.value = value;
        }


        @Override
        public <B> DhruvaResult<E, B> map(Function<A, B> f) {
            return new Failure<>(value);
        }

        @Override
        public <B> DhruvaResult<E, B> flatMap(Function<A, DhruvaResult<E, B>> f) {
            return new Failure<>(value);
        }

        @Override
        public A getOrElse(A defaultValue) {
            return defaultValue;
        }

        @Override
        public A getOrElse(Supplier<A> defaultValue) {
            return defaultValue.get();
        }


    }

    public static <E,A> DhruvaResult<E,A> success(A value){
        return new Success<>(value);
    }

    public static <E,A> Failure<E,A> failure(E value){
        return new Failure<>(value);
    }

    public abstract <B> DhruvaResult<E,B> map(Function<A,B> f);

    public abstract <B> DhruvaResult<E,B> flatMap(Function<A, DhruvaResult<E,B>> f);

    public abstract  A getOrElse(A defaultValue);
    public abstract  A getOrElse(Supplier<A> defaultValue);

}


