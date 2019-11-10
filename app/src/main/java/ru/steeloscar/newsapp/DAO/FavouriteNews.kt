package ru.steeloscar.newsapp.DAO

import androidx.room.*
import ru.steeloscar.newsapp.Entity.FavouriteNews

@Dao
interface FavouriteNews {

    //Добавление FavouriteNews в БД
    @Insert
    fun insertAll(news: FavouriteNews)

    //Удаление FavouriteNews из БД
    @Delete
    fun delete(news: FavouriteNews)

    //Получение всех FavouriteNews из БД
    @Query("SELECT * FROM FAVOURITENEWS")
    fun getAllFavNews(): List<FavouriteNews>

    //Получение всех FavouriteNews из БД с условием
    @Query("SELECT * FROM FAVOURITENEWS WHERE title LIKE :title")
    fun getAllFavNewsWithTitle(title: String): List<FavouriteNews>

}