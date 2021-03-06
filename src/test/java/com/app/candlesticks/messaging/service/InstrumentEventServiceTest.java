package com.app.candlesticks.messaging.service;

import com.app.candlesticks.entity.Instrument;
import com.app.candlesticks.entity.Quote;
import com.app.candlesticks.messaging.repository.InstrumentRepository;
import com.app.candlesticks.messaging.repository.QuoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.List;

import static com.app.candlesticks.TestingData.*;

@DataMongoTest
class InstrumentEventServiceTest {

    @MockBean
    WebSocketSession instrumentStream;

    InstrumentEventService instrumentEventService;

    ObjectMapper mapper;

    @Autowired
    QuoteRepository quoteRepository;

    @Autowired
    InstrumentRepository instrumentRepository;

    @BeforeEach
    void setUp() {
        instrumentEventService = new InstrumentEventService();
        instrumentEventService.setInstrumentRepository(instrumentRepository);
        instrumentEventService.setQuoteRepository(quoteRepository);
        mapper = new ObjectMapper();
        instrumentEventService.setMapper(mapper);
    }

    @AfterEach
    void tearDown() {
        instrumentRepository.deleteAll();
        quoteRepository.deleteAll();
    }

    @Test
    @DisplayName("" +
            "Save an Instrument with a given ISIN into an empty MongoDb. " +
            "Save 2 Quotes with same ISIN. " +
            "Delete the instrument." +
            "The Quotes must be removed as well")
     void saveOneInstrumentAnd2QuotesWithSameIsin_removeTheInstrument_quotesMustBeRemovedAsWell() throws
            JsonProcessingException {
        final String isin = "AAA111111";

        //given empty db
        List<Instrument> instruments = instrumentRepository.findAll();
        Assertions.assertThat(instruments).isEmpty();

        List<Quote> quotes = quoteRepository.findAll();
        Assertions.assertThat(quotes).isEmpty();

        //save one instrument, expected: the db will contain one item
        Instrument instrument = new Instrument(isin, "description");
        instrumentRepository.save(instrument);
        instruments = instrumentRepository.findAll();
        Assertions.assertThat(instruments.size()).isEqualTo(1);

        //save the updated item(with same Isin), expected: the db will contain only the last saved item.
        Quote quote = new Quote(isin, 10.0000, LocalDateTime.now());
        Quote secondQuote = new Quote(isin, 11.0000, LocalDateTime.now());
        quoteRepository.save(quote);
        quoteRepository.save(secondQuote);
        quotes = quoteRepository.findAll();
        Assertions.assertThat(quotes.size()).isEqualTo(2);

        //delete the instrument. Expected: both instrument and quote MongoDb collections will be empty
        instrumentEventService.handleEvent(String.format(INSTRUMENT_DELETE_EVENT_MESSAGE_TEMPLATE, isin));
        quotes = quoteRepository.findAll();
        Assertions.assertThat(quotes).isEmpty();
        instruments = instrumentRepository.findAll();
        Assertions.assertThat(instruments).isEmpty();
    }


    @Test
    @DisplayName("Save two Instruments with same ISIN, but different description to the empty MongoDB. " +
            "The DB must contain only the last item")
     void saveOneInstrumentWithSameIsinTwiceToEmptyDb_dbMustContainOneItemAfter() throws JsonProcessingException {
        final String isin = "AAA111111";

        //given empty db
        List<Instrument> instruments = instrumentRepository.findAll();
        Assertions.assertThat(instruments).isEmpty();

        //save one item, expected: the db will contain one item
        instrumentEventService.handleEvent(String.format(INSTRUMENT_ADD_EVENT_MESSAGE_TEMPLATE, "description", isin));
        instruments = instrumentRepository.findAll();
        Assertions.assertThat(instruments.size()).isEqualTo(1);

        instrumentEventService.handleEvent(String.format(INSTRUMENT_ADD_EVENT_MESSAGE_TEMPLATE, "updated description", isin));
        instruments = instrumentRepository.findAll();
        Assertions.assertThat(instruments.size()).isEqualTo(1);
        Assertions.assertThat(instruments.get(0).getDescription()).isEqualTo("updated description");
    }

}