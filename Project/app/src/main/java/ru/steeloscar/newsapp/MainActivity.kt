package ru.steeloscar.newsapp

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.JsonReader
import android.util.Log
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
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
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


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
    private lateinit var db: NewsDB
    private lateinit var savedNewsDao: SavedNewsDAO
    private var isLoaded = false
    private var isRefreshed = false
    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null
    private lateinit var customTabsIntent: CustomTabsIntent
    private val CUSTOM_PACKAGE = "com.android.chrome"
    private val APP_PREFERENCES = "settings"
    private val APP_PREFERENCES_FIRST_START = "firstStart"
    private var submitSearch = false
    private var task = "sources=rt"
    private var token = "b81c1cca8d3e4f418235e043fc37e532"
    private var isSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        setContentView(R.layout.activity_main)

        val settings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val firstStart = settings.getBoolean(APP_PREFERENCES_FIRST_START, true)

        if (firstStart) {
            if (isNetworkAvailable(this)) {
                refreshLayout.isRefreshing = true
                networkApiTask(task, pageNumber, token)

                val editor = settings.edit()
                editor.putBoolean(APP_PREFERENCES_FIRST_START, false)
                editor.apply()
            } else {
                Toast.makeText(
                    this,
                    "При загрузке данных произошла ошибка. Проверьте ваше подключение к сети.",
                    Toast.LENGTH_SHORT
                ).show()
                Handler().postDelayed({ refreshLayout.isRefreshing = false }, 1000)
            }

        }

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

        restoreSavedNews(this)


        setSupportActionBar(toolBar)
//        title = "Новости"
        tb_title.text = "Новости"

        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)


                if ((myLayoutManager.findLastVisibleItemPosition() == itemPosUpdate) and isLoaded) {
                    if (isNetworkAvailable(this@MainActivity)) {
                        itemPosUpdate += dPos
                        pageNumber++
                        networkApiTask(task, pageNumber, token)
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


        refreshLayout.setOnRefreshListener {
            if (isNetworkAvailable(this)) {
                tb_title.text = "Обновление..."
                isRefreshed = true
                if (isSearch) {
                    isLoaded = false
                    itemPosUpdate = 2
                    task = "sources=rt"
                    isSearch = false
                }
                networkApiTask(task, pageNumber, token)
            } else {
                Toast.makeText(
                    this,
                    "При загрузке данных произошла ошибка. Проверьте ваше подключение к сети.",
                    Toast.LENGTH_SHORT
                ).show()
                Handler().postDelayed({ refreshLayout.isRefreshing = false }, 1000)
            }
        }


        tb_search.setOnClickListener {
            searchNews()
        }

        tb_search_text.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KEYCODE_ENTER) {
                searchNews()
                true
            }
            false
        }

        toolBar.setOnTouchListener(DoubleClickListener())
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

            val cf: CertificateFactory = CertificateFactory.getInstance("X.509")

            val inputStream = resources.openRawResource(R.raw.my)
            val certificate = cf.generateCertificate(inputStream)

// Create a KeyStore containing our trusted CAs
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
                setCertificateEntry("ca", certificate)
            }

// Create a TrustManager that trusts the CAs inputStream our KeyStore
            val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
            val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(keyStore)
            }

// Create an SSLContext that uses our TrustManager
            val context: SSLContext = SSLContext.getInstance("TLS").apply {
                init(null, tmf.trustManagers, null)
            }

            val connection = url.openConnection() as HttpsURLConnection
            connection.sslSocketFactory = context.socketFactory

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
                        runOnUiThread { refreshLayout.isRefreshing = false }
                        isLoaded = true
                        isRefreshed = false
                    }

                    listOfModel.addAll(tempArrayList)

                    runOnUiThread {
                        tb_title.text = "Новости"
//                        title = "Новости"
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

                    if (position > 0) listOfModel.addAll(0, ArrayList(tempArrayList.take(position)))

                    refreshLayout.isRefreshing = false
                    runOnUiThread {
                        tb_title.text = "Новости"
//                        title = "Новости"
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
                    Log.d("response", connection.responseCode.toString())
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

                    listOfModel[index].image =
                        BitmapFactory.decodeStream(stream, null, options) as Bitmap
                    stream.close()
                } catch (e: Exception) {
                    when(e) {
                        is MalformedURLException, is FileNotFoundException -> Log.e("exception", e.message.toString())
                    }
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
            convertDate(publishedAt)
        )
    }

    /*
    Checking the network for availability.
     */

    private fun isNetworkAvailable(context: MainActivity): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT < 23) {
            val activeNetworkInfo = cm.activeNetworkInfo

            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        } else {
            val network = cm.activeNetwork

            if (network != null) {
                val networkConnection = cm.getNetworkCapabilities(network)

                return (networkConnection.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) or networkConnection.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            }
        }

        return false
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
                //Override the click function of a RecyclerView element in custom listener interface.
                recyclerAdapter = RecyclerAdapter(listOfModel, object : CustomItemClickListener {
                    override fun onItemClick(position: Int) {
                        try {
                            createCustomTabs()
                            customTabsIntent.launchUrl(this@MainActivity, Uri.parse(listOfModel[position].url))
                        } catch (e: ActivityNotFoundException) {
                            val intent = Intent(this@MainActivity, WebBrowserActivity::class.java)

                            intent.putExtra("url", listOfModel[position].url)
                            startActivity(intent)
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

    private fun convertDate(date: String) = "${date.substring(11,19)} ${date.substring(0,10)}"

    private fun searchNews() {
        tb_search.startAnimation(AnimationUtils.loadAnimation(this, R.anim.image_click))
        if (!submitSearch) {
            tb_search_text.visibility = View.VISIBLE
            tb_search_text.startAnimation(AnimationUtils.loadAnimation(this, R.anim.alpha_anim_search_text))
            tb_search_text.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            tb_search.setImageResource(R.drawable.ic_check_black_24dp)
            submitSearch = true
        } else {
            if (tb_search_text.text.isNotEmpty()) {
                if (isNetworkAvailable(this)) {
                    tb_title.text = "Обновление..."
                    isSearch = true
                    refreshLayout.isRefreshing = true
                    isRefreshed = false
                    isLoaded = false
                    itemPosUpdate = 2
                    task = "q=${tb_search_text.text}"
                    networkApiTask(task, pageNumber, token)
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(tb_search_text.windowToken, 0)
                    tb_search_text.clearFocus()
                    tb_search.setImageResource(R.drawable.ic_search_black_24dp)
                    tb_search_text.visibility = View.GONE
                    submitSearch = false
                } else {
                    Toast.makeText(
                        this,
                        "При загрузке данных произошла ошибка. Проверьте ваше подключение к сети.",
                        Toast.LENGTH_SHORT
                    ).show()
                    Handler().postDelayed({ refreshLayout.isRefreshing = false }, 1000)
                }
            }
        }
    }

}