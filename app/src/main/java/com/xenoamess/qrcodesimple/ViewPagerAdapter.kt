package com.xenoamess.qrcodesimple

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CameraScanFragment()
            1 -> ScanImageFragment()
            2 -> GenerateFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}
