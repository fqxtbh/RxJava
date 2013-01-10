package org.rx.operations;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.rx.reactive.Observable;
import org.rx.reactive.Observer;
import org.rx.reactive.Subscription;

/**
 * An observable that wraps an observable of the same type and then enforces the semantics
 * expected of a well-behaved observable.
 * 
 * An observable that ensures onNext, onCompleted, or onError calls on its subscribers are
 * not interleaved, onCompleted and onError are only called once respectively, and no
 * onNext calls follow onCompleted and onError calls.
 * 
 * @param <T>
 *            The type of the observable sequence.
 */
/* package */class OperationSynchronize<T> extends Observable<T> {

    /**
     * Accepts an observable and wraps it in another observable which ensures that the resulting observable is well-behaved.
     * 
     * A well-behaved observable ensures onNext, onCompleted, or onError calls to its subscribers are
     * not interleaved, onCompleted and onError are only called once respectively, and no
     * onNext calls follow onCompleted and onError calls.
     * 
     * @param observable
     * @param <T>
     * @return
     */
    public static <T> Observable<T> synchronize(Observable<T> observable) {
        return new OperationSynchronize<T>(observable);
    }

    public OperationSynchronize(Observable<T> innerObservable) {
        this.innerObservable = innerObservable;
    }

    private Observable<T> innerObservable;
    private AtomicObserver<T> atomicObserver;

    public Subscription subscribe(Observer<T> Observer) {
        AtomicObservableSubscription subscription = new AtomicObservableSubscription();
        atomicObserver = new AtomicObserver<T>(Observer, subscription);
        subscription.setActual(innerObservable.subscribe(atomicObserver));
        return subscription;
    }

    public static class UnitTest {

        /**
         * Ensure onCompleted can not be called after an Unsubscribe
         */
        @Test
        public void testOnCompletedAfterUnSubscribe() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            ws.unsubscribe();
            t.sendOnCompleted();

            verify(w, times(1)).onNext("one");
            verify(w, never()).onCompleted();
        }

        /**
         * Ensure onNext can not be called after an Unsubscribe
         */
        @Test
        public void testOnNextAfterUnSubscribe() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            ws.unsubscribe();
            t.sendOnNext("two");

            verify(w, times(1)).onNext("one");
            verify(w, never()).onNext("two");
        }

        /**
         * Ensure onError can not be called after an Unsubscribe
         */
        @Test
        public void testOnErrorAfterUnSubscribe() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            ws.unsubscribe();
            t.sendOnError(new RuntimeException("bad"));

            verify(w, times(1)).onNext("one");
            verify(w, never()).onError(any(Exception.class));
        }

        /**
         * Ensure onNext can not be called after onError
         */
        @Test
        public void testOnNextAfterOnError() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            @SuppressWarnings("unused")
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            t.sendOnError(new RuntimeException("bad"));
            t.sendOnNext("two");

            verify(w, times(1)).onNext("one");
            verify(w, times(1)).onError(any(Exception.class));
            verify(w, never()).onNext("two");
        }

        /**
         * Ensure onCompleted can not be called after onError
         */
        @Test
        public void testOnCompletedAfterOnError() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            @SuppressWarnings("unused")
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            t.sendOnError(new RuntimeException("bad"));
            t.sendOnCompleted();

            verify(w, times(1)).onNext("one");
            verify(w, times(1)).onError(any(Exception.class));
            verify(w, never()).onCompleted();
        }

        /**
         * Ensure onNext can not be called after onCompleted
         */
        @Test
        public void testOnNextAfterOnCompleted() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            @SuppressWarnings("unused")
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            t.sendOnCompleted();
            t.sendOnNext("two");

            verify(w, times(1)).onNext("one");
            verify(w, never()).onNext("two");
            verify(w, times(1)).onCompleted();
            verify(w, never()).onError(any(Exception.class));
        }

        /**
         * Ensure onError can not be called after onCompleted
         */
        @Test
        public void testOnErrorAfterOnCompleted() {
            TestObservable t = new TestObservable(null);
            Observable<String> st = synchronize(t);

            @SuppressWarnings("unchecked")
            Observer<String> w = mock(Observer.class);
            @SuppressWarnings("unused")
            Subscription ws = st.subscribe(w);

            t.sendOnNext("one");
            t.sendOnCompleted();
            t.sendOnError(new RuntimeException("bad"));

            verify(w, times(1)).onNext("one");
            verify(w, times(1)).onCompleted();
            verify(w, never()).onError(any(Exception.class));
        }

        /**
         * A Observable that doesn't do the right thing on UnSubscribe/Error/etc in that it will keep sending events down the pipe regardless of what happens.
         */
        private static class TestObservable extends Observable<String> {

            Observer<String> observer = null;

            public TestObservable(Subscription s) {
            }

            /* used to simulate subscription */
            public void sendOnCompleted() {
                observer.onCompleted();
            }

            /* used to simulate subscription */
            public void sendOnNext(String value) {
                observer.onNext(value);
            }

            /* used to simulate subscription */
            public void sendOnError(Exception e) {
                observer.onError(e);
            }

            @Override
            public Subscription subscribe(final Observer<String> observer) {
                this.observer = observer;
                return new Subscription() {

                    @Override
                    public void unsubscribe() {
                        // going to do nothing to pretend I'm a bad Observable that keeps allowing events to be sent
                    }

                };
            }

        }
    }

}