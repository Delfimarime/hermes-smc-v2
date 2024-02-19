package com.raitonbl.hermes.smsc.camel;

import org.apache.camel.Processor;

public interface SmppConnectionDecider extends Processor {
    String TARGET_HEADER = "SmppDeciderTarget";
}
