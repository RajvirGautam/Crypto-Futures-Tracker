package com.rajvir.FuturesTracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddTrackerBottomSheet(
    private val onConfirm: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.bottom_sheet_add_tracker, container, false)
        v.findViewById<TextView>(R.id.btnConfirmTracker).setOnClickListener {
            onConfirm()
            dismiss()
        }
        return v
    }
}
