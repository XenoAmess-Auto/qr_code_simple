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
    formats: List<BarcodeFormat>,
    recentFormats: List<BarcodeFormat> = emptyList()
) : ArrayAdapter<BarcodeFormat>(context, R.layout.item_barcode_format, ArrayList(formats)) {

    private val recent = recentFormats.toSet()
    private val formats = recentFormats.filter { it in formats } + formats.filter { it !in recent }

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
            text2.text = detail(format)
            text2.visibility = View.VISIBLE
        } else {
            text2.text = detail(format)
            text2.visibility = View.VISIBLE
        }
        return view
    }

    fun resetFilter() {
        formatFilter.filter(null)
    }

    private fun detail(format: BarcodeFormat): String {
        val category = when (format) {
            BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC,
            BarcodeFormat.PDF417, BarcodeFormat.MAXICODE, BarcodeFormat.MICRO_QR,
            BarcodeFormat.HAN_XIN, BarcodeFormat.SWISS_QR_CODE, BarcodeFormat.UPN_QR_CODE,
            BarcodeFormat.AZTEC_RUNE, BarcodeFormat.CODE_ONE, BarcodeFormat.GRID_MATRIX ->
                context.getString(R.string.format_category_matrix)
            BarcodeFormat.POSTNET, BarcodeFormat.ROYAL_MAIL_4_STATE, BarcodeFormat.USPS_ONE_CODE,
            BarcodeFormat.USPS_PACKAGE, BarcodeFormat.JAPAN_POST, BarcodeFormat.KIX_CODE,
            BarcodeFormat.KOREA_POST, BarcodeFormat.AUSTRALIA_POST -> context.getString(R.string.format_category_postal)
            else -> context.getString(R.string.format_category_linear)
        }
        val availability = context.getString(
            if (format.isScannable) R.string.format_scannable else R.string.format_generate_only
        )
        val recentLabel = if (format in recent) "${context.getString(R.string.format_recent)} · " else ""
        val english = englishNames[format].orEmpty()
        return "$recentLabel$category · $availability · $english"
    }

    private fun isEnglishLocale(context: Context): Boolean {
        val locale = context.resources.configuration.locales.get(0)
        return locale.language.equals("en", ignoreCase = true)
    }
}
