package com.raitonbl.hermes.smsc.config.smpp;

import com.raitonbl.hermes.smsc.config.health.CircuitBreakerConfig;
import com.raitonbl.hermes.smsc.sdk.ConfigurationUtils;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.Setter;
import org.apache.camel.component.smpp.SmppSplittingPolicy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class SmppConfiguration implements Cloneable {
    @Null
    public String redirectTo;
    private Byte npi;
    @NotNull
    @NotEmpty
    private SmppConnectionType smppConnectionType;
    private String port;
    private Boolean verifyTls;
    private String username;
    private String password;
    @NotNull
    @NotEmpty
    private String hostname;
    @NotNull
    @NotEmpty
    private String systemId;
    private String destAddr;
    private String encoding;
    private Alphabet alphabet;
    private String systemType;
    private String sourceAddr;
    private Byte dataCoding;
    private Byte destAddrNpi;
    private Byte destAddrTon;
    private String addressRange;
    private RegisteredDelivery deliveryType;
    private Integer maxReconnect;
    private Byte sourceAddrNpi;
    private Byte sourceAddrTon;
    private Long reconnectDelay;
    private SmppSplittingPolicy splittingPolicy;
    private Integer enquireLinkTimer;
    private Integer transactionTimer;
    private Integer numberOfWriteQueue;
    private Integer numberOfReadThreads;
    private Boolean singleDeliveryReport;
    private Long initialReconnectDelay;
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
        this.numberOfWriteQueue = from.numberOfWriteQueue;
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
        ConfigurationUtils.setParameter(sb, isFirst, "alphabet", alphabet, null, Alphabet::getValue);

        boolean isReceiver = this.smppConnectionType.equals(SmppConnectionType.RECEIVER);
        boolean isTransceiver = this.smppConnectionType.equals(SmppConnectionType.TRANSCEIVER);
        boolean isTransmitter = this.smppConnectionType.equals(SmppConnectionType.TRANSMITTER);

        if (isReceiver || isTransceiver) {
            ConfigurationUtils.setParameter(sb, isFirst, "addressRange", addressRange);
            ConfigurationUtils.setParameter(sb, isFirst, "enquireLinkTimer", enquireLinkTimer);
            ConfigurationUtils.setParameter(sb, isFirst, "pduProcessorDegree", numberOfReadThreads);
            ConfigurationUtils.setParameter(sb, isFirst, "pduProcessorQueueCapacity", numberOfWriteQueue);
        }
        if (isTransmitter || isTransceiver) {
            ConfigurationUtils.setParameter(sb, isFirst, "destAddr", destAddr);
            ConfigurationUtils.setParameter(sb, isFirst, "destAddrNpi", destAddrNpi);
            ConfigurationUtils.setParameter(sb, isFirst, "destAddrTon", destAddrTon);
            ConfigurationUtils.setParameter(sb, isFirst, "singleDeliveryReport", singleDeliveryReport, true);
            ConfigurationUtils.setParameter(sb, isFirst, "splittingPolicy", splittingPolicy, SmppSplittingPolicy.REJECT);
            ConfigurationUtils.setParameter(sb, isFirst, "registeredDelivery", deliveryType, RegisteredDelivery.ALWAYS, RegisteredDelivery::getValue);
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
