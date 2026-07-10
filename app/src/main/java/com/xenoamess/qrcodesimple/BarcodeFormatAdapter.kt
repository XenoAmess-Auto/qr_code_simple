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
) : ArrayAdapter<BarcodeFormat>(context, R.layout.item_barcode_format, ArrayList(formats)) {

    private val localizedNames = formats.associateWith { it.localizedName(context) }
    private val englishNames = formats.associateWith { it.displayName }
    private val layoutInflater = LayoutInflater.from(context)
    private val showEnglish = !isEnglishLocale(context)

    private val formatFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim() ?: ""
            val lower = query.lowercase()
            val results = if (lower.isEmpty()) {
                formats.toList()
            } else {
                formats.filter { format ->
                    localizedNames[format]!!.lowercase().contains(lower) ||
                        englishNames[format]!!.lowercase().contains(lower) ||
                        format.name.lowercase().contains(lower)
                }.sortedWith(
                    compareBy(
                        { format ->
                            val localized = localizedNames[format]!!.lowercase()
                            val english = englishNames[format]!!.lowercase()
                            val name = format.name.lowercase()
                            if (localized.startsWith(lower) || english.startsWith(lower) || name.startsWith(lower)) 0 else 1
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
            R.layout.item_barcode_format,
            parent,
            false
        )
        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)
        val format = getItem(position) ?: return view
        text1.text = localizedNames[format]
        if (showEnglish && localizedNames[format] != englishNames[format]) {
            text2.text = englishNames[format]
            text2.visibility = View.VISIBLE
        } else {
            text2.text = ""
            text2.visibility = View.GONE
        }
        return view
    }

    fun resetFilter() {
        formatFilter.filter(null)
    }

    private fun isEnglishLocale(context: Context): Boolean {
        val locale = context.resources.configuration.locales.get(0)
            ?: context.resources.configuration.locale
        return locale.language.equals("en", ignoreCase = true)
    }
}
