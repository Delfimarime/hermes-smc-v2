package com.raitonbl.hermes.smsc.camel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.config.ConfigurationUtils;
import com.raitonbl.hermes.smsc.config.health.CircuitBreakerConfig;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.camel.component.smpp.SmppSplittingPolicy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmppConnectionDefinition extends Entity {
    @JsonProperty("name")
    private String name;
    @JsonProperty("alias")
    private String alias;
    @JsonProperty("description")
    private String description;
    @JsonProperty("tags")
    private Map<String, String> tags;
    @JsonProperty("spec")
    private SmppConfiguration spec;

    @Builder
    public SmppConnectionDefinition(String id, Long version, String name, String alias, String description, Map<String, String> tags, SmppConfiguration spec) {
        super(id, version);
        this.name = name;
        this.alias = alias;
        this.description = description;
        this.tags = tags;
        this.spec = spec;
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SmppConfiguration implements Cloneable {
        @JsonProperty("redirect_to")
        public @NotNull String redirectTo;
        @JsonProperty("npi")
        private Byte npi;
        @JsonProperty("connection_type")
        private @NotNull SmppConnectionType smppConnectionType;
        @JsonProperty("port")
        private String port;
        @JsonProperty("verify_tls")
        private Boolean verifyTls;
        @JsonProperty("username")
        private String username;
        @JsonProperty("password")
        private String password;
        @JsonProperty("hostname")
        private @NotNull @NotEmpty String hostname;
        @JsonProperty("system_id")
        private @NotNull @NotEmpty String systemId;
        @JsonProperty("destination_address")
        private String destAddr;
        @JsonProperty("encoding")
        private String encoding;
        @JsonProperty("alphabet")
        private Alphabet alphabet;
        @JsonProperty("system_type")
        private String systemType;
        @JsonProperty("source_address")
        private String sourceAddr;
        @JsonProperty("data_coding")
        private Byte dataCoding;
        @JsonProperty("destination_address_npi")
        private Byte destAddrNpi;
        @JsonProperty("destination_address_ton")
        private Byte destAddrTon;
        @JsonProperty("address_range")
        private String addressRange;
        @JsonProperty("delivery_type")
        private RegisteredDelivery deliveryType;
        @JsonProperty("max_reconnect")
        private Integer maxReconnect;
        @JsonProperty("source_address_npi")
        private Byte sourceAddrNpi;
        @JsonProperty("source_address_ton")
        private Byte sourceAddrTon;
        @JsonProperty("reconnect_delay")
        private Long reconnectDelay;
        @JsonProperty("splitting_policy")
        private SmppSplittingPolicy splittingPolicy;
        @JsonProperty("enquire_link_timer")
        private Integer enquireLinkTimer;
        @JsonProperty("transaction_timer")
        private Integer transactionTimer;
        @JsonProperty("number_of_write_queues")
        private Integer numberOfWriteQueues;
        @JsonProperty("number_of_read_threads")
        private Integer numberOfReadThreads;
        @JsonProperty("wait_single_delivery_report")
        private Boolean singleDeliveryReport;
        @JsonProperty("initial_reconnect_delay")
        private Long initialReconnectDelay;
        @JsonProperty("circuit_breaker")
        private CircuitBreakerConfig circuitBreakerConfig;

        public SmppConfiguration() {
        }

        public SmppConfiguration(SmppConfiguration from) {
            if (from == null) {
                return;
            }
            this.npi = from.npi;
            this.port = from.port;
            this.verifyTls = from.verifyTls;
            this.username = from.username;
            this.password = from.password;
            this.hostname = from.hostname;
            this.systemId = from.systemId;
            this.destAddr = from.destAddr;
            this.encoding = from.encoding;
            this.alphabet = from.alphabet;
            this.systemType = from.systemType;
            this.sourceAddr = from.sourceAddr;
            this.dataCoding = from.dataCoding;
            this.destAddrNpi = from.destAddrNpi;
            this.destAddrTon = from.destAddrTon;
            this.addressRange = from.addressRange;
            this.deliveryType = from.deliveryType;
            this.maxReconnect = from.maxReconnect;
            this.sourceAddrNpi = from.sourceAddrNpi;
            this.sourceAddrTon = from.sourceAddrTon;
            this.reconnectDelay = from.reconnectDelay;
            this.splittingPolicy = from.splittingPolicy;
            this.enquireLinkTimer = from.enquireLinkTimer;
            this.transactionTimer = from.transactionTimer;
            this.smppConnectionType = from.smppConnectionType;
            this.numberOfWriteQueues = from.numberOfWriteQueues;
            this.numberOfReadThreads = from.numberOfReadThreads;
            this.singleDeliveryReport = from.singleDeliveryReport;
            this.initialReconnectDelay = from.initialReconnectDelay;
        }

        public String toCamelURI() {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            StringBuilder sb = new StringBuilder();
            sb.append("smpp://");
            if (username != null) {
                sb.append(username).append("@");
            }
            sb.append(hostname);
            if (port != null) {
                sb.append(":").append(port);
            }
            //TEMPORARY
            ConfigurationUtils.setParameter(sb, isFirst, "password", getPassword());

            ConfigurationUtils.setParameter(sb, isFirst, "npi", npi);
            ConfigurationUtils.setParameter(sb, isFirst, "encoding", encoding);
            ConfigurationUtils.setParameter(sb, isFirst, "systemId", systemId);
            ConfigurationUtils.setParameter(sb, isFirst, "dataCoding", dataCoding);
            ConfigurationUtils.setParameter(sb, isFirst, "systemType", systemType);
            ConfigurationUtils.setParameter(sb, isFirst, "sourceAddr", sourceAddr);
            ConfigurationUtils.setParameter(sb, isFirst, "sourceAddrNpi", sourceAddrNpi);
            ConfigurationUtils.setParameter(sb, isFirst, "sourceAddrTon", sourceAddrTon);
            ConfigurationUtils.setParameter(sb, isFirst, "maxReconnect", maxReconnect);
            ConfigurationUtils.setParameter(sb, isFirst, "useSSL", verifyTls, true);
            ConfigurationUtils.setParameter(sb, isFirst, "reconnectDelay", reconnectDelay);
            ConfigurationUtils.setParameter(sb, isFirst, "transactionTimer", transactionTimer);
            ConfigurationUtils.setParameter(sb, isFirst, "initialReconnectDelay", initialReconnectDelay);
            ConfigurationUtils.setParameter(sb, isFirst, "alphabet", alphabet, null, v -> v.value);

            boolean isReceiver = this.smppConnectionType.equals(SmppConnectionType.RECEIVER);
            boolean isTransceiver = this.smppConnectionType.equals(SmppConnectionType.TRANSCEIVER);
            boolean isTransmitter = this.smppConnectionType.equals(SmppConnectionType.TRANSMITTER);

            if (isReceiver || isTransceiver) {
                ConfigurationUtils.setParameter(sb, isFirst, "addressRange", addressRange);
                ConfigurationUtils.setParameter(sb, isFirst, "enquireLinkTimer", enquireLinkTimer);
                ConfigurationUtils.setParameter(sb, isFirst, "pduProcessorDegree", numberOfReadThreads);
                ConfigurationUtils.setParameter(sb, isFirst, "pduProcessorQueueCapacity", numberOfWriteQueues);
            }
            if (isTransmitter || isTransceiver) {
                ConfigurationUtils.setParameter(sb, isFirst, "destAddr", destAddr);
                ConfigurationUtils.setParameter(sb, isFirst, "destAddrNpi", destAddrNpi);
                ConfigurationUtils.setParameter(sb, isFirst, "destAddrTon", destAddrTon);
                ConfigurationUtils.setParameter(sb, isFirst, "singleDeliveryReport", singleDeliveryReport, true);
                ConfigurationUtils.setParameter(sb, isFirst, "splittingPolicy", splittingPolicy, SmppSplittingPolicy.REJECT);
                ConfigurationUtils.setParameter(sb, isFirst, "registeredDelivery", deliveryType,RegisteredDelivery.ALWAYS, RegisteredDelivery::getValue);
            }
            if (isTransceiver) {
                ConfigurationUtils.setParameter(sb, isFirst, "messageReceiverRouteId", Optional.of(redirectTo));
            }
            return sb.toString();
        }

        @Override
        public SmppConfiguration clone() {
            return new SmppConfiguration(this);
        }


    }

    public enum SmppConnectionType {
        TRANSMITTER, TRANSCEIVER, RECEIVER;
    }

    public enum RegisteredDelivery {
        NO_DELIVERY, ALWAYS, ON_FAILURE;

        public byte getValue() {
            return (byte) this.ordinal();
        }

    }

    public enum Alphabet {
        DEFAULT_ALPHABET_4((byte) 0), EIGHT_BIT_ALPHABET((byte) 4), UCS2_ALPHABET((byte) 8);

        public final byte value;

        Alphabet(byte value) {
            this.value = value;
        }

    }

}
