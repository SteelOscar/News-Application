package ru.steeloscar.newsapp

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcel
import android.os.Parcelable

class ParcelableViewModel(val source: String, val urlImage: String, val text_title: String?, val text_detail: String?, val url: String, val publishedAt: String):Parcelable {

    var image: Bitmap? = null

    constructor(parcel: Parcel) : this(
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString().toString(),
        parcel.readString().toString()
    ) {
        image = parcel.readParcelable(Bitmap::class.java.classLoader)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(source)
        parcel.writeString(urlImage)
        parcel.writeString(text_title)
        parcel.writeString(text_detail)
        parcel.writeString(url)
        parcel.writeString(publishedAt)
        parcel.writeParcelable(image, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ParcelableViewModel> {
        override fun createFromParcel(parcel: Parcel): ParcelableViewModel {
            return ParcelableViewModel(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableViewModel?> {
            return arrayOfNulls(size)
        }
    }

}