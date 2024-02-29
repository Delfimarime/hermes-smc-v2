package com.raitonbl.hermes.smsc.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Problem {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    public static Problem get(){
        return Problem.builder().detail("An error occurred during your request :(, " +
                "retry and if it persist contact the administrators")
                .instance(null).status(500).title("Something went wrong").type("/problems").build();
    }

}
