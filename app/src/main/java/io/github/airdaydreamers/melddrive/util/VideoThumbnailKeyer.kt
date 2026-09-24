package io.github.airdaydreamers.melddrive.util

import coil3.key.Keyer
import coil3.request.Options
import io.github.airdaydreamers.melddrive.data.model.VideoThumbnailModel

class VideoThumbnailKeyer : Keyer<VideoThumbnailModel> {
    override fun key(data: VideoThumbnailModel, options: Options): String {
        val file = data.file
        return "video_thumb_${file.storageType.name}_${data.serverId ?: -1L}_${file.path}_${file.lastModified}_${file.size}"
    }
}
