package com.raitonbl.hermes.smsc.camel;

import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

@Component
public class DefaultSmppConnectionDecider implements SmppConnectionDecider{
    @Override
    public void process(Exchange exchange) {
        exchange.getIn().setHeader(TARGET_HEADER,"default");
    }
}
