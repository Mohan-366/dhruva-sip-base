package com.cisco.dhruva.application.calltype;

import com.cisco.dsb.common.messaging.DSIPMessage;
import reactor.core.publisher.Sinks;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface CallType {
    Predicate<DSIPMessage> filter();
    Consumer<DSIPMessage> process();
    Sinks.Many<DSIPMessage> getSink();
    //Can put common functions like addCallIdCallTypeMapping
    default Function<DSIPMessage,DSIPMessage> addCallIdCallTypeMapping(){
        return (sipMessage)->{
            //executionContext.
            //change to enum for calltype value
            sipMessage.getContext().put(sipMessage.getCorrelationId(), this);
            return sipMessage;
        };
    }
}
