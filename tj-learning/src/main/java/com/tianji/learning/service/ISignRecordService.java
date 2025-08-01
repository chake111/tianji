package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

/**
 * @author: chake
 * @create 2025/7/24 10:44
 * @ClassName: ISignRecordService
 * @Package: com.tianji.learning.service
 * @Description:
 * @Version 1.0
 */

public interface ISignRecordService {
    SignResultVO addSignRecords();

    Byte[] getSignRecords();


}
