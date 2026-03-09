package com.rajvir.FuturesTracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL

data class BlogItem(
    val title: String,
    val link: String,
    val date: String,
    val description: String
)

class BlogFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_blog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvBlog)
        val progress = view.findViewById<ProgressBar>(R.id.blogProgress)
        rv.layoutManager = LinearLayoutManager(requireContext())

        progress.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val items = fetchRss()
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    progress.visibility = View.GONE
                    rv.adapter = BlogAdapter(items) { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
        }
    }

    private fun fetchRss(): List<BlogItem> {
        return try {
            val url = URL("https://cointelegraph.com/rss")
            val connection = url.openConnection()
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(connection.getInputStream(), "UTF-8")

            val items = mutableListOf<BlogItem>()
            var title = ""; var link = ""; var date = ""; var desc = ""; var inItem = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> { inItem = true; title = ""; link = ""; date = ""; desc = "" }
                        "title" -> if (inItem) title = parser.nextText().trim()
                        "link" -> if (inItem && link.isEmpty()) link = parser.nextText().trim()
                        "pubDate" -> if (inItem) date = parser.nextText().take(22).trim()
                        "description" -> if (inItem) {
                            desc = parser.nextText()
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .trim()
                                .take(200)
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                        if (title.isNotEmpty() && link.isNotEmpty()) {
                            items.add(BlogItem(title, link, date, desc))
                        }
                        inItem = false
                        if (items.size >= 30) return items
                    }
                }
                eventType = parser.next()
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }
}
