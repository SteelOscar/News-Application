package ru.steeloscar.newsapp

import android.view.View

interface CustomItemClickListener {
    fun onItemClick(v: View, position: Int)
}