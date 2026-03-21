package com.readboy.cgl.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.zaralyn.cgl.fragment.InterceptorFragment
import com.zaralyn.cgl.fragment.SqlExecuteFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SqlExecuteFragment()
            1 -> InterceptorFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}