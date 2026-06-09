package com.diary.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DiaryRepository(context: Context) {

    private val dao = (context.applicationContext as com.diary.app.DiaryApplication).database.diaryDao()

    val allEntries: Flow<List<DiaryEntry>> = dao.getAllEntries()

    fun getFavoriteEntries(): Flow<List<DiaryEntry>> = dao.getFavoriteEntries()

    fun searchEntries(query: String): Flow<List<DiaryEntry>> = dao.searchEntries(query)

    suspend fun getEntryById(id: Long): DiaryEntry? = dao.getEntryById(id)

    suspend fun insertEntry(entry: DiaryEntry): Long = dao.insertEntry(entry)

    suspend fun updateEntry(entry: DiaryEntry) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: DiaryEntry) = dao.deleteEntry(entry)

    suspend fun getAllEntriesSync(): List<DiaryEntry> = dao.getAllEntriesSync()

    suspend fun getEntryCount(): Int = dao.getEntryCount()
}
