package com.example.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.databinding.ItemChatThreadBinding
import com.example.databinding.ItemPeopleStripBinding

sealed interface ChatListRowItem {
    val stableId: String

    data class PeopleStripItem(
        val creators: List<PeopleStripUiItem>
    ) : ChatListRowItem {
        override val stableId: String = "__people_strip__"
    }

    data class ThreadRowItem(
        val thread: ChatThread,
        val isOnline: Boolean,
        val peerPhotoUrl: String?,
        val peerAvatarBase64: String?,
        val peerProfile: CreatorProfile?,
        val timeLabel: String,
        val previewText: String
    ) : ChatListRowItem {
        override val stableId: String = thread.id
    }
}

class ChatListRecyclerAdapter(
    private var palette: RecyclerThemePalette,
    private var onSelectChat: (ChatThread) -> Unit,
    private var onViewProfile: (CreatorProfile) -> Unit,
    private var onChatWithCreator: (CreatorProfile) -> Unit,
    private var onCallCreator: (CreatorProfile, String) -> Unit
) : ListAdapter<ChatListRowItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private val sharedPeoplePool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(0, 12)
    }

    init {
        setHasStableIds(true)
    }

    fun updateEnvironment(
        newPalette: RecyclerThemePalette,
        onSelectChat: (ChatThread) -> Unit,
        onViewProfile: (CreatorProfile) -> Unit,
        onChatWithCreator: (CreatorProfile) -> Unit,
        onCallCreator: (CreatorProfile, String) -> Unit
    ) {
        val paletteChanged = this.palette != newPalette
        this.palette = newPalette
        this.onSelectChat = onSelectChat
        this.onViewProfile = onViewProfile
        this.onChatWithCreator = onChatWithCreator
        this.onCallCreator = onCallCreator
        if (paletteChanged) {
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatListRowItem.PeopleStripItem -> VIEW_TYPE_PEOPLE_STRIP
        is ChatListRowItem.ThreadRowItem -> VIEW_TYPE_THREAD_ROW
    }

    override fun getItemId(position: Int): Long =
        getItem(position).stableId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PEOPLE_STRIP -> PeopleStripSectionViewHolder(
                ItemPeopleStripBinding.inflate(inflater, parent, false)
            )
            else -> ThreadRowViewHolder(
                ItemChatThreadBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListRowItem.PeopleStripItem -> (holder as PeopleStripSectionViewHolder).bind(item)
            is ChatListRowItem.ThreadRowItem -> (holder as ThreadRowViewHolder).bind(item, fullBind = true)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        when (val item = getItem(position)) {
            is ChatListRowItem.PeopleStripItem -> (holder as PeopleStripSectionViewHolder).bind(item)
            is ChatListRowItem.ThreadRowItem -> (holder as ThreadRowViewHolder).bind(item, fullBind = false)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ThreadRowViewHolder) {
            AvatarViewBinder.clearAvatar(holder.binding.chatThreadAvatar)
        }
    }

    inner class PeopleStripSectionViewHolder(
        val binding: ItemPeopleStripBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val stripAdapter = PeopleStripRecyclerAdapter(
            palette = palette,
            onViewProfile = onViewProfile,
            onChat = onChatWithCreator,
            onCall = onCallCreator
        )

        init {
            binding.root.tag = "people_strip_section"
            configureOptimizedRecyclerView(
                recyclerView = binding.peopleStripRecycler,
                orientation = RecyclerView.HORIZONTAL,
                viewCacheSize = 6,
                prefetchCount = 4,
                recycledViewPool = sharedPeoplePool
            )
            binding.peopleStripRecycler.addItemDecoration(
                SpacingItemDecoration(
                    horizontalEdgePx = 0,
                    topEdgePx = 0,
                    bottomEdgePx = 0,
                    itemGapPx = binding.root.context.dpToPx(10f),
                    orientation = RecyclerView.HORIZONTAL
                )
            )
            binding.peopleStripRecycler.adapter = stripAdapter
        }

        fun bind(item: ChatListRowItem.PeopleStripItem) {
            val ctx = binding.root.context
            binding.peopleStripHeading.text =
                ctx.getString(R.string.people_strip_heading_format, item.creators.size)
            binding.peopleStripHeading.setTextColor(palette.onSurface)
            binding.peopleStripSubtitle.setTextColor(palette.onSurfaceVariant)

            stripAdapter.updateEnvironment(
                newPalette = palette,
                onViewProfile = onViewProfile,
                onChat = onChatWithCreator,
                onCall = onCallCreator
            )
            stripAdapter.submitList(item.creators)
        }
    }

    inner class ThreadRowViewHolder(
        val binding: ItemChatThreadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: ChatListRowItem.ThreadRowItem? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.thread?.let { onSelectChat(it) }
            }
        }

        fun bind(item: ChatListRowItem.ThreadRowItem, fullBind: Boolean) {
            currentItem = item
            val thread = item.thread
            val ctx = binding.root.context

            binding.root.tag = "chat_thread_${thread.id}"

            if (fullBind) {
                binding.root.background = roundedRippleBackground(
                    fillColor = applyAlpha(palette.surfaceVariant, 0.68f),
                    rippleBaseColor = palette.onSurface,
                    radiusPx = ctx.dpToPxF(18f)
                )
                binding.chatThreadTypeBadge.background = roundedSolidDrawable(
                    fillColor = applyAlpha(palette.secondaryContainer, 0.7f),
                    radiusPx = ctx.dpToPxF(50f)
                )
                binding.chatThreadTypeBadge.setTextColor(palette.onSecondaryContainer)
            }

            binding.chatThreadTitle.text = thread.title
            binding.chatThreadTitle.setTextColor(palette.onSurface)

            if (item.timeLabel.isNotEmpty()) {
                binding.chatThreadTime.visibility = View.VISIBLE
                binding.chatThreadTime.text = item.timeLabel
                binding.chatThreadTime.setTextColor(palette.primary)
            } else {
                binding.chatThreadTime.visibility = View.GONE
            }

            val iconRes = when {
                thread.lastMessageText.startsWith("Photo:") -> R.drawable.ic_vec_image
                thread.lastMessageText.startsWith("Voice note") -> R.drawable.ic_vec_mic
                thread.lastMessageText.startsWith("Started ") -> R.drawable.ic_vec_video_call
                else -> 0
            }
            if (iconRes != 0) {
                binding.chatThreadPreviewIcon.visibility = View.VISIBLE
                binding.chatThreadPreviewIcon.setImageResource(iconRes)
                binding.chatThreadPreviewIcon.setColorFilter(palette.primary)
            } else {
                binding.chatThreadPreviewIcon.visibility = View.GONE
            }

            binding.chatThreadPreview.text = item.previewText
            binding.chatThreadPreview.setTextColor(palette.onSurfaceVariant)

            binding.chatThreadTypeBadge.text = if (thread.isGroup) {
                "Group · ${thread.participantIds.size}"
            } else {
                "Direct · ${thread.participantIds.size}"
            }

            val peersLabel = thread.participantNames
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
            if (!peersLabel.isNullOrBlank()) {
                binding.chatThreadPeers.visibility = View.VISIBLE
                binding.chatThreadPeers.text = peersLabel
                binding.chatThreadPeers.setTextColor(palette.onSurfaceVariant)
            } else {
                binding.chatThreadPeers.visibility = View.GONE
            }

            val peerProfile = item.peerProfile
            AvatarViewBinder.bindAvatar(
                binding = binding.chatThreadAvatar,
                palette = palette,
                displayName = thread.title,
                isOnline = item.isOnline,
                avatarSizeDp = 50,
                isGroup = thread.isGroup,
                photoUrl = item.peerPhotoUrl,
                avatarBase64 = item.peerAvatarBase64,
                indicatorTag = "chat_thread_online_indicator_${thread.id}",
                onClick = if (peerProfile != null) {
                    { onViewProfile(peerProfile) }
                } else {
                    null
                }
            )
        }
    }

    companion object {
        private const val VIEW_TYPE_PEOPLE_STRIP = 0
        private const val VIEW_TYPE_THREAD_ROW = 1
        private const val PAYLOAD_PARTIAL = "partial"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatListRowItem>() {
            override fun areItemsTheSame(
                oldItem: ChatListRowItem,
                newItem: ChatListRowItem
            ): Boolean = oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(
                oldItem: ChatListRowItem,
                newItem: ChatListRowItem
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: ChatListRowItem,
                newItem: ChatListRowItem
            ): Any? = when {
                oldItem is ChatListRowItem.PeopleStripItem && newItem is ChatListRowItem.PeopleStripItem -> {
                    PAYLOAD_PARTIAL
                }
                oldItem is ChatListRowItem.ThreadRowItem && newItem is ChatListRowItem.ThreadRowItem -> {
                    val avatarUnchanged =
                        oldItem.peerPhotoUrl == newItem.peerPhotoUrl &&
                            oldItem.peerAvatarBase64 == newItem.peerAvatarBase64 &&
                            oldItem.thread.isGroup == newItem.thread.isGroup
                    if (avatarUnchanged) PAYLOAD_PARTIAL else null
                }
                else -> null
            }
        }
    }
}
