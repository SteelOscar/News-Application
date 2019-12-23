package ru.steeloscar.newsapp.Database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.steeloscar.newsapp.DAO.FavouriteNewsDAO
import ru.steeloscar.newsapp.DAO.SavedNewsDAO
import ru.steeloscar.newsapp.Entity.FavouriteNews
import ru.steeloscar.newsapp.Entity.SavedNewsEntity

@Database(entities = [FavouriteNews::class, SavedNewsEntity::class], version = 1)
abstract class NewsDB: RoomDatabase() {
    abstract fun getFavouriteNewsDAO(): FavouriteNewsDAO
    abstract fun getSavedNewsDAO(): SavedNewsDAO
}
