package org.nowstart.lotto.data.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.nowstart.lotto.data.entity.UserEntity;

@Getter
@Builder
@ToString
public class TaskResult {
    private UserEntity user;
    private boolean success;
    private String message;
    private List<ResultDto> results;
    private String failureSubject;
}
