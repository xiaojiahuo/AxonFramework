/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventPublicationOrderTest {

    private CommandBus commandBus;
    private EventStore eventStore;

    @Before
    public void setUp() {
        this.commandBus = new SimpleCommandBus();
        eventStore = spy(new EmbeddedEventStore(new InMemoryEventStorageEngine()));
        EventSourcingRepository<StubAggregate> repository =
                new EventSourcingRepository<>(StubAggregate.class, eventStore);
        StubAggregateCommandHandler target = new StubAggregateCommandHandler();
        target.setRepository(repository);
        target.setEventBus(eventStore);
        new AnnotationCommandHandlerAdapter(target).subscribe(commandBus);
    }

    @Test
    public void testPublicationOrderIsMaintained_AggregateAdded() {
        String aggregateId = UUID.randomUUID().toString();
        when(eventStore.readEvents(aggregateId)).thenReturn(DomainEventStream.of(new GenericDomainEventMessage<>("test",
                                                                                                                 aggregateId,
                                                                                                                 0,
                                                                                                                 new StubAggregateCreatedEvent(
                                                                                                                         aggregateId))));
        doAnswer(invocation -> {
            System.out.println("Published event: " + invocation.getArguments()[0].toString());
            return Void.class;
        }).when(eventStore).publish(isA(EventMessage.class));
        commandBus.dispatch(asCommandMessage(new UpdateStubAggregateWithExtraEventCommand(aggregateId)));
        verify(eventStore).publish(isA(DomainEventMessage.class), argThat(new NotADomainEventMatcher()),
                                   isA(DomainEventMessage.class));
    }

    private static class NotADomainEventMatcher extends BaseMatcher<EventMessage> {

        @Override
        public boolean matches(Object o) {
            return !(o instanceof DomainEventMessage);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Not a DomainEventMessage");
        }
    }
}
