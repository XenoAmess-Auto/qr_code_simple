package com.xenoamess.qrcodesimple

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.xenoamess.qrcodesimple.data.BarcodeFormat

class BarcodeFormatAdapter(
    context: Context,
    private val formats: List<BarcodeFormat>
) : ArrayAdapter<BarcodeFormat>(context, android.R.layout.simple_dropdown_item_1line, ArrayList(formats)) {

    private val localizedNames = formats.associateWith { it.localizedName(context) }
    private val layoutInflater = LayoutInflater.from(context)

    private val formatFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim() ?: ""
            val lower = query.lowercase()
            val results = if (lower.isEmpty()) {
                formats.toList()
            } else {
                formats.filter { format ->
                    localizedNames[format]!!.lowercase().contains(lower) ||
                        format.name.lowercase().contains(lower)
                }.sortedWith(
                    compareBy(
                        { format ->
                            val name = format.name.lowercase()
                            val display = localizedNames[format]!!.lowercase()
                            if (name.startsWith(lower) || display.startsWith(lower)) 0 else 1
                        },
                        { it.ordinal }
                    )
                )
            }
            return FilterResults().apply {
                values = results
                count = results.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            clear()
            @Suppress("UNCHECKED_CAST")
            val filtered = results?.values as? List<BarcodeFormat> ?: emptyList()
            addAll(filtered)
            if (filtered.isNotEmpty()) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }
    }

    override fun getFilter(): Filter = formatFilter

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        createView(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        createView(position, convertView, parent)

    private fun createView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: layoutInflater.inflate(
            android.R.layout.simple_dropdown_item_1line,
            parent,
            false
        )
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = getItem(position)?.localizedName(context)
        return view
    }

    fun resetFilter() {
        formatFilter.filter(null)
    }
}
