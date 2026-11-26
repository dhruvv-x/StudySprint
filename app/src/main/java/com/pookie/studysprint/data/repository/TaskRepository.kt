package com.pookie.studysprint.data.repository

import com.pookie.studysprint.data.local.TaskDao
import com.pookie.studysprint.data.local.TaskEntity
import com.pookie.studysprint.domain.model.Priority
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {

    fun observeBySubject(subjectId: Long): Flow<List<TaskEntity>> =
        dao.observeBySubject(subjectId)

    fun observePending(): Flow<List<TaskEntity>> = dao.observePending()

    suspend fun getById(id: Long): TaskEntity? = dao.getById(id)

    suspend fun add(
        subjectId: Long,
        title: String,
        priority: Priority,
        estimatedMinutes: Int
    ): Long =
        dao.insert(
            TaskEntity(
                subjectId = subjectId,
                title = title.trim(),
                priority = priority,
                estimatedMinutes = estimatedMinutes,
                createdAt = System.currentTimeMillis()
            )
        )

    suspend fun update(task: TaskEntity) = dao.update(task)

    suspend fun delete(task: TaskEntity) = dao.delete(task)

    suspend fun setCompleted(id: Long, completed: Boolean) =
        dao.setCompleted(
            id = id,
            completed = completed,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
}