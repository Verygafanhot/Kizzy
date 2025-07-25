/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GetCurrentlyPlayingMedia.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.get_current_data.media

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.blankj.utilcode.util.AppUtils
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.Constants.APPLICATION_ID
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetCurrentPlayingMedia @Inject constructor(
    private val metadataResolver: MetadataResolver,
    private val componentName: ComponentName,
    @ApplicationContext private val context: Context
) {
    object Assets {
        val PLAY = "app-assets/$APPLICATION_ID/1300361266212241430.png";
        val PAUSE = "app-assets/$APPLICATION_ID/1300361619490209802.png";
        val STOP = "app-assets/$APPLICATION_ID/1300361702621188160.png";
    }

    private fun getPlaybackStateIcon(playbackState: Int): RpcImage {
        return when (playbackState) {
            PlaybackState.STATE_PLAYING -> RpcImage.DiscordImage(Assets.PLAY)
            PlaybackState.STATE_PAUSED -> RpcImage.DiscordImage(Assets.PAUSE)
            PlaybackState.STATE_STOPPED -> RpcImage.DiscordImage(Assets.STOP)
            else -> RpcImage.DiscordImage(Assets.PAUSE)
        }
    }

    operator fun invoke(): CommonRpc {
        var largeIcon: RpcImage? = null
        var smallIcon: RpcImage? = null
        var smallText: String? = null
        var timestamps: Timestamps? = null
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = mediaSessionManager.getActiveSessions(componentName)
        for (mediaController in sessions) {
            // If the app is not enabled for media rpc, skip it
            if (!Prefs.isMediaAppEnabled(mediaController.packageName)) {
                continue
            }

            if (
                Prefs[Prefs.MEDIA_RPC_HIDE_ON_PAUSE, false] &&
                (
                    mediaController.playbackState?.state == PlaybackState.STATE_PAUSED ||
                    mediaController.playbackState?.state == PlaybackState.STATE_STOPPED
                )
            ) {
                continue
            }

            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val appName = AppUtils.getAppName(mediaController.packageName)
            val author =
                if (Prefs[Prefs.MEDIA_RPC_ARTIST_NAME, false])
                metadata?.let { metadataResolver.getArtistOrAuthor(it) }
                else null
            val album =
                if (Prefs[Prefs.MEDIA_RPC_ALBUM_NAME, false])
                metadata?.let { metadataResolver.getAlbum(it) }
                else null
            val bitmap = metadata?.let { metadataResolver.getCoverArt(it) }
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            duration?.let {
                if (it != 0L && mediaController.playbackState?.state == PlaybackState.STATE_PLAYING) timestamps = Timestamps(
                    end = System.currentTimeMillis() + duration - (mediaController.playbackState?.position ?: 0L),
                    start = System.currentTimeMillis() - (mediaController.playbackState?.position ?: 0L)
                )
            }
            if (title != null) {
                largeIcon =
                    if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) RpcImage.ApplicationIcon(
                        mediaController.packageName, context
                    ) else null
                if (bitmap != null) {
                    smallIcon = largeIcon
                    smallText = appName
                    largeIcon = RpcImage.BitmapImage(
                        context = context,
                        bitmap = bitmap,
                        packageName = mediaController.packageName,
                        // <Main artist>|<Album or Title>
                        title = "${metadata.let { metadataResolver.getAlbumArtists(it) }}|${metadata.let { metadataResolver.getAlbum(it) } ?: title}"
                    )
                }

                if (Prefs[Prefs.MEDIA_RPC_SHOW_PLAYBACK_STATE, false]) {
                    smallIcon = getPlaybackStateIcon(mediaController.playbackState?.state ?: PlaybackState.STATE_PAUSED)
                    smallText = null
                }

                return if (Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, false]) {
                    CommonRpc(
                        name = title,
                        details = author,
                        state = album,
                        largeImage = largeIcon,
                        smallImage = smallIcon,
                        largeText = appName,
                        smallText = smallText,
                        packageName = "$title::${mediaController.packageName}",
                        time = timestamps.takeIf { it != null }
                    )
                } else {
                    CommonRpc(
                        name = appName,
                        details = title,
                        state = author,
                        largeImage = largeIcon,
                        smallImage = smallIcon,
                        largeText = album,
                        smallText = smallText,
                        packageName = "$title::${mediaController.packageName}",
                        time = timestamps.takeIf { it != null }
                    )
                }
            }
        }
        return CommonRpc()
    }
}