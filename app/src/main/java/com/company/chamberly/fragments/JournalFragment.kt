package com.company.chamberly.fragments

import android.app.DatePickerDialog
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.company.chamberly.R
import com.company.chamberly.activities.ExpressFeelings
import com.company.chamberly.adapters.JournalAdapter
import com.company.chamberly.viewmodels.JournalEntry
import com.company.chamberly.viewmodels.JournalViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class JournalFragment : Fragment() {

    private lateinit var journalViewModel: JournalViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JournalAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_journal, container, false)
        val addButton = view.findViewById<Button>(R.id.add_journal)
        addButton.setOnClickListener {
            showDatePickerDialog()
        }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        journalViewModel = ViewModelProvider(this).get(JournalViewModel::class.java)
        journalViewModel.journalEntries.observe(viewLifecycleOwner, { entries ->
            adapter = JournalAdapter(entries, this::onEditClick, this::onDeleteClick)
            recyclerView.adapter = adapter
        })

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return view
        journalViewModel.fetchJournalEntries(uid)
        return view
    }

    override fun onResume() {
        super.onResume()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        journalViewModel.fetchJournalEntries(uid)
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = "$selectedYear-${selectedMonth + 1}-$selectedDay"
                navigateToNextFragment(selectedDate)
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun navigateToNextFragment(selectedDate: String) {
        val intent = Intent(requireContext(), ExpressFeelings::class.java).apply {
            putExtra("selectedDate", selectedDate)
        }
        startActivity(intent)
    }

    private fun onEditClick(entry: JournalEntry, isEditMode: Boolean) {
        val intent = Intent(requireContext(), ExpressFeelings::class.java).apply {
            putExtra("selectedDate", entry.date)
            putExtra("emotionText", entry.emotionText)
            putExtra("isEditMode", isEditMode)
        }
        startActivity(intent)
    }

    private fun onDeleteClick(entry: JournalEntry) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        val userDocRef = firestore.collection("Journal").document(uid)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            if (snapshot.exists()) {
                val data = snapshot.data ?: return@runTransaction
                data.remove(entry.date)
                transaction.set(userDocRef, data)
            }
        }.addOnCompleteListener {
            if (it.isSuccessful) {
                journalViewModel.fetchJournalEntries(uid)
            }
        }
    }
}