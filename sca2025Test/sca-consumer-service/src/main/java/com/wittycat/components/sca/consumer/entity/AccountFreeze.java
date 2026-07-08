package com.wittycat.components.sca.consumer.entity;

import lombok.Data;

@Data
public class AccountFreeze {

    private String xid;

    private Integer userId;

    private Double freezeMoney;

    private Integer state;

    public static abstract class State {
        public final static int TRY = 0;
        public final static int CONFIRM = 1;
        public final static int CANCEL = 2;

    }

}
