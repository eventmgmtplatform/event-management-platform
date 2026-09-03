package com.eventmanagement.integration;

import org.apache.kafka.clients.admin.MemberAssignment;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationWorkerReadinessCheckTest {

    @Test
    void shouldDetectExpectedTopicAssignment() {

        MemberDescription member = memberWithTopics(
                "integration.commands"
        );

        assertTrue(
                IntegrationWorkerReadinessCheck
                        .hasTopicAssignment(
                                List.of(member),
                                "integration.commands"
                        )
        );
    }

    @Test
    void shouldRejectDifferentTopicAssignment() {

        MemberDescription member = memberWithTopics(
                "integration.results"
        );

        assertFalse(
                IntegrationWorkerReadinessCheck
                        .hasTopicAssignment(
                                List.of(member),
                                "integration.commands"
                        )
        );
    }

    @Test
    void shouldRejectEmptyMemberCollection() {

        assertFalse(
                IntegrationWorkerReadinessCheck
                        .hasTopicAssignment(
                                List.of(),
                                "integration.commands"
                        )
        );
    }

    private MemberDescription memberWithTopics(
            String... topics
    ) {

        Set<TopicPartition> partitions =
                java.util.Arrays.stream(topics)
                        .map(
                                topic ->
                                        new TopicPartition(
                                                topic,
                                                0
                                        )
                        )
                        .collect(
                                java.util.stream.Collectors.toSet()
                        );

        return new MemberDescription(
                "consumer-1",
                Optional.empty(),
                "client-1",
                "/127.0.0.1",
                new MemberAssignment(partitions)
        );
    }
}
