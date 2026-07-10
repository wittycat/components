package com.wittycat.components.sca.provider.service;

public interface ATProductService {
    /**
     * try逻辑
     */
    boolean deduct(
            Integer productId,
            Integer userId,
            Integer freezeNum);
}
