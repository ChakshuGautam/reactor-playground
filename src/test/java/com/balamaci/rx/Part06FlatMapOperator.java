package com.balamaci.rx;

import com.balamaci.rx.util.Helpers;
import javafx.util.Pair;
import org.junit.Test;
import rx.Observable;
import rx.observables.GroupedObservable;
import rx.schedulers.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The flatMap operator is so important and has so many different uses it deserves it's own category to explain
 * I like to think of it as a sort of fork-join operation because what flatMap does is it takes individual items
 * and maps each of them to an Observable(so it creates new Streams from each object) and then 'flattens' the
 * events from these Streams back into a single Stream.
 * Why this looks like fork-join because for each element you can fork some async jobs that emit some items before completing,
 * and these items are sent downstream to the subscribers as coming from a single stream
 *
 * RuleOfThumb 1: When you have an 'item' as parameter and you need to invoke something that returns an
 *                    Observable<T> instead of <T>, you need flatMap
 * RuleOfThumb 2: When you have Observable<Observable<T>> you probably need flatMap.
 *
 * @author sbalamaci
 */
public class Part06FlatMapOperator implements BaseTestObservables {

    /**
     * Common usecase when for each item you make an async remote call that returns a stream of items (an Observable<T>)
     *
     * The thing to notice that it's not clear upfront that events from the flatMaps 'substreams' don't arrive
     * in a guaranteed order and events from a substream might get interleaved with the events from other substreams.
     *
     */
    @Test
    public void flatMap() {
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> colors = Observable.just("orange", "red", "green")
                .flatMap(colorName -> simulateRemoteOperation(colorName));
        subscribeWithLog(colors, latch);

        Helpers.wait(latch);
    }

    /**
     * Inside the flatMap we can operate on the substream with the same stream operators like for ex count
     */
    @Test
    public void flatMapSubstreamOperations() {
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> colors = Observable.just("orange", "red", "green", "blue");

        Observable<Pair<String, Integer>> colorsCounted = colors
                .flatMap(colorName -> {
                    Observable<Long> timer = Observable.interval(2, TimeUnit.SECONDS);

                    return simulateRemoteOperation(colorName) // <- Still a stream
                                    .zipWith(timer, (val, timerVal) -> val)
                                    .count()
                                    .map(counter -> new Pair<>(colorName, counter));
                    }
                );

        subscribeWithLog(colorsCounted, latch);

        Helpers.wait(latch);
    }


    /**
     * Controlling the level of concurrency of the substreams.
     * In the ex. below, only the first two substreams(the Observables returned by simulateRemoteOperation)
     * are subscribed. As soon as one of them completes, another substream is subscribed.
     *
     */
    @Test
    public void flatMapConcurrency() {
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> colors = Observable.just("orange", "red", "green")
                .flatMap(val -> simulateRemoteOperation(val), 1);
        subscribeWithLog(colors, latch);

        Helpers.wait(latch);
    }

    /**
     * As seen above flatMap might mean that events emitted by multiple streams might get interleaved
     *
     * concatMap operator acts as a flatMap with 1 level of concurrency which means only one of the created
     * substreams(Observable) is subscribed and thus only one emits events so it's just this substream which
     * emits events until it finishes and a new one will be subscribed and so on
     */
    @Test
    public void concatMap() {
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> colors = Observable.just("orange", "red", "green", "blue")
                .subscribeOn(Schedulers.io())
                .concatMap(val -> simulateRemoteOperation(val));
        subscribeWithLog(colors, latch);

        Helpers.wait(latch);
    }

    /**
     * When you have a Stream of Streams - Observable<Observable<T>>
     */
    @Test
    public void flatMapFor() {
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> colors = Observable.from(new String[]{"red", "green", "blue",
                "red", "yellow", "green", "green"});

        Observable<GroupedObservable<String, String>> groupedColorsStream = colors
                                                                                .groupBy(val -> val);//grouping key
                                                                                // is the String itself, the color

        Observable<Pair<String, Integer>> countedColors = groupedColorsStream.flatMap(groupedObservable
                                        -> groupedObservable
                                                .count()
                                                .map(countVal -> new Pair<>(groupedObservable.getKey(), countVal))
                            );
        subscribeWithLog(countedColors, latch);
        Helpers.wait(latch);
    }

    /**
     * Simulated remote operation that emits as many events as the length of the color string
     * @param color color
     * @return
     */
    private Observable<String> simulateRemoteOperation(String color) {
        return Observable.<String>create(subscriber -> {
            Runnable asyncRun = () -> {
                for (int i = 0; i < color.length(); i++) {
                    subscriber.onNext(color + i);
                    Helpers.sleepMillis(200);
                }

                subscriber.onCompleted();
            };
            new Thread(asyncRun).start();
        });
    }

}
