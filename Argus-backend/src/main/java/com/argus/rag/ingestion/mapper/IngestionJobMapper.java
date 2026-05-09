package com.argus.rag.ingestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.argus.rag.ingestion.model.entity.IngestionJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link IngestionJobEntity} 的 MyBatis 映射器，继承 {@link BaseMapper} 获得通用 CRUD 能力。
 * <p>
 * 提供对 {@code ingestion_jobs} 表的原子状态切换操作（认领/成功/失败），
 * 以及可运行任务查询。通用 insert / selectById 由 BaseMapper 提供。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Mapper
public interface IngestionJobMapper extends BaseMapper<IngestionJobEntity> {

    /**
     * 查询处于指定状态且计划执行时间已到的可运行任务。
     *
     * @param status 任务状态，取 {@link com.argus.rag.common.enums.IngestionJobStatus} 枚举值
     * @param now    当前时间，用于与计划执行时间比较
     * @param limit  最大返回条数
     * @return 可运行的任务列表
     */
    List<IngestionJobEntity> selectRunnableJobs(
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * 原子认领任务：将任务从 {@code pendingStatus} 切换为 {@code runningStatus} 并绑定 {@code workerId}。
     *
     * @param jobId         任务 ID
     * @param pendingStatus 预期的待处理状态值
     * @param runningStatus 要切换到的运行中状态值
     * @param workerId      执行该任务的 worker 标识
     * @param startedAt     任务开始时间
     * @return 影响行数（0 表示任务已被其他 worker 认领）
     */
    int claimRunning(
            @Param("jobId") Long jobId,
            @Param("pendingStatus") String pendingStatus,
            @Param("runningStatus") String runningStatus,
            @Param("workerId") String workerId,
            @Param("startedAt") LocalDateTime startedAt
    );

    /**
     * 将运行中任务标记为成功。
     *
     * @param jobId            任务 ID
     * @param runningStatus    预期的运行中状态值
     * @param succeededStatus  要切换到的成功状态值
     * @param workerId         执行该任务的 worker 标识
     * @param finishedAt       任务完成时间
     * @return 影响行数
     */
    int markSucceeded(
            @Param("jobId") Long jobId,
            @Param("runningStatus") String runningStatus,
            @Param("succeededStatus") String succeededStatus,
            @Param("workerId") String workerId,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    /**
     * 将运行中任务标记为失败并记录错误信息。
     *
     * @param jobId          任务 ID
     * @param runningStatus  预期的运行中状态值
     * @param failedStatus   要切换到的失败状态值
     * @param workerId       执行该任务的 worker 标识
     * @param finishedAt     任务完成时间
     * @param lastError      错误信息
     * @return 影响行数
     */
    int markFailed(
            @Param("jobId") Long jobId,
            @Param("runningStatus") String runningStatus,
            @Param("failedStatus") String failedStatus,
            @Param("workerId") String workerId,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("lastError") String lastError
    );
}
