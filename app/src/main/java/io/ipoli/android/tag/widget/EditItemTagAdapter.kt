package io.ipoli.android.tag.widget

import android.content.Context
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.quest.Color
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.item_choose_tag.view.*
import kotlinx.android.synthetic.main.item_edit_quest_tag.view.*


class EditItemAutocompleteTagAdapter(tags: List<Tag>, context: Context) :
    ArrayAdapter<Tag>(
        context,
        R.layout.item_tag_autocomplete,
        tags
    ) {
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
        bindView(position, convertView, parent)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
        bindView(position, convertView, parent)

    private fun bindView(position: Int, convertView: View?, parent: ViewGroup): View {

        val view = if (convertView == null) {
            val inflater = LayoutInflater.from(context)
            inflater.inflate(R.layout.item_tag_autocomplete, parent, false) as TextView
        } else {
            convertView as TextView
        }

        val item = getItem(position)
        view.text = item.name

        val icon = item.icon?.let { AndroidIcon.valueOf(it.name).icon }
            ?: MaterialDesignIconic.Icon.gmi_label
        val color = AndroidColor.valueOf(item.color.name).color500

        view.setCompoundDrawablesWithIntrinsicBounds(
            IconicsDrawable(view.context)
                .normalIcon(
                    icon,
                    color
                ).respectFontBounds(true), null, null, null
        )

        return view
    }
}

class EditItemTagAdapter(
    private val removeTagCallback: (Tag) -> Unit = {},
    private val useWhiteTheme: Boolean = true
) :
    BaseRecyclerViewAdapter<EditItemTagAdapter.TagViewModel>(R.layout.item_edit_quest_tag) {

    data class TagViewModel(
        val name: String,
        val icon: IIcon,
        val tag: Tag,
        val iconColor: Color = tag.color
    ) : RecyclerViewViewModel {
        override val id: String
            get() = tag.id
    }

    override fun onBindViewModel(vm: TagViewModel, view: View, holder: SimpleViewHolder) {
        view.tagName.text = vm.name
        view.tagName.setTextColor(
            ContextCompat.getColor(
                view.context,
                if (useWhiteTheme) R.color.md_white else R.color.md_dark_text_87
            )
        )

        view.tagName.setCompoundDrawablesWithIntrinsicBounds(
            IconicsDrawable(view.context)
                .normalIcon(
                    vm.icon,
                    if (useWhiteTheme) R.color.md_white else AndroidColor.valueOf(vm.iconColor.name).color500
                ).respectFontBounds(true), null, null, null
        )

        view.tagRemove.setImageResource(if (useWhiteTheme) R.drawable.ic_clear_white_24dp else R.drawable.ic_clear_black_24dp)

        view.tagRemove.setOnClickListener(Debounce.clickListener {
            removeTagCallback(vm.tag)
        })
    }
}

class TagAdapter(
    private val removeTagCallback: (Tag) -> Unit,
    private val addTagCallback: (Tag) -> Unit
) : BaseRecyclerViewAdapter<TagAdapter.TagViewModel>(R.layout.item_choose_tag) {

    data class TagViewModel(
        val name: String,
        val icon: IIcon,
        val tag: Tag,
        @ColorRes val iconColor: Int,
        @DrawableRes val background: Int,
        val isSelected: Boolean,
        val canBeAdded: Boolean
    ) : RecyclerViewViewModel {
        override val id: String
            get() = tag.id
    }

    override fun onBindViewModel(vm: TagViewModel, view: View, holder: SimpleViewHolder) {

        view.tagIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .listItemIcon(
                    vm.icon,
                    vm.iconColor
                )
        )
        view.tagBackground.setBackgroundResource(vm.background)

        if (vm.isSelected) {
            view.setOnClickListener(Debounce.clickListener {
                removeTagCallback(vm.tag)
            })
        } else {
            if (vm.canBeAdded) {
                view.setOnClickListener(Debounce.clickListener {
                    addTagCallback(vm.tag)
                })
            } else {
                view.setOnClickListener(null)
            }
        }
    }

}