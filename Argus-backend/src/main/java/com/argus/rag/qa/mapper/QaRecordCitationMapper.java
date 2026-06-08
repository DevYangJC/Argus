package com.argus.rag.qa.mapper;

import com.argus.rag.qa.model.entity.QaRecordCitationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** QA 引用快照 Mapper。 */
@Mapper
public interface QaRecordCitationMapper extends BaseMapper<QaRecordCitationEntity> {

    /** 按问答记录 ID 查询引用快照，保持保存顺序返回。 */
    List<QaRecordCitationEntity> selectByRecordId(@Param("recordId") Long recordId);

    /** Deletes citation snapshots for a QA record before removing the record itself. */
    int deleteByRecordId(@Param("recordId") Long recordId);
}
