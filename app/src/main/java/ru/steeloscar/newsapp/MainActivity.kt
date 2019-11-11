package ru.steeloscar.newsapp

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.JsonReader
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import kotlinx.android.synthetic.main.activity_main.*
import ru.steeloscar.newsapp.DAO.SavedNewsDAO
import ru.steeloscar.newsapp.Database.NewsDB
import ru.steeloscar.newsapp.Entity.SavedNewsEntity
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class MainActivity : AppCompatActivity() {

    private var myRecyclerView: RecyclerView? = null
    lateinit var listOfModel: ArrayList<RecyclerViewModel>
    private lateinit var recyclerAdapter: RecyclerAdapter
    private lateinit var myLayoutManager: LinearLayoutManager
    private var itemPosUpdate = 2
    private var dPos = 3
    private var pageNumber = 1
    private var startTime = System.currentTimeMillis()
    private var elapsedTime: Long = 0
    private var fabState = true
    private lateinit var db: NewsDB
    private lateinit var savedNewsDao: SavedNewsDAO
    private var isLoaded = false
    private var isRefreshed = false
    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null
    private lateinit var customTabsIntent: CustomTabsIntent
    private val CUSTOM_PACKAGE = "com.android.chrome"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolBar)
        title = "Новости"

        listOfModel = ArrayList()
        myRecyclerView = findViewById(R.id.recycler_view)
        myLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val mDividerItemDecoration =
            DividerItemDecoration(recycler_view.context, myLayoutManager.orientation)
        myRecyclerView?.apply {
            addItemDecoration(mDividerItemDecoration)
            layoutManager = myLayoutManager
            isVerticalScrollBarEnabled = false
        }

        db = Room.databaseBuilder(this, NewsDB::class.java, "my-database").build()
        savedNewsDao = db.getSavedNewsDAO()

        createCustomTabs()

        restoreSavedNews(this)

        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)


                if ((myLayoutManager.findLastVisibleItemPosition() == itemPosUpdate) and isLoaded) {
                    if (isNetworkAvailable(this@MainActivity)) {
                        itemPosUpdate += dPos
                        pageNumber++
                        networkApiTask(
                            "sources=rbc",
                            pageNumber,
                            "b81c1cca8d3e4f418235e043fc37e532"
                        )
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "При загрузке данных произошла ошибка. Проверьте ваше подключение к сети.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

        })



        fab_main.setOnClickListener {
            fabState = if (fabState) fabRotateAnim(
                R.drawable.ic_close_black_56dp,
                fabState
            ) else fabRotateAnim(R.drawable.build24px, fabState)
        }

        fab_children1.setOnClickListener {

        }


        refreshLayout.setOnRefreshListener {
            if (isNetworkAvailable(this)) {
                title = "Обновление..."
                isRefreshed = true
                Log.d("url", isLoaded.toString())
                networkApiTask("sources=rbc", pageNumber, "b81c1cca8d3e4f418235e043fc37e532")
            } else {
                Toast.makeText(
                    this,
                    "При загрузке данных произошла ошибка. Проверьте ваше подключение к сети.",
                    Toast.LENGTH_SHORT
                ).show()
                Handler().postDelayed({ refreshLayout.isRefreshing = false }, 1000)
            }
        }

        toolBar.setOnTouchListener(DoubleClickListener())
    }

    /*
    Rotate floating action button, if it pressed. Depending on the state, change its icon.
     */

    private fun fabRotateAnim(res_fab_icon: Int, fabState: Boolean): Boolean {
        fab_main.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_fab_icon))
        Handler().postDelayed({
            fab_main.setImageResource(res_fab_icon)
            if (fabState) fab_children1.show() else fab_children1.hide()
        }, 600)
        return !fabState
    }

    /*
    Loading news from REST API https://hewsapi.org
    This method running in the other thread, different from the UI thread.
     */

    private fun networkApiTask(task: String, page: Int, token: String) {
        Thread {
            var pageNumber = page
            if (isRefreshed and isLoaded) pageNumber = 1
            val request = "${getString(R.string.url)}$task&page=$pageNumber&apiKey=$token"
            val url = URL(request)
            val connection = url.openConnection() as HttpsURLConnection
            connection.connect()

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream
                val responseBodyReader = InputStreamReader(responseBody, "UTF-8")
                val jsonReader = JsonReader(responseBodyReader)

                val tempArrayList = readJSON(jsonReader)
                responseBodyReader.close()
                responseBody.close()

                if (!isRefreshed or !isLoaded) {
                    if (!isLoaded) {
                        listOfModel.clear()
                        refreshLayout.isRefreshing = false
                        isLoaded = true
                        isRefreshed = false
                    }

                    listOfModel.addAll(tempArrayList)

                    runOnUiThread {
                        title = "Новости"
                        recyclerAdapter.mode = false
                        recyclerAdapter.notifyDataSetChanged()
                        for (index in listOfModel.count() - tempArrayList.count() until listOfModel.count()) {
                            pictureLoadTask(index)
                            recyclerAdapter.notifyDataSetChanged()
                        }
                    }
                } else {
                    var position = 0

                    for ((index, element) in tempArrayList.withIndex()) {
                        if (element.text_detail == listOfModel[0].text_detail) {
                            position = index
                            break
                        }
                    }

                    Log.d("url", "position = $position")

                    if (position > 0) listOfModel.addAll(0, ArrayList(tempArrayList.take(position)))

                    refreshLayout.isRefreshing = false
                    runOnUiThread {
                        title = "Новости"
                        recyclerAdapter.mode = false
//                        recyclerAdapter.notifyDataSetChanged()
                        if (position > 0) {
                            for (index in 0 until position) {
                                pictureLoadTask(index)
                                recyclerAdapter.notifyDataSetChanged()
                            }
                        }
                        isRefreshed = false
                    }
                }
            } else {
                runOnUiThread {
                    Log.d("responce", connection.responseCode.toString())
                }
            }

            connection.disconnect()
        }.start()
    }

    /*
    Loading picture in created news.
    This method running in the other thread, different from the UI thread.
     */

    private fun pictureLoadTask(index: Int) {
        val thread = Thread(
            Runnable {
                val options = Options()
                val url = listOfModel[index].urlImage

                try {
                    val stream = URL(url).openStream()

                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options, 300, 150)
                    listOfModel[index].image =
                        BitmapFactory.decodeStream(stream, null, options) as Bitmap
                    stream.close()
                } catch (e: MalformedURLException) {
                    Log.e("exception", e.message)
                } catch (e: FileNotFoundException) {
                    Log.e("exception", e.message)
                }
            }
        )
        thread.start()
    }

    /*
    Primary JSON processing, getting a data array
     */

    private fun readJSON(reader: JsonReader): ArrayList<RecyclerViewModel> {
        lateinit var arrayList: ArrayList<RecyclerViewModel>

        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "articles") {
                arrayList = readArrayJson(reader)
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return arrayList
    }

    /*
    Constructing an ArrayList from a list of readArrayObject () objects
     */

    private fun readArrayJson(reader: JsonReader): ArrayList<RecyclerViewModel> {
        val arrayList = ArrayList<RecyclerViewModel>()

        reader.beginArray()
        while (reader.hasNext()) {
            val tempObj = readArrayObject(reader)
            if (tempObj != null) arrayList.add(tempObj)
        }
        reader.endArray()
        return arrayList
    }

    /*
    Create RecyclerViewModel from JSON object of article news. Skip a article, if in one of this fields not contains data.
     */

    private fun readArrayObject(reader: JsonReader): RecyclerViewModel? {
        lateinit var source: String
        var title: String? = null
        var detail: String? = null
        lateinit var url: String
        var urlImage: String? = null
        lateinit var publishedAt: String

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "source" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {

                        if (reader.nextName() == "name") source = reader.nextString()
                        else reader.skipValue()

                    }
                    reader.endObject()
                }
                "title" -> {
                    title = try {
                        reader.nextString()
                    } catch (e: IllegalStateException) {
                        reader.skipValue()
                        null
                    }
                }
                "description" -> {
                    detail = try {
                        reader.nextString()
                    } catch (e: IllegalStateException) {
                        reader.skipValue()
                        null
                    }
                }
                "url" -> url = reader.nextString()
                "urlToImage" -> {
                    urlImage = try {
                        reader.nextString()
                    } catch (e: IllegalStateException) {
                        reader.skipValue()
                        null
                    }
                }
                "publishedAt" -> publishedAt = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if ((title == null) or (detail == null) or (urlImage == null)) null else RecyclerViewModel(
            this.resources,
            source,
            R.drawable.loading,
            urlImage as String,
            title,
            detail,
            url,
            publishedAt
        )
    }

    /*
    Don't know what write in this comment. Probably, this method calculate resolution of picture.
     */

    private fun calculateInSampleSize(options: Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if ((height > reqHeight) or (width > reqWidth)) {

            while ((height / (2 * inSampleSize) > reqHeight) and (width / (2 * inSampleSize) > reqWidth)) {
                inSampleSize *= 2
            }

        }
        return inSampleSize
    }

    /*
    Checking the network for availability.
     */

    private fun isNetworkAvailable(context: MainActivity): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = cm.activeNetworkInfo

        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
    }


    /*
    Double tap at the toolbar for move to top of list. Moving smoothly.
     */

    private inner class DoubleClickListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_DOWN) {
                elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime > 500) {
                    startTime = System.currentTimeMillis()
                    return false
                } else {
                    if (elapsedTime > 50) {
                        if (myLayoutManager.findLastVisibleItemPosition() > 10) recycler_view.scrollToPosition(5)
                        recycler_view.smoothScrollToPosition(0)
                        startTime = System.currentTimeMillis()
                        return true
                    }
                }
            }
            return false
        }
    }

    /*
    Saving data when activity is stopped.
     */

    override fun onStop() {
        super.onStop()
        saveStateNews(listOfModel)
    }

    /*
    Saving list of models to database.
    This method running in the other thread, different from the UI thread.
     */

    private fun saveStateNews(listModel: ArrayList<RecyclerViewModel>) {
        Thread(Runnable {
            savedNewsDao.deleteAll()
            for (model in listModel) {
                val bitmap = model.image
                val stream = ByteArrayOutputStream()
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                val image = stream.toByteArray()
                val entity = SavedNewsEntity(
                    image,
                    model.text_title,
                    model.text_detail,
                    model.publishedAt,
                    model.source,
                    model.url,
                    model.urlImage
                )
                savedNewsDao.insertAll(entity)
            }
        }).start()
    }

    /*
    Restoring saved news from database.
    This method running in the other thread, different from the UI thread.
     */

    private fun restoreSavedNews(context: Context) {
        val listViewModel = ArrayList<RecyclerViewModel>()
        var savedNews: List<SavedNewsEntity>

        Thread(Runnable {
            savedNews = savedNewsDao.getAllSavedNews()

            if (savedNews.isNotEmpty()) {
                for (entity in savedNews) {
                    val viewModel = RecyclerViewModel(
                        context.resources,
                        entity.source,
                        0,
                        entity.urlImage,
                        entity.title,
                        entity.detail,
                        entity.url,
                        entity.date
                    )
                    viewModel.image =
                        BitmapFactory.decodeByteArray(entity.image, 0, entity.image.count())
                    listViewModel.add(viewModel)
                }
            }

            runOnUiThread {
                listOfModel.addAll(listViewModel)
                Log.d("url", listOfModel.count().toString())
                //Override the click function of a RecyclerView element in custom listener interface.
                recyclerAdapter = RecyclerAdapter(listOfModel, object : CustomItemClickListener {
                    override fun onItemClick(v: View, position: Int) {
                        try {
                            customTabsIntent.launchUrl(this@MainActivity, Uri.parse(listOfModel[position].url))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(this@MainActivity, "Google Chrome does not exists.", Toast.LENGTH_SHORT).show()

                        }
                    }
                })
                myRecyclerView!!.adapter = recyclerAdapter
            }
        }).start()
    }

    /*
    Saving used data for further recovery when changing orientation.
    Not used because it causes an error "data parcel size is ..." ().
    %The problem is not solved, orientation change is disabled in the manifest%
    */

