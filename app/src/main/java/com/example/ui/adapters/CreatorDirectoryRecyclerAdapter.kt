package com.example.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.data.CreatorProfile
import com.example.databinding.ItemCreatorDirectoryBinding

data class CreatorDirectoryUiItem(
    val creator: CreatorProfile,
    val isOnline: Boolean
)

class CreatorDirectoryRecyclerAdapter(
    private var palette: RecyclerThemePalette,
    private var onViewProfile: (CreatorProfile) -> Unit,
    private var onChat: (CreatorProfile) -> Unit,
    private var onAudioCall: (CreatorProfile) -> Unit,
    private var onVideoCall: (CreatorProfile) -> Unit
) : ListAdapter<CreatorDirectoryUiItem, CreatorDirectoryRecyclerAdapter.DirectoryViewHolder>(DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    fun updateEnvironment(
        newPalette: RecyclerThemePalette,
        onViewProfile: (CreatorProfile) -> Unit,
        onChat: (CreatorProfile) -> Unit,
        onAudioCall: (CreatorProfile) -> Unit,
        onVideoCall: (CreatorProfile) -> Unit
    ) {
        val paletteChanged = this.palette != newPalette
        this.palette = newPalette
        this.onViewProfile = onViewProfile
        this.onChat = onChat
        this.onAudioCall = onAudioCall
        this.onVideoCall = onVideoCall
        if (paletteChanged) {
            notifyDataSetChanged()
        }
    }

    override fun getItemId(position: Int): Long =
        getItem(position).creator.userId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryViewHolder {
        val binding = ItemCreatorDirectoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DirectoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DirectoryViewHolder, position: Int) {
        holder.bind(getItem(position), fullBind = true)
    }

    override fun onBindViewHolder(
        holder: DirectoryViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.bind(getItem(position), fullBind = false)
        }
    }

    override fun onViewRecycled(holder: DirectoryViewHolder) {
        super.onViewRecycled(holder)
        AvatarViewBinder.clearAvatar(holder.binding.creatorDirectoryAvatar)
    }

    inner class DirectoryViewHolder(
        val binding: ItemCreatorDirectoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentCreator: CreatorProfile? = null

        init {
            binding.root.setOnClickListener {
                currentCreator?.let { onViewProfile(it) }
            }
            binding.creatorDirectoryChatButton.setOnClickListener {
                currentCreator?.let { onChat(it) }
            }
            binding.creatorDirectoryAudioButton.setOnClickListener {
                currentCreator?.let { onAudioCall(it) }
            }
            binding.creatorDirectoryVideoButton.setOnClickListener {
                currentCreator?.let { onVideoCall(it) }
            }
        }

        fun bind(item: CreatorDirectoryUiItem, fullBind: Boolean) {
            val creator = item.creator
            currentCreator = creator
            val ctx = binding.root.context

            binding.root.tag = "creator_directory_row_${creator.userId}"
            binding.creatorDirectoryChatButton.tag = "directory_chat_${creator.userId}"
            binding.creatorDirectoryAudioButton.tag = "directory_audio_${creator.userId}"
            binding.creatorDirectoryVideoButton.tag = "directory_video_${creator.userId}"

            if (fullBind) {
                binding.root.background = roundedRippleBackground(
                    fillColor = applyAlpha(palette.surfaceVariant, 0.68f),
                    rippleBaseColor = palette.onSurface,
                    radiusPx = ctx.dpToPxF(18f)
                )
                val tonalRadius = ctx.dpToPxF(24f)
                binding.creatorDirectoryChatButton.background = roundedRippleBackground(
                    fillColor = palette.secondaryContainer,
                    rippleBaseColor = palette.onSecondaryContainer,
                    radiusPx = tonalRadius
                )
                binding.creatorDirectoryChatButton.setColorFilter(palette.onSecondaryContainer)

                binding.creatorDirectoryAudioButton.background = roundedRippleBackground(
                    fillColor = palette.secondaryContainer,
                    rippleBaseColor = palette.onSecondaryContainer,
                    radiusPx = tonalRadius
                )
                binding.creatorDirectoryAudioButton.setColorFilter(palette.onSecondaryContainer)

                binding.creatorDirectoryVideoButton.background = roundedRippleBackground(
                    fillColor = palette.secondaryContainer,
                    rippleBaseColor = palette.onSecondaryContainer,
                    radiusPx = tonalRadius
                )
                binding.creatorDirectoryVideoButton.setColorFilter(palette.onSecondaryContainer)
            }

            binding.creatorDirectoryName.text = creator.displayName
            binding.creatorDirectoryName.setTextColor(palette.onSurface)

            val statusWord = if (item.isOnline) "Online" else "Offline"
            binding.creatorDirectoryHandleStatus.text = "@${creator.handle} · $statusWord"
            binding.creatorDirectoryHandleStatus.setTextColor(
                if (item.isOnline) palette.primary else palette.onSurfaceVariant
            )

            val bioText = creator.bio?.trim().orEmpty()
            if (bioText.isNotEmpty()) {
                binding.creatorDirectoryBio.visibility = View.VISIBLE
                binding.creatorDirectoryBio.text = bioText
                binding.creatorDirectoryBio.setTextColor(palette.onSurfaceVariant)
            } else {
                binding.creatorDirectoryBio.visibility = View.GONE
            }

            binding.creatorDirectoryChatButton.contentDescription = "Message ${creator.displayName}"
            binding.creatorDirectoryAudioButton.contentDescription = "Audio call ${creator.displayName}"
            binding.creatorDirectoryVideoButton.contentDescription = "Video call ${creator.displayName}"

            AvatarViewBinder.bindAvatar(
                binding = binding.creatorDirectoryAvatar,
                palette = palette,
                displayName = creator.displayName,
                isOnline = item.isOnline,
                avatarSizeDp = 46,
                photoUrl = creator.photoUrl,
                avatarBase64 = creator.avatarBase64,
                indicatorTag = "directory_online_indicator_${creator.userId}"
            )
        }
    }

    companion object {
        private const val PAYLOAD_STATUS_ONLY = "status_only"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CreatorDirectoryUiItem>() {
            override fun areItemsTheSame(
                oldItem: CreatorDirectoryUiItem,
                newItem: CreatorDirectoryUiItem
            ): Boolean = oldItem.creator.userId == newItem.creator.userId

            override fun areContentsTheSame(
                oldItem: CreatorDirectoryUiItem,
                newItem: CreatorDirectoryUiItem
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: CreatorDirectoryUiItem,
                newItem: CreatorDirectoryUiItem
            ): Any? {
                val unchangedExceptStatus =
                    oldItem.creator.photoUrl == newItem.creator.photoUrl &&
                        oldItem.creator.avatarBase64 == newItem.creator.avatarBase64 &&
                        oldItem.creator.displayName == newItem.creator.displayName &&
                        oldItem.creator.handle == newItem.creator.handle &&
                        oldItem.creator.bio == newItem.creator.bio
                return if (unchangedExceptStatus && oldItem.isOnline != newItem.isOnline) {
                    PAYLOAD_STATUS_ONLY
                } else {
                    null
                }
            }
        }
    }
}
