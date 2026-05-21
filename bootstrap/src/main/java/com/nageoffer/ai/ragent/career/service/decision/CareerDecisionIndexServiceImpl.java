package com.nageoffer.ai.ragent.career.service.decision;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.dao.entity.CareerDecisionIndexDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerDecisionIndexMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CareerDecisionIndexServiceImpl implements CareerDecisionIndexService {

    private static final int SUMMARY_MAX_LENGTH = 512;

    private final CareerDecisionIndexMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void record(CareerDecisionIndexCommand command) {
        if (command == null || StrUtil.isBlank(command.decisionKey())) {
            return;
        }
        CareerDecisionIndexDO record = CareerDecisionIndexDO.builder()
                .traceId(blankToNull(command.traceId()))
                .userId(blankToNull(command.userId()))
                .businessScene(blankToNull(command.businessScene()))
                .businessId(blankToNull(command.businessId()))
                .agentType(blankToNull(command.agentType()))
                .decisionType(blankToNull(command.decisionType()))
                .decisionKey(command.decisionKey())
                .decisionSummary(limit(command.decisionSummary(), SUMMARY_MAX_LENGTH))
                .inputRefJson(writeJson(command.inputRef()))
                .outputRefJson(writeJson(command.outputRef()))
                .build();
        mapper.insert(record);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Career decision index JSON serialization failed, storing empty ref", ex);
            return "{}";
        }
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
