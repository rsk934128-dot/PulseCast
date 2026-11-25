package com.example.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.data.CreatorProfile
import com.example.databinding.ItemPeopleStripCardBinding

data class PeopleStripUiItem(
    val creator: CreatorProfile,
    val isOnline: Boolean
)

class PeopleStripRecyclerAdapter(
    private var palette: RecyclerThemePalette,
    private var onViewProfile: (CreatorProfile) -> Unit,
    private var onChat: (CreatorProfile) -> Unit,
    private var onCall: (CreatorProfile, String) -> Unit
) : ListAdapter<PeopleStripUiItem, PeopleStripRecyclerAdapter.CardViewHolder>(DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    fun updateEnvironment(
        newPalette: RecyclerThemePalette,
        onViewProfile: (CreatorProfile) -> Unit,
        onChat: (CreatorProfile) -> Unit,
        onCall: (CreatorProfile, String) -> Unit
    ) {
        val paletteChanged = this.palette != newPalette
        this.palette = newPalette
        this.onViewProfile = onViewProfile
        this.onChat = onChat
        this.onCall = onCall
        if (paletteChanged) {
            notifyDataSetChanged()
        }
    }

    override fun getItemId(position: Int): Long =
        getItem(position).creator.userId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemPeopleStripCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position), fullBind = true)
    }

    override fun onBindViewHolder(
        holder: CardViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.bind(getItem(position), fullBind = false)
        }
    }

    override fun onViewRecycled(holder: CardViewHolder) {
        super.onViewRecycled(holder)
        AvatarViewBinder.clearAvatar(holder.binding.peopleCardAvatar)
    }

    inner class CardViewHolder(
        val binding: ItemPeopleStripCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentCreator: CreatorProfile? = null

        init {
            binding.root.setOnClickListener {
                currentCreator?.let { onViewProfile(it) }
            }
            binding.peopleQuickChatButton.setOnClickListener {
                currentCreator?.let { onChat(it) }
            }
            binding.peopleQuickAudioButton.setOnClickListener {
                currentCreator?.let { onCall(it, "audio") }
            }
            binding.peopleQuickVideoButton.setOnClickListener {
                currentCreator?.let { onCall(it, "video") }
            }
        }

        fun bind(item: PeopleStripUiItem, fullBind: Boolean) {
            val creator = item.creator
            currentCreator = creator
            val ctx = binding.root.context

            binding.root.tag = "people_strip_card_${creator.userId}"
            binding.peopleQuickChatButton.tag = "quick_chat_${creator.userId}"
            binding.peopleQuickAudioButton.tag = "quick_audio_${creator.userId}"
            binding.peopleQuickVideoButton.tag = "quick_video_${creator.userId}"

            if (fullBind) {
                binding.root.background = roundedRippleBackground(
                    fillColor = applyAlpha(palette.surfaceVariant, 0.72f),
                    rippleBaseColor = palette.onSurface,
                    radiusPx = ctx.dpToPxF(16f)
                )
                binding.peopleQuickChatButton.background = roundedRippleBackground(
                    fillColor = android.graphics.Color.TRANSPARENT,
                    rippleBaseColor = palette.primary,
                    radiusPx = ctx.dpToPxF(24f),
                    strokeWidthPx = ctx.dpToPx(1f),
                    strokeColor = palette.outline
                )
                binding.peopleQuickChatIcon.setColorFilter(palette.primary)
                binding.peopleQuickChatLabel.setTextColor(palette.primary)

                val tonalRadius = ctx.dpToPxF(24f)
                binding.peopleQuickAudioButton.background = roundedRippleBackground(
                    fillColor = palette.secondaryContainer,
                    rippleBaseColor = palette.onSecondaryContainer,
                    radiusPx = tonalRadius
                )
                binding.peopleQuickAudioButton.setColorFilter(palette.onSecondaryContainer)

                binding.peopleQuickVideoButton.background = roundedRippleBackground(
                    fillColor = palette.secondaryContainer,
                    rippleBaseColor = palette.onSecondaryContainer,
                    radiusPx = tonalRadius
                )
                binding.peopleQuickVideoButton.setColorFilter(palette.onSecondaryContainer)
            }

            binding.peopleCardName.text = creator.displayName
            binding.peopleCardName.setTextColor(palette.onSurface)

            val statusWord = if (item.isOnline) "Online" else "Offline"
            binding.peopleCardHandleStatus.text = "@${creator.handle} · $statusWord"
            binding.peopleCardHandleStatus.setTextColor(
                if (item.isOnline) palette.primary else palette.onSurfaceVariant
            )

            binding.peopleQuickAudioButton.contentDescription = "Audio call ${creator.displayName}"
            binding.peopleQuickVideoButton.contentDescription = "Video call ${creator.displayName}"

            AvatarViewBinder.bindAvatar(
                binding = binding.peopleCardAvatar,
                palette = palette,
                displayName = creator.displayName,
                isOnline = item.isOnline,
                avatarSizeDp = 40,
                photoUrl = creator.photoUrl,
                avatarBase64 = creator.avatarBase64,
                indicatorTag = "people_strip_online_indicator_${creator.userId}"
            )
        }
    }

    companion object {
        private const val PAYLOAD_STATUS_ONLY = "status_only"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PeopleStripUiItem>() {
            override fun areItemsTheSame(
                oldItem: PeopleStripUiItem,
                newItem: PeopleStripUiItem
            ): Boolean = oldItem.creator.userId == newItem.creator.userId

            override fun areContentsTheSame(
                oldItem: PeopleStripUiItem,
                newItem: PeopleStripUiItem
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: PeopleStripUiItem,
                newItem: PeopleStripUiItem
            ): Any? {
                val avatarUnchanged =
                    oldItem.creator.photoUrl == newItem.creator.photoUrl &&
                        oldItem.creator.avatarBase64 == newItem.creator.avatarBase64 &&
                        oldItem.creator.displayName == newItem.creator.displayName &&
                        oldItem.creator.handle == newItem.creator.handle
                return if (avatarUnchanged && oldItem.isOnline != newItem.isOnline) {
                    PAYLOAD_STATUS_ONLY
                } else {
                    null
                }
            }
        }
    }
}
