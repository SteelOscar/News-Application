package ru.steeloscar.newsapp.Entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class FavouriteNews(val image: ByteArray, val title: String, val detail: String) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}