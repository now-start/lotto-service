package org.nowstart.lotto.service;

import jakarta.persistence.EntityManagerFactory;
import org.nowstart.lotto.data.entity.UserEntity;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.stereotype.Service;

@Service
public class TaskReader extends JpaPagingItemReader<UserEntity> {

    public TaskReader(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
        setName("taskReader");
        setQueryString("SELECT u FROM UserEntity u");
        setPageSize(10);
    }
}
