package ru.steeloscar.newsapp

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class RecyclerViewModel(resource: Resources, val source: String, imageResource: Int,val urlImage: String, val text_title: String?, val text_detail: String?, val url: String, val publishedAt: String) {
    var image: Bitmap? = null

    init {
        if (imageResource != 0) {
            image = BitmapFactory.decodeResource(resource, imageResource)
        }
    }
}