//    override fun onSaveInstanceState(outState: Bundle) {
//    val parcelableListModel = ArrayList<ParcelableViewModel>()
//
//    for (element in listOfModel) {
//    val parcelableViewModel = ParcelableViewModel(
//    element.source,
//    element.urlImage,
//    element.text_title,
//    element.text_detail,
//    element.url,
//    element.publishedAt
//    )
//    parcelableViewModel.image = element.image
//    parcelableListModel.add(parcelableViewModel)
//    }
//
//    outState.putParcelableArrayList("parcelable", parcelableListModel)
//    outState.putInt("pageNumber", pageNumber)
//    outState.putInt("itemPosUpdate", itemPosUpdate)
//    outState.putBoolean("isLoaded", isLoaded)
//    outState.putBoolean("isRefreshed", isRefreshed)
//    outState.putBoolean("fabState", fabState)
//
//    super.onSaveInstanceState(outState)
//    }

    /*
    Create ArrayList<RecyclerViewModel> from ArrayList<ParcelableViewModel>
    %Not used, because saving data in onSaveInstanceState not working%
     */
//    private fun ParcelableListToList(parcelableList: ArrayList<ParcelableViewModel>?) {
//
//        if (parcelableList != null) {
//            for (element in parcelableList) {
//                val recyclerViewModel = RecyclerViewModel(
//                    this.resources,
//                    element.source,
//                    0,
//                    element.urlImage,
//                    element.text_title,
//                    element.text_detail,
//                    element.url,
//                    element.publishedAt
//                )
//                recyclerViewModel.image = element.image
//                listOfModel.add(recyclerViewModel)
//            }
//        }
//
//        recyclerAdapter = RecyclerAdapter(listOfModel, object : CustomItemClickListener {
//            override fun onItemClick(v: View, position: Int) {
//                customTabsIntent.launchUrl(this@MainActivity, Uri.parse(listOfModel[position].url))
//            }
//        })
//        myRecyclerView!!.adapter = recyclerAdapter
//    }

    /*
    Create the custom tabs for showing webpage.
     */

    private fun createCustomTabs() {
        val customTabsServiceConnection =object: CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName?,
                client: CustomTabsClient?
            ) {
                customTabsClient = client
                customTabsClient?.warmup(0L)
                customTabsSession = customTabsClient?.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                customTabsClient = null
            }
        }
        CustomTabsClient.bindCustomTabsService(this,CUSTOM_PACKAGE, customTabsServiceConnection)
        customTabsIntent = CustomTabsIntent.Builder(customTabsSession)
            .setShowTitle(true)
            .setToolbarColor(Color.DKGRAY)
            .build()
    }

}