package ru.steeloscar.newsapp

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView


class RecyclerAdapter(viewList: ArrayList<RecyclerViewModel>,clickListener: CustomItemClickListener): RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {

    private val viewList = viewList
    private val clickListener = clickListener
    private var onAttach = true
    private val DURATION = 500.toLong()
    var mode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.cardview_layout, parent, false)

        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return viewList.count()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.findImageViewById(R.id.item_image).setImageBitmap(viewList[position].image)
        holder.findTextViewById(R.id.item_title).text = viewList[position].text_title
        holder.findTextViewById(R.id.item_detail).text = viewList[position].text_detail
        holder.findTextViewById(R.id.item_source).text = viewList[position].source
        holder.findTextViewById(R.id.item_date).text = viewList[position].publishedAt

        holder.itemView.setOnClickListener {
            clickListener.onItemClick(it, holder.position)
        }

        if (mode) setAnimation(holder.itemView, position )
    }

    private fun setAnimation(v: View, i: Int) {
        var cnt = i

        if (!onAttach) {
            cnt = -1
        }

        var isNotFirstItem = cnt == -1

        cnt++
        v.alpha = 0.0f
        val animatorSet = AnimatorSet()
        val animator = ObjectAnimator.ofFloat(v, "alpha", 0.0f, 0.5f, 1.0f)
        ObjectAnimator.ofFloat(v, "alpha", 0.0f).start()
        animator.startDelay = if (isNotFirstItem) 300 / 2 else ( cnt * DURATION/3 )
        animator.duration = 200
        animatorSet.play(animator)
        animator.start()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {

        recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {

                onAttach = false
                mode = true
                super.onScrollStateChanged(recyclerView, newState)
            }
        })

        super.onAttachedToRecyclerView(recyclerView)
    }


    inner class ViewHolder(private val v: View) : RecyclerView.ViewHolder(v) {

        private val cachedView = SparseArray<View>()

        fun findImageViewById(id: Int): ImageView {
            val cView = cachedView.get(id)

            if (cView != null) {
                return  cView as ImageView
            }

            val view =  v.findViewById<ImageView>(id)


            cachedView.put(id, view)
            return view as ImageView
        }

        fun findTextViewById(id: Int): TextView {
            val cView = cachedView.get(id)

            if (cView != null) {
                return  cView as TextView
            }

            val view = v.findViewById<TextView>(id)

            cachedView.put(id, view)
            return view as TextView
        }

    }

}