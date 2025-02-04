package com.company.chamberly.fragments

import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.company.chamberly.R
import com.company.chamberly.viewmodels.UserViewModel
import kotlin.math.max

class CreateChamberFragment : Fragment() {

    private val userViewModel: UserViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_create_chamber, container, false)
        val chamberTitleField = view.findViewById<EditText>(R.id.chamber_title)
        val createButton = view.findViewById<Button>(R.id.create_button)

        val maxLength = 50
        val filterArray = arrayOf(InputFilter.LengthFilter(maxLength))
        chamberTitleField.filters = filterArray

        createButton.setOnClickListener {
            val chamberTitle = chamberTitleField.text.toString()
            if (chamberTitle.isBlank()) {
                chamberTitleField.error = "Please enter a title"
            } else {
                userViewModel.createChamber(
                    chamberTitle = chamberTitle,
                    callback = { findNavController().popBackStack() }
                )

            }
        }
        return view
    }
}