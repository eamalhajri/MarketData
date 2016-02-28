package com.handson.rx;


import com.handson.dto.Quote;
import com.handson.infra.EventStreamClient;
import org.junit.Before;
import org.junit.Test;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.TestSubject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StockServerTest {

    private EventStreamClient stockEventStreamClient;
    private EventStreamClient forexEventStreamClient;
    private TestScheduler scheduler;
    private StockServer stockServer;
    private TestSubject<String> quoteSourceSubject;
    private TestSubject<String> forexSourceSubject;

    @Before
    public void setUpServer() {
        stockEventStreamClient = mock(EventStreamClient.class);
        forexEventStreamClient = mock(EventStreamClient.class);
        scheduler = Schedulers.test();
        stockServer = new StockServer(42, stockEventStreamClient, forexEventStreamClient);
        quoteSourceSubject = TestSubject.create(scheduler);
        when(stockEventStreamClient.readServerSideEvents()).thenReturn(quoteSourceSubject);
        forexSourceSubject = TestSubject.create(scheduler);
        when(forexEventStreamClient.readServerSideEvents()).thenReturn(forexSourceSubject);
    }

    @Test
    public void should_filter_quotes_for_requested_stock() {
        // given
        TestSubscriber<Quote> testSubscriber = new TestSubscriber<>();
        Map<String, List<String>> parameters
                = Collections.singletonMap("STOCK", Arrays.asList("GOOGLE"));
        stockServer.getEvents(parameters).subscribe(testSubscriber);
        // when
        quoteSourceSubject.onNext(new Quote("GOOGLE", 705.8673).toJson());
        forexSourceSubject.onNext(new Quote("EUR/USD", 1).toJson());
        quoteSourceSubject.onNext(new Quote("APPLE", 98.18).toJson());
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        // then
        List<Quote> events = testSubscriber.getOnNextEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).code).isEqualTo("GOOGLE");
    }

    @Test
    public void should_generate_one_quote_in_euro_for_one_quote_in_dollar() {
        // given
        TestSubscriber<Quote> testSubscriber = new TestSubscriber<>();
        Map<String, List<String>> parameters
                = Collections.singletonMap("STOCK", Arrays.asList("GOOGLE"));
        stockServer.getEvents(parameters).subscribe(testSubscriber);
        // when
        quoteSourceSubject.onNext(new Quote("GOOGLE", 1300).toJson());
        forexSourceSubject.onNext(new Quote("EUR/USD", 1.3).toJson());
        forexSourceSubject.onNext(new Quote("EUR/USD", 1.4).toJson());
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        // then
        List<Quote> events = testSubscriber.getOnNextEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).quote).isEqualTo(1000);
    }

    @Test
    public void should_generate_quotes_in_euro_using_latest_known_foreign_exchange_rate() {
        // given
        TestSubscriber<Quote> testSubscriber = new TestSubscriber<>();
        Map<String, List<String>> parameters
                = Collections.singletonMap("STOCK", Arrays.asList("GOOGLE"));
        stockServer.getEvents(parameters).subscribe(testSubscriber);
        // when
        forexSourceSubject.onNext(new Quote("EUR/USD", 1.3).toJson(), 90);
        quoteSourceSubject.onNext(new Quote("GOOGLE", 1300).toJson(), 100);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        // then
        List<Quote> events = testSubscriber.getOnNextEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).quote).isEqualTo(1000);
    }


}