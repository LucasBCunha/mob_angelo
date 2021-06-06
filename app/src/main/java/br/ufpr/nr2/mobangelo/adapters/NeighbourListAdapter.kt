package br.ufpr.nr2.mobangelo.adapters

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import br.ufpr.nr2.mobangelo.R
import br.ufpr.nr2.mobangelo.bluetooth.CommunityManager.Neighbour
import br.ufpr.nr2.mobangelo.bluetooth.Constants


class NeighbourListAdapter(
    val context: Context,
    list: List<Neighbour>
) :
    BaseAdapter() {

    var dataSource: MutableList<Neighbour> = list as MutableList<Neighbour>
    private var vi: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    private var size = dataSource.size

    init {
        Log.d(Constants.TAG, "size of list in adapter: $size")
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val view: View

        val neighbour = getItem(position) as Neighbour

        if (convertView == null) {
            view = vi.inflate(R.layout.list_item_neighbour, parent, false)

            holder = ViewHolder()
            holder.deviceNameTv = view.findViewById(R.id.tv_device_name) as TextView
            holder.trustLevelTv = view.findViewById(R.id.tv_trust_level) as TextView
            holder.macAddressTv = view.findViewById(R.id.tv_mac_address) as TextView
            holder.competencyTv = view.findViewById(R.id.tv_competency)  as TextView

            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as ViewHolder
        }

        val deviceNameTv = holder.deviceNameTv
        val macAddressTv = holder.macAddressTv

        val trustLevelTv = holder.trustLevelTv
        val competencyTv = holder.competencyTv

        deviceNameTv.text = neighbour.device.name
        macAddressTv.text = neighbour.device.address.toString()
        trustLevelTv.text = "${neighbour.trustLevel}%"
        competencyTv.text = Constants.getCompetencyLabel(neighbour.qualification)

        if (position % 2 == 1) {
            view.setBackgroundResource(R.color.colorAccentLight)
        } else {
            view.setBackgroundResource(R.color.colorPrimaryLightLight)
        }

        return view
    }


    override fun notifyDataSetChanged() {
        size = dataSource.size
        super.notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return size
    }

    override fun getItem(position: Int): Neighbour? {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun fetchColor(context: Context, resource: Int): Int {
        val typedValue = TypedValue()
        val a: TypedArray =
            context.obtainStyledAttributes(typedValue.data, intArrayOf(resource))
        val color = a.getColor(0, 0)
        a.recycle()
        return color
    }

    private class ViewHolder {
        lateinit var deviceNameTv: TextView
        lateinit var trustLevelTv: TextView
        lateinit var macAddressTv: TextView
        lateinit var competencyTv: TextView
    }

}