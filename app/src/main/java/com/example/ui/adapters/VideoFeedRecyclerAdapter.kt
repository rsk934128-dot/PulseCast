package com.example.ui.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.databinding.ItemVideoFeedCardBinding
import com.example.media.formatDuration

data class VideoFeedUiItem(
    val video: PublishedVideo,
    val authorProfile: CreatorProfile?,
    val isAuthorOnline: Boolean,
    val isLiked: Boolean,
    val isLikeUpdating: Boolean,
    val isCopied: Boolean
)

class VideoFeedRecyclerAdapter(
    private var palette: RecyclerThemePalette,
    private var onAuthorClick: (PublishedVideo) -> Unit,
    private var onToggleLike: (PublishedVideo) -> Unit,
    private var onShareClick: (PublishedVideo) -> Unit,
    private var onVideoClick: (PublishedVideo) -> Unit
) : ListAdapter<VideoFeedUiItem, VideoFeedRecyclerAdapter.VideoViewHolder>(DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    fun updateEnvironment(
        newPalette: RecyclerThemePalette,
        onAuthorClick: (PublishedVideo) -> Unit,
        onToggleLike: (PublishedVideo) -> Unit,
        onShareClick: (PublishedVideo) -> Unit,
        onVideoClick: (PublishedVideo) -> Unit
    ) {
        val paletteChanged = this.palette != newPalette
        this.palette = newPalette
        this.onAuthorClick = onAuthorClick
        this.onToggleLike = onToggleLike
        this.onShareClick = onShareClick
        this.onVideoClick = onVideoClick
        if (paletteChanged) {
            notifyDataSetChanged()
        }
    }

    override fun getItemId(position: Int): Long =
        getItem(position).video.id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoFeedCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position), payloads = emptyList())
    }

    override fun onBindViewHolder(
        holder: VideoViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bind(getItem(position), payloads = payloads)
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        Base64BitmapCache.cancelPendingDecode(holder.binding.videoCoverImage)
        holder.binding.videoCoverImage.setImageDrawable(null)
        AvatarViewBinder.clearAvatar(holder.binding.videoAuthorAvatar)
    }

    inner class VideoViewHolder(
        val binding: ItemVideoFeedCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: VideoFeedUiItem? = null

        init {
            val ctx = binding.root.context
            binding.root.applyRoundedCornerOutline(ctx.dpToPxF(20f))
            binding.videoCoverContainer.applyTopRoundedCornerOutline(ctx.dpToPxF(20f))
            binding.videoAuthorContainer.applyRoundedCornerOutline(ctx.dpToPxF(10f))
            binding.videoLikeButton.applyRoundedCornerOutline(ctx.dpToPxF(50f))
            binding.videoShareButton.applyRoundedCornerOutline(ctx.dpToPxF(50f))

            binding.videoCoverScrim.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(android.graphics.Color.TRANSPARENT, applyAlpha(android.graphics.Color.BLACK, 0.55f))
            )
            binding.videoDurationBadge.background = roundedSolidDrawable(
                fillColor = applyAlpha(android.graphics.Color.BLACK, 0.75f),
                radiusPx = ctx.dpToPxF(8f)
            )
            binding.videoPlayCircle.background = circleSolidDrawable(
                fillColor = applyAlpha(android.graphics.Color.BLACK, 0.55f)
            )
            binding.videoPlayIcon.setColorFilter(android.graphics.Color.WHITE)

            binding.root.setOnClickListener {
                currentItem?.video?.let { onVideoClick(it) }
            }
            binding.videoAuthorContainer.setOnClickListener {
                currentItem?.video?.let { onAuthorClick(it) }
            }
            binding.videoLikeButton.setOnClickListener {
                val item = currentItem ?: return@setOnClickListener
                if (!item.isLikeUpdating) {
                    onToggleLike(item.video)
                }
            }
            binding.videoShareButton.setOnClickListener {
                currentItem?.video?.let { onShareClick(it) }
            }
        }

        fun bind(item: VideoFeedUiItem, payloads: List<Any>) {
            currentItem = item
            val video = item.video
            val ctx = binding.root.context
            val isPartialPayload = payloads.isNotEmpty()

            binding.root.tag = "video_card_${video.id}"
            binding.videoAuthorContainer.tag = "feed_author_${video.id}"
            binding.videoLikeButton.tag = "feed_like_button_${video.id}"
            binding.videoLikeCount.tag = "feed_like_count_${video.id}"
            binding.videoShareButton.tag = "feed_share_button_${video.id}"
            binding.videoShareLabel.tag = "feed_share_label_${video.id}"

            if (!isPartialPayload) {
                binding.root.background = roundedRippleBackground(
                    fillColor = applyAlpha(palette.surfaceVariant, 0.72f),
                    rippleBaseColor = palette.onSurface,
                    radiusPx = ctx.dpToPxF(20f)
                )
                binding.videoAuthorContainer.background = roundedRippleBackground(
                    fillColor = android.graphics.Color.TRANSPARENT,
                    rippleBaseColor = palette.primary,
                    radiusPx = ctx.dpToPxF(10f)
                )
                binding.videoCategoryBadge.background = roundedSolidDrawable(
                    fillColor = applyAlpha(palette.primaryContainer, 0.9f),
                    radiusPx = ctx.dpToPxF(8f)
                )
                binding.videoCategoryBadge.setTextColor(palette.onPrimaryContainer)
                binding.videoCategoryBadge.text = video.category.uppercase()

                binding.videoDurationBadge.text = formatDuration(video.durationSec)
                binding.videoPlayIcon.contentDescription = "Play ${video.title}"

                binding.videoTitleText.text = video.title
                binding.videoTitleText.setTextColor(palette.onSurface)

                if (!video.description.isNullOrBlank()) {
                    binding.videoDescriptionText.visibility = View.VISIBLE
                    binding.videoDescriptionText.text = video.description
                    binding.videoDescriptionText.setTextColor(palette.onSurfaceVariant)
                } else {
                    binding.videoDescriptionText.visibility = View.GONE
                }

                binding.videoAuthorName.text = video.authorName
                binding.videoAuthorName.setTextColor(palette.primary)

                binding.videoCommentIcon.setColorFilter(palette.onSurfaceVariant)
                binding.videoCommentCount.setTextColor(palette.onSurfaceVariant)

                bindCoverArt(video)
            }

            binding.videoCommentCount.text = "${video.commentsCount}"

            if (!isPartialPayload || payloads.any { it == PAYLOAD_AUTHOR_ONLINE || it == PAYLOAD_ALL_META }) {
                AvatarViewBinder.bindAvatar(
                    binding = binding.videoAuthorAvatar,
                    palette = palette,
                    displayName = video.authorName,
                    isOnline = item.isAuthorOnline,
                    avatarSizeDp = 28,
                    photoUrl = item.authorProfile?.photoUrl,
                    avatarBase64 = item.authorProfile?.avatarBase64
                )
            }

            bindLikeState(item)
            bindShareState(item)
        }

        private fun bindCoverArt(video: PublishedVideo) {
            val fallbackRes = when (video.coverKey) {
                "thumb_2" -> R.drawable.img_video_thumb_2
                else -> R.drawable.img_video_thumb_1
            }
            binding.videoCoverImage.contentDescription = video.title
            if (!video.thumbnailBase64.isNullOrBlank()) {
                val targetWidthPx = binding.root.context.resources.displayMetrics.widthPixels.coerceAtMost(960)
                Base64BitmapCache.decodeInto(
                    imageView = binding.videoCoverImage,
                    base64 = video.thumbnailBase64,
                    targetMaxPx = targetWidthPx,
                    preferRgb565 = true
                ) { loaded ->
                    if (!loaded) {
                        binding.videoCoverImage.setImageResource(fallbackRes)
                    }
                }
            } else {
                Base64BitmapCache.cancelPendingDecode(binding.videoCoverImage)
                binding.videoCoverImage.setImageResource(fallbackRes)
            }
        }

        private fun bindLikeState(item: VideoFeedUiItem) {
            val video = item.video
            val ctx = binding.root.context
            binding.videoLikeButton.isEnabled = !item.isLikeUpdating
            binding.videoLikeButton.contentDescription = if (item.isLiked) {
                ctx.getString(R.string.feed_unlike_video, video.title)
            } else {
                ctx.getString(R.string.feed_like_video, video.title)
            }
            binding.videoLikeButton.background = roundedRippleBackground(
                fillColor = if (item.isLiked) {
                    applyAlpha(palette.tertiaryContainer, 0.72f)
                } else {
                    android.graphics.Color.TRANSPARENT
                },
                rippleBaseColor = palette.tertiary,
                radiusPx = ctx.dpToPxF(50f)
            )
            binding.videoLikeIcon.setImageResource(
                if (item.isLiked) R.drawable.ic_vec_favorite else R.drawable.ic_vec_favorite_border
            )
            binding.videoLikeIcon.setColorFilter(
                if (item.isLiked) palette.tertiary else palette.onSurfaceVariant
            )
            binding.videoLikeCount.text = "${video.likesCount}"
            binding.videoLikeCount.setTextColor(
                if (item.isLiked) palette.onTertiaryContainer else palette.onSurfaceVariant
            )
        }

        private fun bindShareState(item: VideoFeedUiItem) {
            val video = item.video
            val ctx = binding.root.context
            binding.videoShareButton.contentDescription =
                ctx.getString(R.string.feed_share_video_cd, video.title)
            binding.videoShareButton.background = roundedRippleBackground(
                fillColor = if (item.isCopied) {
                    applyAlpha(palette.primaryContainer, 0.85f)
                } else {
                    android.graphics.Color.TRANSPARENT
                },
                rippleBaseColor = palette.primary,
                radiusPx = ctx.dpToPxF(50f)
            )
            binding.videoShareIcon.setImageResource(
                if (item.isCopied) R.drawable.ic_vec_check else R.drawable.ic_vec_share
            )
            binding.videoShareIcon.setColorFilter(
                if (item.isCopied) palette.primary else palette.onSurfaceVariant
            )
            binding.videoShareLabel.text = ctx.getString(
                if (item.isCopied) R.string.feed_share_video_copied else R.string.feed_share_video
            )
            binding.videoShareLabel.setTextColor(
                if (item.isCopied) palette.onPrimaryContainer else palette.onSurfaceVariant
            )
        }
    }

    companion object {
        private const val PAYLOAD_INTERACTION = "interaction"
        private const val PAYLOAD_AUTHOR_ONLINE = "author_online"
        private const val PAYLOAD_ALL_META = "all_meta"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<VideoFeedUiItem>() {
            override fun areItemsTheSame(
                oldItem: VideoFeedUiItem,
                newItem: VideoFeedUiItem
            ): Boolean = oldItem.video.id == newItem.video.id

            override fun areContentsTheSame(
                oldItem: VideoFeedUiItem,
                newItem: VideoFeedUiItem
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: VideoFeedUiItem,
                newItem: VideoFeedUiItem
            ): Any? {
                val mediaAndTextUnchanged =
                    oldItem.video.title == newItem.video.title &&
                        oldItem.video.description == newItem.video.description &&
                        oldItem.video.category == newItem.video.category &&
                        oldItem.video.durationSec == newItem.video.durationSec &&
                        oldItem.video.thumbnailBase64 == newItem.video.thumbnailBase64 &&
                        oldItem.video.coverKey == newItem.video.coverKey &&
                        oldItem.video.authorName == newItem.video.authorName
                if (!mediaAndTextUnchanged) return null
                return if (oldItem.authorProfile != newItem.authorProfile ||
                    oldItem.isAuthorOnline != newItem.isAuthorOnline
                ) {
                    PAYLOAD_ALL_META
                } else {
                    PAYLOAD_INTERACTION
                }
            }
        }
    }
}
