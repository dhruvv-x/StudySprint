package com.pookie.studysprint.data.repository

import com.pookie.studysprint.data.local.SubjectDao
import com.pookie.studysprint.data.local.SubjectEntity
import com.pookie.studysprint.data.local.SubjectWithCounts
import kotlinx.coroutines.flow.Flow

class SubjectRepository(private val dao: SubjectDao) {

    fun observeWithCounts(): Flow<List<SubjectWithCounts>> = dao.observeWithCounts()

    fun observeAll(): Flow<List<SubjectEntity>> = dao.observeAll()

    suspend fun getById(id: Long): SubjectEntity? = dao.getById(id)

    suspend fun add(name: String, colorIndex: Int): Long =
        dao.insert(
            SubjectEntity(
                name = name.trim(),
                colorIndex = colorIndex,
                createdAt = System.currentTimeMillis()
            )
        )

    suspend fun update(subject: SubjectEntity) = dao.update(subject)

    suspend fun delete(subject: SubjectEntity) = dao.delete(subject)
}