package ru.steeloscar.newsapp.Entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class SavedNewsEntity (val image: ByteArray, val title: String?, val detail: String?,val date: String,val source: String, val url: String, val urlImage: String) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}