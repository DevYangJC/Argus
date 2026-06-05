package com.argus.rag.qa.mapper;

import com.argus.rag.qa.model.entity.QaRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** QA 问答记录 Mapper，包含可见性过滤查询。 */
@Mapper
public interface QaRecordMapper extends BaseMapper<QaRecordEntity> {

    /** 分页查询当前用户可见的 QA 记录。 */
    List<QaRecordEntity> selectVisibleRecords(
            @Param("currentUserId") Long currentUserId,
            @Param("systemAdmin") boolean systemAdmin,
            @Param("groupId") Long groupId,
            @Param("answered") Boolean answered,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    /** 统计当前用户可见的 QA 记录总数。 */
    Long countVisibleRecords(
            @Param("currentUserId") Long currentUserId,
            @Param("systemAdmin") boolean systemAdmin,
            @Param("groupId") Long groupId,
            @Param("answered") Boolean answered
    );

    /** 查询当前用户可见的单条 QA 记录详情。 */
    QaRecordEntity selectVisibleRecord(
            @Param("recordId") Long recordId,
            @Param("currentUserId") Long currentUserId,
            @Param("systemAdmin") boolean systemAdmin
    );
}
