package com.wittycat.components.sca.provider.entity;

import lombok.Data;

@Data
public class ProductFreeze {
    private String xid;//事务ID

    private Integer freezeNum;//冻结数量

    private Integer userId;//用户ID

    private Integer state;//冻结状态

    public static abstract class State {
        public final static int TRY = 0;
        public final static int CONFIRM = 1;
        public final static int CANCEL = 2;

    }

}
