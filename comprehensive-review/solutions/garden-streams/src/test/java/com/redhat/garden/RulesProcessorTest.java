package com.redhat.garden;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.garden.entities.Sensor;
import com.redhat.garden.entities.SensorMeasurement;
import com.redhat.garden.entities.SensorMeasurementEnriched;
import com.redhat.garden.entities.SensorMeasurementType;
import com.redhat.garden.events.DryConditionsDetected;
import com.redhat.garden.events.StrongWindDetected;
import com.redhat.garden.events.LowTemperatureDetected;

import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;


public class RulesProcessorTest {

    TopologyTestDriver testDriver;

    TestInputTopic<Integer, Sensor> sensorsTopic;
    ObjectMapperSerde<Sensor> sensorSerde;

    TestInputTopic<Integer, SensorMeasurementEnriched> enrichedMeasurementsTopic;
    ObjectMapperSerde<SensorMeasurementEnriched> sensorMeasurementSerde;

    TestOutputTopic<Integer, LowTemperatureDetected> lowTemperatureEventsTopic;
    ObjectMapperSerde<LowTemperatureDetected> lowTemperatureEventSerde;

    TestOutputTopic<Integer, DryConditionsDetected> dryConditionsEventsTopic;
    ObjectMapperSerde<DryConditionsDetected> dryConditionsEventSerde;

    TestOutputTopic<Integer, StrongWindDetected> strongWindEventsTopic;
    ObjectMapperSerde<StrongWindDetected> strongWindEventSerde;


    @BeforeEach
    public void setup() {
        RulesProcessor processor = new RulesProcessor();
        testDriver = new TopologyTestDriver(processor.getTopology());

        sensorMeasurementSerde = new ObjectMapperSerde<>(SensorMeasurementEnriched.class);
        enrichedMeasurementsTopic = testDriver.createInputTopic(
                    RulesProcessor.ENRICHED_SENSOR_MEASUREMENTS_TOPIC,
                    new IntegerSerializer(),
                    sensorMeasurementSerde.serializer());

        lowTemperatureEventSerde = new ObjectMapperSerde<>(LowTemperatureDetected.class);
        lowTemperatureEventsTopic = testDriver.createOutputTopic(
            RulesProcessor.LOW_TEMPERATURE_EVENTS_TOPIC,
            new IntegerDeserializer(),
            lowTemperatureEventSerde.deserializer());

        dryConditionsEventSerde = new ObjectMapperSerde<>(DryConditionsDetected.class);
        dryConditionsEventsTopic = testDriver.createOutputTopic(
            RulesProcessor.LOW_HUMIDITY_EVENTS_TOPIC,
            new IntegerDeserializer(),
            dryConditionsEventSerde.deserializer());

        strongWindEventSerde = new ObjectMapperSerde<>(StrongWindDetected.class);
        strongWindEventsTopic = testDriver.createOutputTopic(
            RulesProcessor.STRONG_WIND_EVENTS_TOPIC,
            new IntegerDeserializer(),
            strongWindEventSerde.deserializer());
    }

    @AfterEach
    public void teardown() {
        testDriver.close();
        sensorMeasurementSerde.close();
        lowTemperatureEventSerde.close();
        dryConditionsEventSerde.close();
        strongWindEventSerde.close();
    }

    @Test
    public void testLowTemperatureConditions() {
        // Given
        SensorMeasurementEnriched measurement = new SensorMeasurementEnriched(
            new SensorMeasurement(1, SensorMeasurementType.TEMPERATURE, 4.5, 10L),
            new Sensor(1, "Sensor 1", "Garden 1"));

        // When
        enrichedMeasurementsTopic.pipeInput(measurement.sensorId, measurement);

        // Then
        TestRecord<Integer, LowTemperatureDetected> record = lowTemperatureEventsTopic.readRecord();
        LowTemperatureDetected event = record.getValue();
        assertEquals(4.5, event.value);
    }

    @Test
    public void testGoodTemperatureConditions() {
        // Given
        SensorMeasurementEnriched measurement = new SensorMeasurementEnriched(
            new SensorMeasurement(1, SensorMeasurementType.TEMPERATURE, 20.0, 10L),
            new Sensor(1, "Sensor 1", "Garden 1"));

        // When
        enrichedMeasurementsTopic.pipeInput(measurement.sensorId, measurement);

        // Then
        assertTrue(lowTemperatureEventsTopic.isEmpty());
    }

    @Test
    public void testDryConditions() {
        // Given
        SensorMeasurementEnriched measurement = new SensorMeasurementEnriched(
            new SensorMeasurement(1, SensorMeasurementType.HUMIDITY, 0.1, 10L),
            new Sensor(1, "Sensor 1", "Garden 1"));

        // When
        enrichedMeasurementsTopic.pipeInput(measurement.sensorId, measurement);

        // Then
        TestRecord<Integer, DryConditionsDetected> record = dryConditionsEventsTopic.readRecord();
        DryConditionsDetected event = record.getValue();
        assertEquals(0.1, event.value);
    }

    @Test
    public void testGoodHumidityConditions() {
        // Given
        SensorMeasurementEnriched measurement = new SensorMeasurementEnriched(
            new SensorMeasurement(1, SensorMeasurementType.HUMIDITY, 0.8, 10L),
            new Sensor(1, "Sensor 1", "Garden 1"));

        // When
        enrichedMeasurementsTopic.pipeInput(measurement.sensorId, measurement);

        // Then
        assertTrue(dryConditionsEventsTopic.isEmpty());
    }

    @Test
    public void testStrongWindConditions() {
        // Given
        SensorMeasurementEnriched measurement = new SensorMeasurementEnriched(
            new SensorMeasurement(1, SensorMeasurementType.WIND, 15.0, 10L),
            new Sensor(1, "Sensor 1", "Garden 1"));

        // When
        enrichedMeasurementsTopic.pipeInput(measurement.sensorId, measurement);

        // Then
        TestRecord<Integer, StrongWindDetected> record = strongWindEventsTopic.readRecord();
        StrongWindDetected event = record.getValue();
        assertEquals(15.0, event.value);
    }

    @Test
    public void testCalmWindConditions() {
        // Given
        SensorMeasurementEnriched measurement = new SensorMeasurementEnriched(
            new SensorMeasurement(1, SensorMeasurementType.WIND, 3.0, 10L),
            new Sensor(1, "Sensor 1", "Garden 1"));

        // When
        enrichedMeasurementsTopic.pipeInput(measurement.sensorId, measurement);

        // Then
        assertTrue(dryConditionsEventsTopic.isEmpty());
    }

}
