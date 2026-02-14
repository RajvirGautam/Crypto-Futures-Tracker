package com.rajvir.FuturesTracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_PAGE = "extra_start_page"
        const val PAGE_HOME = 0
        const val PAGE_CHARTS = 1
        const val PAGE_WIDGETS = 2
        const val PAGE_DISCUSSION = 3
        const val PAGE_BLOG = 4
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val root = findViewById<android.view.View>(R.id.dashboardRoot)
        viewPager = findViewById(R.id.vpMain)
        bottomNav = findViewById(R.id.bottomNav)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            viewPager.updatePadding(top = bars.top)
            bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        val pages = listOf(
            HomeFragment(),
            ChartsFragment(),
            WidgetsFragment(),
            DiscussionFragment(),
            BlogFragment()
        )

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = pages.size
            override fun createFragment(position: Int): Fragment = pages[position]
        }
        viewPager.isUserInputEnabled = false

        bottomNav.setOnItemSelectedListener { item ->
            viewPager.currentItem = when (item.itemId) {
                R.id.nav_home -> PAGE_HOME
                R.id.nav_charts -> PAGE_CHARTS
                R.id.nav_widgets -> PAGE_WIDGETS
                R.id.nav_discussion -> PAGE_DISCUSSION
                R.id.nav_blog -> PAGE_BLOG
                else -> PAGE_HOME
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val itemId = when (position) {
                    PAGE_HOME -> R.id.nav_home
                    PAGE_CHARTS -> R.id.nav_charts
                    PAGE_WIDGETS -> R.id.nav_widgets
                    PAGE_DISCUSSION -> R.id.nav_discussion
                    PAGE_BLOG -> R.id.nav_blog
                    else -> R.id.nav_home
                }
                if (bottomNav.selectedItemId != itemId) {
                    bottomNav.selectedItemId = itemId
                }
            }
        })

        val startPage = intent.getIntExtra(EXTRA_START_PAGE, PAGE_HOME).coerceIn(PAGE_HOME, PAGE_BLOG)
        viewPager.currentItem = startPage
        bottomNav.selectedItemId = when (startPage) {
            PAGE_HOME -> R.id.nav_home
            PAGE_CHARTS -> R.id.nav_charts
            PAGE_WIDGETS -> R.id.nav_widgets
            PAGE_DISCUSSION -> R.id.nav_discussion
            PAGE_BLOG -> R.id.nav_blog
            else -> R.id.nav_home
        }
    }
}
