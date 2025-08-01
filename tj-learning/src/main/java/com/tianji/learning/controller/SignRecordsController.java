package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author: chake
 * @create 2025/7/24 10:40
 * @ClassName: SignRecordsController
 * @Package: com.tianji.learning.controller
 * @Description:
 * @Version 1.0
 */
@RestController
@Api(tags = "签到相关接口")
@RequiredArgsConstructor
@RequestMapping("/sign-records")
public class SignRecordsController {

    private final ISignRecordService signRecordService;

    @PostMapping
    @ApiOperation("签到")
    public SignResultVO addSignRecords() {
        return signRecordService.addSignRecords();
    }

    @GetMapping
    @ApiOperation("获取签到记录")
    public Byte[] getSignRecords() {
        return signRecordService.getSignRecords();
    }
}
