package com.tianji.promotion.utils;

import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.exceptions.BizIllegalException;
import org.redisson.api.RLock;

public enum MyLockStrategy {
    SKIP_FAST(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(0, prop.leaseTime(), prop.unit());
        }
    },
    FAIL_FAST(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(0, prop.leaseTime(), prop.unit());
            if (!isLock) {
                throw new BizIllegalException(ErrorInfo.Msg.OPERATE_TOO_FREQUENTLY);
            }
            return true;
        }
    },
    KEEP_TRYING(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            lock.lock( prop.leaseTime(), prop.unit());
            return true;
        }
    },
    SKIP_AFTER_RETRY_TIMEOUT(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
        }
    },
    FAIL_AFTER_RETRY_TIMEOUT(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
            if (!isLock) {
                throw new BizIllegalException(ErrorInfo.Msg.OPERATE_TOO_FREQUENTLY);
            }
            return true;
        }
    },
    ;

    public abstract boolean tryLock(RLock lock, MyLock prop) throws InterruptedException;
}