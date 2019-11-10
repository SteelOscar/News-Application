package ru.steeloscar.newsapp.DAO

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import ru.steeloscar.newsapp.Entity.SavedNewsEntity

@Dao
interface SavedNewsDAO {

    //Добавление SavedNews в БД
    @Insert
    fun insertAll(news: SavedNewsEntity)

    //Удаление SavedNews из БД
    @Delete
    fun delete(news: SavedNewsEntity)

    //Получение всех SavedNews из БД
    @Query("SELECT * FROM savednewsentity")
    fun getAllSavedNews(): List<SavedNewsEntity>

    @Query("SELECT * FROM savednewsentity WHERE title like :title")
    fun getSavedNewsWithTitle(title: String): List<SavedNewsEntity>

    @Query("DELETE FROM savednewsentity")
    fun deleteAll()
}