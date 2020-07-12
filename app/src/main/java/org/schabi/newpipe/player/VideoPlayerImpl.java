/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * PopupVideoPlayer.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.AnticipateInterpolator;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nostra13.universalimageloader.core.assist.FailReason;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.PlaybackParameterDialog;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.*;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.*;

import java.util.List;

import static android.content.Context.WINDOW_SERVICE;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static org.schabi.newpipe.player.MainPlayer.*;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.isTablet;
import static org.schabi.newpipe.util.AnimationUtils.Type.SLIDE_AND_ALPHA;
import static org.schabi.newpipe.util.AnimationUtils.animateRotation;
import static org.schabi.newpipe.util.AnimationUtils.animateView;
import static org.schabi.newpipe.util.ListHelper.getPopupResolutionIndex;
import static org.schabi.newpipe.util.ListHelper.getResolutionIndex;

/**
 * Unified UI for all players
 *
 * @author mauriciocolli
 */

public class VideoPlayerImpl extends VideoPlayer
        implements View.OnLayoutChangeListener,
        PlaybackParameterDialog.Callback,
        View.OnLongClickListener {
    private static final String TAG = ".VideoPlayerImpl";

    private static final float MAX_GESTURE_LENGTH = 0.75f;

    private TextView titleTextView;
    private TextView channelTextView;
    private RelativeLayout volumeRelativeLayout;
    private ProgressBar volumeProgressBar;
    private ImageView volumeImageView;
    private RelativeLayout brightnessRelativeLayout;
    private ProgressBar brightnessProgressBar;
    private ImageView brightnessImageView;
    private TextView resizingIndicator;
    private ImageButton queueButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;
    private ImageButton playWithKodi;
    private ImageButton openInBrowser;
    private ImageButton fullscreenButton;
    private ImageButton playerCloseButton;
    private ImageButton screenRotationButton;

    private ImageButton playPauseButton;
    private ImageButton playPreviousButton;
    private ImageButton playNextButton;

    private RelativeLayout queueLayout;
    private ImageButton itemsListCloseButton;
    private RecyclerView itemsList;
    private ItemTouchHelper itemTouchHelper;

    private boolean queueVisible;
    private MainPlayer.PlayerType playerType = MainPlayer.PlayerType.VIDEO;

    private ImageButton moreOptionsButton;
    private ImageButton shareButton;

    private View primaryControls;
    private View secondaryControls;

    private int maxGestureLength;

    private boolean audioOnly = false;
    private boolean isFullscreen = false;
    private boolean isVerticalVideo = false;
    boolean shouldUpdateOnProgress;

    private final MainPlayer service;
    private PlayerServiceEventListener fragmentListener;
    private PlayerEventListener activityListener;
    private GestureDetector gestureDetector;
    private final SharedPreferences defaultPreferences;
    private ContentObserver settingsContentObserver;
    @NonNull
    final private AudioPlaybackResolver resolver;

    private int cachedDuration;
    private String cachedDurationString;

    // Popup
    private WindowManager.LayoutParams popupLayoutParams;
    public WindowManager windowManager;

    private View closingOverlayView;
    private View closeOverlayView;
    private FloatingActionButton closeOverlayButton;

    public boolean isPopupClosing = false;

    static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    static final String POPUP_SAVED_X = "popup_saved_x";
    static final String POPUP_SAVED_Y = "popup_saved_y";
    private static final int MINIMUM_SHOW_EXTRA_WIDTH_DP = 300;
    private static final int IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
    private static final int ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;
    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;
    // Popup end


    @Override
    public void handleIntent(Intent intent) {
        if (intent.getStringExtra(VideoPlayer.PLAY_QUEUE_KEY) == null) return;

        final MainPlayer.PlayerType oldPlayerType = playerType;
        choosePlayerTypeFromIntent(intent);
        audioOnly = audioPlayerSelected();

        // We need to setup audioOnly before super(), see "sourceOf"
        super.handleIntent(intent);

        if (oldPlayerType != playerType && playQueue != null) {
            // If playerType changes from one to another we should reload the player
            // (to disable/enable video stream or to set quality)
            setRecovery();
            reload();
        }

        setupElementsVisibility();
        setupElementsSize();

        if (audioPlayerSelected()) {
            service.removeViewFromParent();
        } else if (popupPlayerSelected()) {
            getRootView().setVisibility(View.VISIBLE);
            initPopup();
            initPopupCloseOverlay();
        } else {
            getRootView().setVisibility(View.VISIBLE);
            initVideoPlayer();
        }

        onPlay();
    }

    VideoPlayerImpl(final MainPlayer service) {
        super("MainPlayer" + TAG, service);
        this.service = service;
        this.shouldUpdateOnProgress = true;
        this.windowManager = (WindowManager) service.getSystemService(WINDOW_SERVICE);
        this.defaultPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        this.resolver = new AudioPlaybackResolver(context, dataSource);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void initViews(View rootView) {
        super.initViews(rootView);
        this.titleTextView = rootView.findViewById(R.id.titleTextView);
        this.channelTextView = rootView.findViewById(R.id.channelTextView);
        this.volumeRelativeLayout = rootView.findViewById(R.id.volumeRelativeLayout);
        this.volumeProgressBar = rootView.findViewById(R.id.volumeProgressBar);
        this.volumeImageView = rootView.findViewById(R.id.volumeImageView);
        this.brightnessRelativeLayout = rootView.findViewById(R.id.brightnessRelativeLayout);
        this.brightnessProgressBar = rootView.findViewById(R.id.brightnessProgressBar);
        this.brightnessImageView = rootView.findViewById(R.id.brightnessImageView);
        this.resizingIndicator = rootView.findViewById(R.id.resizing_indicator);
        this.queueButton = rootView.findViewById(R.id.queueButton);
        this.repeatButton = rootView.findViewById(R.id.repeatButton);
        this.shuffleButton = rootView.findViewById(R.id.shuffleButton);
        this.playWithKodi = rootView.findViewById(R.id.playWithKodi);
        this.openInBrowser = rootView.findViewById(R.id.openInBrowser);
        this.fullscreenButton = rootView.findViewById(R.id.fullScreenButton);
        this.screenRotationButton = rootView.findViewById(R.id.screenRotationButton);
        this.playerCloseButton = rootView.findViewById(R.id.playerCloseButton);

        this.playPauseButton = rootView.findViewById(R.id.playPauseButton);
        this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton);
        this.playNextButton = rootView.findViewById(R.id.playNextButton);

        this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton);
        this.primaryControls = rootView.findViewById(R.id.primaryControls);
        this.secondaryControls = rootView.findViewById(R.id.secondaryControls);
        this.shareButton = rootView.findViewById(R.id.share);

        this.queueLayout = rootView.findViewById(R.id.playQueuePanel);
        this.itemsListCloseButton = rootView.findViewById(R.id.playQueueClose);
        this.itemsList = rootView.findViewById(R.id.playQueue);

        closingOverlayView = rootView.findViewById(R.id.closingOverlay);

        titleTextView.setSelected(true);
        channelTextView.setSelected(true);
    }

    @Override
    protected void setupSubtitleView(@NonNull SubtitleView view,
                                     final float captionScale,
                                     @NonNull final CaptionStyleCompat captionStyle) {
        if (popupPlayerSelected()) {
            float captionRatio = (captionScale - 1.0f) / 5.0f + 1.0f;
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio);
            view.setApplyEmbeddedStyles(captionStyle.equals(CaptionStyleCompat.DEFAULT));
            view.setStyle(captionStyle);
        } else {
            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
            final float captionRatioInverse = 20f + 4f * (1.0f - captionScale);
            view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) minimumLength / captionRatioInverse);
            view.setApplyEmbeddedStyles(captionStyle.equals(CaptionStyleCompat.DEFAULT));
            view.setStyle(captionStyle);
        }
    }

    /**
     * This method ensures that popup and main players have different look. We use one layout for both players and
     * need to decide what to show and what to hide. Additional measuring should be done inside {@link #setupElementsSize}.
     * {@link #setControlsSize} is used to adapt the UI to fullscreen mode, multiWindow, navBar, etc
     */
    private void setupElementsVisibility() {
        if (popupPlayerSelected()) {
            fullscreenButton.setVisibility(View.VISIBLE);
            screenRotationButton.setVisibility(View.GONE);
            getResizeView().setVisibility(View.GONE);
            getRootView().findViewById(R.id.metadataView).setVisibility(View.GONE);
            queueButton.setVisibility(View.GONE);
            moreOptionsButton.setVisibility(View.GONE);
            getTopControlsRoot().setOrientation(LinearLayout.HORIZONTAL);
            primaryControls.getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
            secondaryControls.setAlpha(1.0f);
            secondaryControls.setVisibility(View.VISIBLE);
            secondaryControls.setTranslationY(0);
            shareButton.setVisibility(View.GONE);
            playWithKodi.setVisibility(View.GONE);
            openInBrowser.setVisibility(View.GONE);
            playerCloseButton.setVisibility(View.GONE);
            getTopControlsRoot().bringToFront();
            getTopControlsRoot().setClickable(false);
            getTopControlsRoot().setFocusable(false);
            getBottomControlsRoot().bringToFront();
            onQueueClosed();
        } else {
            fullscreenButton.setVisibility(View.GONE);
            setupScreenRotationButton();
            getResizeView().setVisibility(View.VISIBLE);
            getRootView().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
            moreOptionsButton.setVisibility(View.VISIBLE);
            getTopControlsRoot().setOrientation(LinearLayout.VERTICAL);
            primaryControls.getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;
            secondaryControls.setVisibility(View.INVISIBLE);
            moreOptionsButton.setImageDrawable(service.getResources().getDrawable(
                    R.drawable.ic_expand_more_white_24dp));
            shareButton.setVisibility(View.VISIBLE);
            playWithKodi.setVisibility(
                    defaultPreferences.getBoolean(service.getString(R.string.show_play_with_kodi_key), false) ? View.VISIBLE : View.GONE);
            openInBrowser.setVisibility(View.VISIBLE);
            playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
            // Top controls have a large minHeight which is allows to drag the player down in fullscreen mode (just larger area
            // to make easy to locate by finger)
            getTopControlsRoot().setClickable(true);
            getTopControlsRoot().setFocusable(true);
        }
        if (!isFullscreen()) {
            titleTextView.setVisibility(View.GONE);
            channelTextView.setVisibility(View.GONE);
        } else {
            titleTextView.setVisibility(View.VISIBLE);
            channelTextView.setVisibility(View.VISIBLE);
        }

        animateRotation(moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    /**
     * Changes padding, size of elements based on player selected right now. Popup player has small padding in comparison with the
     * main player
     */
    private void setupElementsSize() {
        if (popupPlayerSelected()) {
            final int controlsPadding = service.getResources().getDimensionPixelSize(R.dimen.player_popup_controls_padding);
            final int buttonsPadding = service.getResources().getDimensionPixelSize(R.dimen.player_popup_buttons_padding);
            getTopControlsRoot().setPaddingRelative(controlsPadding, 0, controlsPadding, 0);
            getBottomControlsRoot().setPaddingRelative(controlsPadding, 0, controlsPadding, 0);
            getQualityTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
            getPlaybackSpeedTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
            getQualityTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
            getCaptionTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
            getQualityTextView().setMinimumWidth(0);
            getPlaybackSpeedTextView().setMinimumWidth(0);
        } else if (videoPlayerSelected()) {
            final int buttonsMinWidth = service.getResources().getDimensionPixelSize(R.dimen.player_main_buttons_min_width);
            final int playerTopPadding = service.getResources().getDimensionPixelSize(R.dimen.player_main_top_padding);
            final int controlsPadding = service.getResources().getDimensionPixelSize(R.dimen.player_main_controls_padding);
            final int buttonsPadding = service.getResources().getDimensionPixelSize(R.dimen.player_main_buttons_padding);
            getTopControlsRoot().setPaddingRelative(controlsPadding, playerTopPadding, controlsPadding, 0);
            getBottomControlsRoot().setPaddingRelative(controlsPadding, 0, controlsPadding, 0);
            getQualityTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
            getPlaybackSpeedTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
            getQualityTextView().setMinimumWidth(buttonsMinWidth);
            getPlaybackSpeedTextView().setMinimumWidth(buttonsMinWidth);
            getCaptionTextView().setPadding(buttonsPadding, buttonsPadding, buttonsPadding, buttonsPadding);
        }
    }

    @Override
    public void initListeners() {
        super.initListeners();

        final PlayerGestureListener listener = new PlayerGestureListener(this, service);
        gestureDetector = new GestureDetector(context, listener);
        getRootView().setOnTouchListener(listener);

        queueButton.setOnClickListener(this);
        repeatButton.setOnClickListener(this);
        shuffleButton.setOnClickListener(this);

        playPauseButton.setOnClickListener(this);
        playPreviousButton.setOnClickListener(this);
        playNextButton.setOnClickListener(this);

        moreOptionsButton.setOnClickListener(this);
        moreOptionsButton.setOnLongClickListener(this);
        shareButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        screenRotationButton.setOnClickListener(this);
        playWithKodi.setOnClickListener(this);
        openInBrowser.setOnClickListener(this);
        playerCloseButton.setOnClickListener(this);

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) { setupScreenRotationButton(); }
        };
        service.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
        getRootView().addOnLayoutChangeListener(this);
    }

    public AppCompatActivity getParentActivity() {
        // ! instanceof ViewGroup means that view was added via windowManager for Popup
        if (getRootView() == null || getRootView().getParent() == null || !(getRootView().getParent() instanceof ViewGroup))
            return null;

        final ViewGroup parent = (ViewGroup) getRootView().getParent();
        return (AppCompatActivity) parent.getContext();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // View
    //////////////////////////////////////////////////////////////////////////*/

    private void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private void setShuffleButton(final ImageButton shuffleButton, final boolean shuffled) {
        final int shuffleAlpha = shuffled ? 255 : 77;
        shuffleButton.setImageAlpha(shuffleAlpha);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Playback Parameters Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackParameterChanged(float playbackTempo, float playbackPitch, boolean playbackSkipSilence) {
        setPlaybackParameters(playbackTempo, playbackPitch, playbackSkipSilence);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        super.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        isVerticalVideo = width < height;
        prepareOrientation();
        setupScreenRotationButton();
    }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onRepeatModeChanged(int i) {
        super.onRepeatModeChanged(i);
        updatePlaybackButtons();
        updatePlayback();
        service.resetNotification();
        service.updateNotification(-1);
    }

    @Override
    public void onShuffleClicked() {
        super.onShuffleClicked();
        updatePlaybackButtons();
        updatePlayback();
    }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        super.onPlayerError(error);

        if (fragmentListener != null)
            fragmentListener.onPlayerError(error);
    }

    protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
        super.onMetadataChanged(tag);

        titleTextView.setText(tag.getMetadata().getName());
        channelTextView.setText(tag.getMetadata().getUploaderName());

        service.resetNotification();
        service.updateNotification(-1);
        updateMetadata();
    }

    @Override
    public void onPlaybackShutdown() {
        if (DEBUG) Log.d(TAG, "onPlaybackShutdown() called");
        // Override it because we don't want playerImpl destroyed
    }

    @Override
    public void onUpdateProgress(final int currentProgress, final int duration, final int bufferPercent) {
        super.onUpdateProgress(currentProgress, duration, bufferPercent);

        updateProgress(currentProgress, duration, bufferPercent);

        if (!shouldUpdateOnProgress || getCurrentState() == BasePlayer.STATE_COMPLETED
                || getCurrentState() == BasePlayer.STATE_PAUSED || getPlayQueue() == null)
            return;

        if (service.getBigNotRemoteView() != null) {
            if (cachedDuration != duration) {
                cachedDuration = duration;
                cachedDurationString = getTimeString(duration);
            }
            service.getBigNotRemoteView()
                    .setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            service.getBigNotRemoteView()
                    .setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + cachedDurationString);
        }
        if (service.getNotRemoteView() != null) {
            service.getNotRemoteView()
                    .setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
        }
        service.updateNotification(-1);
    }

    @Override
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        // For LiveStream or video/popup players we can use super() method
        // but not for audio player
        if (!audioOnly)
            return super.sourceOf(item, info);
        else {
            return resolver.resolve(info);
        }
    }

    @Override
    public void onPlayPrevious() {
        super.onPlayPrevious();
        triggerProgressUpdate();
    }

    @Override
    public void onPlayNext() {
        super.onPlayNext();
        triggerProgressUpdate();
    }

    @Override
    protected void initPlayback(@NonNull PlayQueue queue, int repeatMode, float playbackSpeed,
                                float playbackPitch, boolean playbackSkipSilence, boolean playOnReady) {
        super.initPlayback(queue, repeatMode, playbackSpeed, playbackPitch, playbackSkipSilence, playOnReady);
        updateQueue();
    }

    /*//////////////////////////////////////////////////////////////////////////
        // Player Overrides
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void toggleFullscreen() {
        if (DEBUG) Log.d(TAG, "toggleFullscreen() called");
        if (simpleExoPlayer == null || getCurrentMetadata() == null) return;

        if (popupPlayerSelected()) {
            setRecovery();
            service.removeViewFromParent();
            Intent intent = NavigationHelper.getPlayerIntent(
                    service,
                    MainActivity.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackSkipSilence(),
                    null,
                    true
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Constants.KEY_SERVICE_ID, getCurrentMetadata().getMetadata().getServiceId());
            intent.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
            intent.putExtra(Constants.KEY_URL, getVideoUrl());
            intent.putExtra(Constants.KEY_TITLE, getVideoTitle());
            intent.putExtra(VideoDetailFragment.AUTO_PLAY, true);
            service.onDestroy();
            context.startActivity(intent);
            return;
        } else {
            if (fragmentListener == null) return;

            isFullscreen = !isFullscreen;
            setControlsSize();
            fragmentListener.onFullscreenStateChanged(isFullscreen());
        }

        if (!isFullscreen()) {
            titleTextView.setVisibility(View.GONE);
            channelTextView.setVisibility(View.GONE);
            playerCloseButton.setVisibility(videoPlayerSelected() ? View.VISIBLE : View.GONE);
        } else {
            titleTextView.setVisibility(View.VISIBLE);
            channelTextView.setVisibility(View.VISIBLE);
            playerCloseButton.setVisibility(View.GONE);
        }
        setupScreenRotationButton();
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v.getId() == playPauseButton.getId()) {
            onPlayPause();

        } else if (v.getId() == playPreviousButton.getId()) {
            onPlayPrevious();

        } else if (v.getId() == playNextButton.getId()) {
            onPlayNext();

        } else if (v.getId() == queueButton.getId()) {
            onQueueClicked();
            return;
        } else if (v.getId() == repeatButton.getId()) {
            onRepeatClicked();
            return;
        } else if (v.getId() == shuffleButton.getId()) {
            onShuffleClicked();
            return;
        } else if (v.getId() == moreOptionsButton.getId()) {
            onMoreOptionsClicked();

        } else if (v.getId() == shareButton.getId()) {
            onShareClicked();

        } else if (v.getId() == playWithKodi.getId()) {
            onPlayWithKodiClicked();

        } else if (v.getId() == openInBrowser.getId()) {
            onOpenInBrowserClicked();

        } else if (v.getId() == fullscreenButton.getId()) {
            toggleFullscreen();

        } else if (v.getId() == screenRotationButton.getId()) {
            if (!isVerticalVideo) fragmentListener.onScreenRotationButtonClicked();
            else toggleFullscreen();

        } else if (v.getId() == playerCloseButton.getId()) {
            service.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER));
        }

        if (getCurrentState() != STATE_COMPLETED) {
            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            animateView(getControlsRoot(), true, DEFAULT_CONTROLS_DURATION, 0, () -> {
                if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                    if (v.getId() == playPauseButton.getId()) hideControls(0, 0);
                    else hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
                }
            });
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v.getId() == moreOptionsButton.getId() && isFullscreen()) {
            fragmentListener.onMoreOptionsLongClicked();
            hideControls(0, 0);
            hideSystemUIIfNeeded();
        }
        return true;
    }

    private void onQueueClicked() {
        queueVisible = true;

        hideSystemUIIfNeeded();
        buildQueue();
        updatePlaybackButtons();

        getControlsRoot().setVisibility(View.INVISIBLE);
        animateView(queueLayout, SLIDE_AND_ALPHA,true,
                DEFAULT_CONTROLS_DURATION);

        itemsList.scrollToPosition(playQueue.getIndex());
    }

    public void onQueueClosed() {
        if (!queueVisible) return;

        animateView(queueLayout, SLIDE_AND_ALPHA,false,
                DEFAULT_CONTROLS_DURATION, 0, () -> {
                    // Even when queueLayout is GONE it receives touch events and ruins normal behavior of the app. This line fixes it
                    queueLayout.setTranslationY(-queueLayout.getHeight() * 5);
                });
        queueVisible = false;
    }

    private void onMoreOptionsClicked() {
        if (DEBUG) Log.d(TAG, "onMoreOptionsClicked() called");

        final boolean isMoreControlsVisible = secondaryControls.getVisibility() == View.VISIBLE;

        animateRotation(moreOptionsButton, DEFAULT_CONTROLS_DURATION,
                isMoreControlsVisible ? 0 : 180);
        animateView(secondaryControls, SLIDE_AND_ALPHA, !isMoreControlsVisible,
                DEFAULT_CONTROLS_DURATION, 0,
                () -> {
                    // Fix for a ripple effect on background drawable. When view returns from GONE state it takes more
                    // milliseconds than returning from INVISIBLE state. And the delay makes ripple background end to fast
                    if (isMoreControlsVisible) secondaryControls.setVisibility(View.INVISIBLE);
                });
        showControls(DEFAULT_CONTROLS_DURATION);
    }

    private void onShareClicked() {
        // share video at the current time (youtube.com/watch?v=ID&t=SECONDS)
        ShareUtils.shareUrl(service,
                getVideoTitle(),
                getVideoUrl() + "&t=" + getPlaybackSeekBar().getProgress() / 1000);
    }

    private void onPlayWithKodiClicked() {
        if (getCurrentMetadata() == null) return;

        try {
            NavigationHelper.playWithKore(getParentActivity(), Uri.parse(
                    getCurrentMetadata().getMetadata().getUrl().replace("https", "http")));
        } catch (Exception e) {
            if (DEBUG) Log.i(TAG, "Failed to start kore", e);
            showInstallKoreDialog(getParentActivity());
        }
    }

    private void onOpenInBrowserClicked() {
        if (getCurrentMetadata() == null) return;

        ShareUtils.openUrlInBrowser(getParentActivity(), getCurrentMetadata().getMetadata().getOriginalUrl());
    }

    private static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (DialogInterface dialog, int which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> {
                });
        builder.create().show();
    }

    private void setupScreenRotationButton() {
        final boolean orientationLocked = PlayerHelper.globalScreenOrientationLocked(service);
        final boolean tabletInLandscape = isTablet(service) && service.isLandscape();
        final boolean showButton = videoPlayerSelected() && (orientationLocked || isVerticalVideo || tabletInLandscape);
        screenRotationButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
        screenRotationButton.setImageDrawable(service.getResources().getDrawable(
                isFullscreen() ? R.drawable.ic_fullscreen_exit_white : R.drawable.ic_fullscreen_white));
    }

    private void prepareOrientation() {
        final boolean orientationLocked = PlayerHelper.globalScreenOrientationLocked(service);
        if (orientationLocked && isFullscreen() && service.isLandscape() == isVerticalVideo && fragmentListener != null)
            fragmentListener.onScreenRotationButtonClicked();
    }

    @Override
    public void onPlaybackSpeedClicked() {
        if (videoPlayerSelected()) {
            PlaybackParameterDialog
                    .newInstance(getPlaybackSpeed(), getPlaybackPitch(), getPlaybackSkipSilence(), this)
                    .show(getParentActivity().getSupportFragmentManager(), null);
        } else {
            super.onPlaybackSpeedClicked();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);
        if (wasPlaying()) showControlsThenHide();
    }

    @Override
    public void onDismiss(PopupMenu menu) {
        super.onDismiss(menu);
        if (isPlaying()) hideControls(DEFAULT_CONTROLS_DURATION, 0);
    }

    @Override
    public void onLayoutChange(final View view, int l, int t, int r, int b,
                               int ol, int ot, int or, int ob) {
        if (l != ol || t != ot || r != or || b != ob) {
            // Use smaller value to be consistent between screen orientations
            // (and to make usage easier)
            int width = r - l, height = b - t;
            int min = Math.min(width, height);
            maxGestureLength = (int) (min * MAX_GESTURE_LENGTH);

            if (DEBUG) Log.d(TAG, "maxGestureLength = " + maxGestureLength);

            volumeProgressBar.setMax(maxGestureLength);
            brightnessProgressBar.setMax(maxGestureLength);

            setInitialGestureValues();
            queueLayout.getLayoutParams().height = height - queueLayout.getTop();

            if (popupPlayerSelected()) {
                float widthDp = Math.abs(r - l) / service.getResources().getDisplayMetrics().density;
                final int visibility = widthDp > MINIMUM_SHOW_EXTRA_WIDTH_DP ? View.VISIBLE : View.GONE;
                secondaryControls.setVisibility(visibility);
            }
        }
    }

    @Override
    protected int nextResizeMode(int currentResizeMode) {
        final int newResizeMode;
        switch (currentResizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
            default:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
        }

        storeResizeMode(newResizeMode);
        return newResizeMode;
    }

    private void storeResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode) {
        defaultPreferences.edit()
                .putInt(service.getString(R.string.last_resize_mode), resizeMode)
                .apply();
    }

    private void restoreResizeMode() {
        setResizeMode(defaultPreferences.getInt(
                service.getString(R.string.last_resize_mode), AspectRatioFrameLayout.RESIZE_MODE_FIT));
    }

    @Override
    protected VideoPlaybackResolver.QualityResolver getQualityResolver() {
        return new VideoPlaybackResolver.QualityResolver() {
            @Override
            public int getDefaultResolutionIndex(List<VideoStream> sortedVideos) {
                return videoPlayerSelected() ? ListHelper.getDefaultResolutionIndex(context, sortedVideos)
                        : ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
            }

            @Override
            public int getOverrideResolutionIndex(List<VideoStream> sortedVideos,
                                                  String playbackQuality) {
                return videoPlayerSelected() ? getResolutionIndex(context, sortedVideos, playbackQuality)
                        : getPopupResolutionIndex(context, sortedVideos, playbackQuality);
            }
        };
    }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

    private void animatePlayButtons(final boolean show, final int duration) {
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        if (playQueue.getIndex() > 0)
            animateView(playPreviousButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        if (playQueue.getIndex() + 1 < playQueue.getStreams().size())
            animateView(playNextButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);

    }

    @Override
    public void changeState(int state) {
        super.changeState(state);
        updatePlayback();
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
        animatePlayButtons(false, 100);
        getRootView().setKeepScreenOn(false);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        getRootView().setKeepScreenOn(true);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animatePlayButtons(true, 200);
        });

        updateWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS);
        checkLandscape();
        getRootView().setKeepScreenOn(true);

        service.getLockManager().acquireWifiAndCpu();
        service.resetNotification();
        service.updateNotification(R.drawable.ic_pause_white);

        service.startForeground(NOTIFICATION_ID, service.getNotBuilder().build());
    }

    @Override
    public void onPaused() {
        super.onPaused();
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
            animatePlayButtons(true, 200);
        });

        updateWindowFlags(IDLE_WINDOW_FLAGS);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);

        // Remove running notification when user don't want music (or video in popup) to be played in background
        if (!minimizeOnPopupEnabled() && !backgroundPlaybackEnabled() && videoPlayerSelected())
            service.stopForeground(true);

        getRootView().setKeepScreenOn(false);

        service.getLockManager().releaseWifiAndCpu();
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        animatePlayButtons(false, 100);
        getRootView().setKeepScreenOn(true);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }


    @Override
    public void onCompleted() {
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_replay_white);
            animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
        });
        getRootView().setKeepScreenOn(false);

        updateWindowFlags(IDLE_WINDOW_FLAGS);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_replay_white);

        service.getLockManager().releaseWifiAndCpu();

        super.onCompleted();
    }

    @Override
    public void destroy() {
        super.destroy();

        service.getContentResolver().unregisterContentObserver(settingsContentObserver);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast Receiver
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void setupBroadcastReceiver(IntentFilter intentFilter) {
        super.setupBroadcastReceiver(intentFilter);
        if (DEBUG) Log.d(TAG, "setupBroadcastReceiver() called with: intentFilter = [" + intentFilter + "]");

        intentFilter.addAction(ACTION_CLOSE);
        intentFilter.addAction(ACTION_PLAY_PAUSE);
        intentFilter.addAction(ACTION_OPEN_CONTROLS);
        intentFilter.addAction(ACTION_REPEAT);
        intentFilter.addAction(ACTION_PLAY_PREVIOUS);
        intentFilter.addAction(ACTION_PLAY_NEXT);
        intentFilter.addAction(ACTION_FAST_REWIND);
        intentFilter.addAction(ACTION_FAST_FORWARD);

        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED);
        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);

        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
    }

    @Override
    public void onBroadcastReceived(Intent intent) {
        super.onBroadcastReceived(intent);
        if (intent == null || intent.getAction() == null)
            return;

        if (DEBUG) Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");

        switch (intent.getAction()) {
            case ACTION_CLOSE:
                service.onDestroy();
                break;
            case ACTION_PLAY_NEXT:
                onPlayNext();
                break;
            case ACTION_PLAY_PREVIOUS:
                onPlayPrevious();
                break;
            case ACTION_FAST_FORWARD:
                onFastForward();
                break;
            case ACTION_FAST_REWIND:
                onFastRewind();
                break;
            case ACTION_PLAY_PAUSE:
                onPlayPause();
                break;
            case ACTION_REPEAT:
                onRepeatClicked();
                break;
            case VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED:
                useVideoSource(true);
                break;
            case VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED:
                // This will be called when user goes to another app/activity, turns off a screen.
                // We don't want to interrupt playback and don't want to see notification if player is stopped.
                // Next lines of code will enable background playback if needed
                if (videoPlayerSelected() && (isPlaying() || isLoading())) {
                    if (backgroundPlaybackEnabled()) {
                        useVideoSource(false);
                    } else if (minimizeOnPopupEnabled()) {
                        setRecovery();
                        NavigationHelper.playOnPopupPlayer(getParentActivity(), playQueue, true);
                    } else {
                        onPause();
                    }
                }
                break;
            case Intent.ACTION_CONFIGURATION_CHANGED:
                // The only situation I need to re-calculate elements sizes is when a user rotates a device from landscape to landscape
                // because in that case the controls should be aligned to another side of a screen. The problem is when user leaves
                // the app and returns back (while the app in landscape) Android reports via DisplayMetrics that orientation is
                // portrait and it gives wrong sizes calculations. Let's skip re-calculation in every case but landscape
                final boolean reportedOrientationIsLandscape = service.isLandscape();
                final boolean actualOrientationIsLandscape = context.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE;
                if (reportedOrientationIsLandscape && actualOrientationIsLandscape) setControlsSize();
                // Close it because when changing orientation from portrait (in fullscreen mode) the size of queue layout can be
                // larger than the screen size
                onQueueClosed();
                break;
            case Intent.ACTION_SCREEN_ON:
                shouldUpdateOnProgress = true;
                // Interrupt playback only when screen turns on and user is watching video in popup player
                // Same action for video player will be handled in ACTION_VIDEO_FRAGMENT_RESUMED event
                if (backgroundPlaybackEnabled() && popupPlayerSelected() && (isPlaying() || isLoading()))
                    useVideoSource(true);
                break;
            case Intent.ACTION_SCREEN_OFF:
                shouldUpdateOnProgress = false;
                // Interrupt playback only when screen turns off with popup player working
                if (backgroundPlaybackEnabled() && popupPlayerSelected() && (isPlaying() || isLoading()))
                    useVideoSource(false);
                break;
        }
        service.resetNotification();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail Loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
        super.onLoadingComplete(imageUri, view, loadedImage);
        // rebuild notification here since remote view does not release bitmaps,
        // causing memory leaks
        service.resetNotification();
        service.updateNotification(-1);
    }

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        super.onLoadingFailed(imageUri, view, failReason);
        service.resetNotification();
        service.updateNotification(-1);
    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {
        super.onLoadingCancelled(imageUri, view);
        service.resetNotification();
        service.updateNotification(-1);
    }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

    private void setInitialGestureValues() {
        if (getAudioReactor() != null) {
            final float currentVolumeNormalized = (float) getAudioReactor().getVolume() / getAudioReactor().getMaxVolume();
            volumeProgressBar.setProgress((int) (volumeProgressBar.getMax() * currentVolumeNormalized));
        }
    }

    private void choosePlayerTypeFromIntent(final Intent intent) {
        // If you want to open popup from the app just include Constants.POPUP_ONLY into an extra
        if (intent.getIntExtra(PLAYER_TYPE, PLAYER_TYPE_VIDEO) == PLAYER_TYPE_AUDIO) {
            playerType = MainPlayer.PlayerType.AUDIO;
        } else if (intent.getIntExtra(PLAYER_TYPE, PLAYER_TYPE_VIDEO) == PLAYER_TYPE_POPUP) {
            playerType = MainPlayer.PlayerType.POPUP;
        } else {
            playerType = MainPlayer.PlayerType.VIDEO;
        }
    }

    public boolean backgroundPlaybackEnabled() {
        return PlayerHelper.getMinimizeOnExitAction(service) == MINIMIZE_ON_EXIT_MODE_BACKGROUND;
    }

    public boolean minimizeOnPopupEnabled() {
        return PlayerHelper.getMinimizeOnExitAction(service) == PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
    }

    public boolean audioPlayerSelected() {
        return playerType == MainPlayer.PlayerType.AUDIO;
    }

    public boolean videoPlayerSelected() {
        return playerType == MainPlayer.PlayerType.VIDEO;
    }

    public boolean popupPlayerSelected() {
        return playerType == MainPlayer.PlayerType.POPUP;
    }

    public boolean isPlayerStopped() {
        return getPlayer() == null || getPlayer().getPlaybackState() == SimpleExoPlayer.STATE_IDLE;
    }

    private int distanceFromCloseButton(final MotionEvent popupMotionEvent) {
        final int closeOverlayButtonX = closeOverlayButton.getLeft() + closeOverlayButton.getWidth() / 2;
        final int closeOverlayButtonY = closeOverlayButton.getTop() + closeOverlayButton.getHeight() / 2;

        final float fingerX = popupLayoutParams.x + popupMotionEvent.getX();
        final float fingerY = popupLayoutParams.y + popupMotionEvent.getY();

        return (int) Math.sqrt(Math.pow(closeOverlayButtonX - fingerX, 2) + Math.pow(closeOverlayButtonY - fingerY, 2));
    }

    private float getClosingRadius() {
        final int buttonRadius = closeOverlayButton.getWidth() / 2;
        // 20% wider than the button itself
        return buttonRadius * 1.2f;
    }

    public boolean isInsideClosingRadius(final MotionEvent popupMotionEvent) {
        return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius();
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    @Override
    public void showControlsThenHide() {
        if (queueVisible) return;

        showOrHideButtons();
        showSystemUIPartially();
        super.showControlsThenHide();
    }

    @Override
    public void showControls(final long duration) {
        if (queueVisible) return;

        showOrHideButtons();
        showSystemUIPartially();
        super.showControls(duration);
    }

    @Override
    public void hideControls(final long duration, final long delay) {
        if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");

        showOrHideButtons();

        getControlsVisibilityHandler().removeCallbacksAndMessages(null);
        getControlsVisibilityHandler().postDelayed(() ->
                        animateView(getControlsRoot(), false, duration, 0,
                                this::hideSystemUIIfNeeded), delay
        );
    }

    private void showOrHideButtons() {
        if (playQueue == null)
            return;

        playPreviousButton.setVisibility(playQueue.getIndex() == 0 ? View.INVISIBLE : View.VISIBLE);
        playNextButton.setVisibility(playQueue.getIndex() + 1 == playQueue.getStreams().size() ? View.INVISIBLE : View.VISIBLE);
        queueButton.setVisibility(playQueue.getStreams().size() <= 1 || popupPlayerSelected() ? View.GONE : View.VISIBLE);
    }

    private void showSystemUIPartially() {
        if (isFullscreen() && getParentActivity() != null) {
            final int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            getParentActivity().getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
    }

    @Override
    public void hideSystemUIIfNeeded() {
        if (fragmentListener != null)
            fragmentListener.hideSystemUiIfNeeded();
    }

    /**
     * Measures width and height of controls visible on screen. It ensures that controls will be side-by-side with
     * NavigationBar and notches but not under them. Tablets have only bottom NavigationBar
     */
    public void setControlsSize() {
        final Point size = new Point();
        final Display display = getRootView().getDisplay();
        if (display == null || !videoPlayerSelected()) return;
        // This method will give a correct size of a usable area of a window.
        // It doesn't include NavigationBar, notches, etc.
        display.getSize(size);

        final int width = isFullscreen ? (service.isLandscape() ? size.x : size.y) : ViewGroup.LayoutParams.MATCH_PARENT;
        final int gravity = isFullscreen ? (display.getRotation() == Surface.ROTATION_90 ? Gravity.START : Gravity.END) : Gravity.TOP;

        getTopControlsRoot().getLayoutParams().width = width;
        final RelativeLayout.LayoutParams topParams = ((RelativeLayout.LayoutParams) getTopControlsRoot().getLayoutParams());
        topParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
        topParams.removeRule(RelativeLayout.ALIGN_PARENT_END);
        topParams.addRule(gravity == Gravity.END ? RelativeLayout.ALIGN_PARENT_END : RelativeLayout.ALIGN_PARENT_START);
        getTopControlsRoot().requestLayout();

        getBottomControlsRoot().getLayoutParams().width = width;
        final RelativeLayout.LayoutParams bottomParams = ((RelativeLayout.LayoutParams) getBottomControlsRoot().getLayoutParams());
        bottomParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
        bottomParams.removeRule(RelativeLayout.ALIGN_PARENT_END);
        bottomParams.addRule(gravity == Gravity.END ? RelativeLayout.ALIGN_PARENT_END : RelativeLayout.ALIGN_PARENT_START);
        getBottomControlsRoot().requestLayout();

        final ViewGroup controlsRoot = getRootView().findViewById(R.id.playbackWindowRoot);
        // In tablet navigationBar located at the bottom of the screen. And the situations when we need to set custom height is
        // in fullscreen mode in tablet in non-multiWindow mode or with vertical video. Other than that MATCH_PARENT is good
        final boolean navBarAtTheBottom = PlayerHelper.isTablet(service) || !service.isLandscape();
        controlsRoot.getLayoutParams().height = isFullscreen && !isInMultiWindow() && navBarAtTheBottom
                ? size.y
                : ViewGroup.LayoutParams.MATCH_PARENT;
        controlsRoot.requestLayout();

        final int topPadding = isFullscreen && !isInMultiWindow() ? getStatusBarHeight() : 0;
        getRootView().findViewById(R.id.playbackWindowRoot).setPadding(0, topPadding, 0, 0);
        getRootView().findViewById(R.id.playbackWindowRoot).requestLayout();
    }

    /**
     * @return statusBar height that was found inside system resources or default value if no value was provided inside resources
     */
    private int getStatusBarHeight() {
        int statusBarHeight = 0;
        final int resourceId = service.getResources().getIdentifier("status_bar_height_landscape", "dimen", "android");
        if (resourceId > 0) statusBarHeight = service.getResources().getDimensionPixelSize(resourceId);
        if (statusBarHeight == 0) {
            // Some devices provide wrong value for status bar height in landscape mode, this is workaround
            final DisplayMetrics metrics = getRootView().getResources().getDisplayMetrics();
            statusBarHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, metrics);
        }
        return statusBarHeight;
    }

    /**
     * @return true if main player is attached to activity and activity inside multiWindow mode
     */
    private boolean isInMultiWindow() {
        final AppCompatActivity parent = getParentActivity();
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && parent != null && parent.isInMultiWindowMode();
    }

    private void updatePlaybackButtons() {
        if (repeatButton == null || shuffleButton == null ||
                simpleExoPlayer == null || playQueue == null) return;

        setRepeatModeButton(repeatButton, getRepeatMode());
        setShuffleButton(shuffleButton, playQueue.isShuffled());
    }

    public void checkLandscape() {
        final AppCompatActivity parent = getParentActivity();
        final boolean videoInLandscapeButNotInFullscreen = service.isLandscape() && !isFullscreen() && videoPlayerSelected() && !audioOnly;
        final boolean playingState = getCurrentState() != STATE_COMPLETED && getCurrentState() != STATE_PAUSED;
        if (parent != null && videoInLandscapeButNotInFullscreen && playingState && !PlayerHelper.isTablet(service))
            toggleFullscreen();

        setControlsSize();
    }

    private void buildQueue() {
        itemsList.setAdapter(playQueueAdapter);
        itemsList.setClickable(true);
        itemsList.setLongClickable(true);

        itemsList.clearOnScrollListeners();
        itemsList.addOnScrollListener(getQueueScrollListener());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        playQueueAdapter.setSelectedListener(getOnSelectedListener());

        itemsListCloseButton.setOnClickListener(view -> onQueueClosed());
    }

    public void useVideoSource(final boolean video) {
        if (playQueue == null || audioOnly == !video || audioPlayerSelected())
            return;

        audioOnly = !video;
        // When a user returns from background controls could be hidden but systemUI will be shown 100%. Hide it
        if (!audioOnly && !isControlsVisible()) hideSystemUIIfNeeded();
        setRecovery();
        reload();
    }

    private OnScrollBelowItemsListener getQueueScrollListener() {
        return new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(RecyclerView recyclerView) {
                if (playQueue != null && !playQueue.isComplete()) {
                    playQueue.fetch();
                } else if (itemsList != null) {
                    itemsList.clearOnScrollListeners();
                }
            }
        };
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new PlayQueueItemTouchCallback() {
            @Override
            public void onMove(int sourceIndex, int targetIndex) {
                if (playQueue != null) playQueue.move(sourceIndex, targetIndex);
            }

            @Override
            public void onSwiped(int index) {
                if (index != -1) playQueue.remove(index);
            }
        };
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(PlayQueueItem item, View view) {
                onSelected(item);
            }

            @Override
            public void held(PlayQueueItem item, View view) {
                final int index = playQueue.indexOf(item);
                if (index != -1) playQueue.remove(index);
            }

            @Override
            public void onStartDrag(PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
            }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");

        // Popup is already added to windowManager
        if (popupHasParent()) return;

        updateScreenSize();

        final boolean popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(service);
        final float defaultSize = service.getResources().getDimension(R.dimen.popup_default_width);
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        popupWidth = popupRememberSizeAndPos ? sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) : defaultSize;
        popupHeight = getMinimumVideoHeight(popupWidth);

        popupLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) popupHeight,
                popupLayoutParamType(),
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT);
        popupLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        popupLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        getSurfaceView().setHeights((int) popupHeight, (int) popupHeight);

        final int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        final int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        popupLayoutParams.x = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_X, centerX) : centerX;
        popupLayoutParams.y = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_Y, centerY) : centerY;

        checkPopupPositionBounds();

        getLoadingPanel().setMinimumWidth(popupLayoutParams.width);
        getLoadingPanel().setMinimumHeight(popupLayoutParams.height);

        service.removeViewFromParent();
        windowManager.addView(getRootView(), popupLayoutParams);

        // Popup doesn't have aspectRatio selector, using FIT automatically
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopupCloseOverlay() {
        if (DEBUG) Log.d(TAG, "initPopupCloseOverlay() called");

        // closeOverlayView is already added to windowManager
        if (closeOverlayView != null) return;

        closeOverlayView = View.inflate(service, R.layout.player_popup_close_overlay, null);
        closeOverlayButton = closeOverlayView.findViewById(R.id.closeButton);

        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        final WindowManager.LayoutParams closeOverlayLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                popupLayoutParamType(),
                flags,
                PixelFormat.TRANSLUCENT);
        closeOverlayLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        closeOverlayLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        closeOverlayButton.setVisibility(View.GONE);
        windowManager.addView(closeOverlayView, closeOverlayLayoutParams);
    }

    private void initVideoPlayer() {
        restoreResizeMode();
        getRootView().setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Popup utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * @see #checkPopupPositionBounds(float, float)
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean checkPopupPositionBounds() {
        return checkPopupPositionBounds(screenWidth, screenHeight);
    }

    /**
     * Check if {@link #popupLayoutParams}' position is within a arbitrary boundary that goes from (0,0) to (boundaryWidth,
     * boundaryHeight).
     * <p>
     * If it's out of these boundaries, {@link #popupLayoutParams}' position is changed and {@code true} is returned
     * to represent this change.
     *
     * @return if the popup was out of bounds and have been moved back to it
     */
    public boolean checkPopupPositionBounds(final float boundaryWidth, final float boundaryHeight) {
        if (DEBUG) {
            Log.d(TAG, "checkPopupPositionBounds() called with: boundaryWidth = ["
                    + boundaryWidth + "], boundaryHeight = [" + boundaryHeight + "]");
        }

        if (popupLayoutParams.x < 0) {
            popupLayoutParams.x = 0;
            return true;
        } else if (popupLayoutParams.x > boundaryWidth - popupLayoutParams.width) {
            popupLayoutParams.x = (int) (boundaryWidth - popupLayoutParams.width);
            return true;
        }

        if (popupLayoutParams.y < 0) {
            popupLayoutParams.y = 0;
            return true;
        } else if (popupLayoutParams.y > boundaryHeight - popupLayoutParams.height) {
            popupLayoutParams.y = (int) (boundaryHeight - popupLayoutParams.height);
            return true;
        }

        return false;
    }

    public void savePositionAndSize() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        sharedPreferences.edit().putInt(POPUP_SAVED_X, popupLayoutParams.x).apply();
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, popupLayoutParams.y).apply();
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, popupLayoutParams.width).apply();
    }

    private float getMinimumVideoHeight(final float width) {
        //if (DEBUG) Log.d(TAG, "getMinimumVideoHeight() called with: width = [" + width + "], returned: " + height);
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
    }

    public void updateScreenSize() {
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        if (DEBUG)
            Log.d(TAG, "updateScreenSize() called > screenWidth = " + screenWidth + ", screenHeight = " + screenHeight);

        popupWidth = service.getResources().getDimension(R.dimen.popup_default_width);
        popupHeight = getMinimumVideoHeight(popupWidth);

        minimumWidth = service.getResources().getDimension(R.dimen.popup_minimum_width);
        minimumHeight = getMinimumVideoHeight(minimumWidth);

        maximumWidth = screenWidth;
        maximumHeight = screenHeight;
    }

    public void updatePopupSize(int width, int height) {
        if (DEBUG) Log.d(TAG, "updatePopupSize() called with: width = [" + width + "], height = [" + height + "]");

        if (popupLayoutParams == null || windowManager == null || getParentActivity() != null || getRootView().getParent() == null)
            return;

        width = (int) (width > maximumWidth ? maximumWidth : width < minimumWidth ? minimumWidth : width);

        if (height == -1) height = (int) getMinimumVideoHeight(width);
        else height = (int) (height > maximumHeight ? maximumHeight : height < minimumHeight ? minimumHeight : height);

        popupLayoutParams.width = width;
        popupLayoutParams.height = height;
        popupWidth = width;
        popupHeight = height;
        getSurfaceView().setHeights((int) popupHeight, (int) popupHeight);

        if (DEBUG) Log.d(TAG, "updatePopupSize() updated values:  width = [" + width + "], height = [" + height + "]");
        windowManager.updateViewLayout(getRootView(), popupLayoutParams);
    }

    private void updateWindowFlags(final int flags) {
        if (popupLayoutParams == null || windowManager == null || getParentActivity() != null || getRootView().getParent() == null)
            return;

        popupLayoutParams.flags = flags;
        windowManager.updateViewLayout(getRootView(), popupLayoutParams);
    }

    private int popupLayoutParamType() {
        return Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_PHONE :
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Misc
    //////////////////////////////////////////////////////////////////////////*/

    public void closePopup() {
        if (DEBUG) Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing);
        if (isPopupClosing) return;
        isPopupClosing = true;

        savePlaybackState();
        windowManager.removeView(getRootView());

        animateOverlayAndFinishService();
    }

    public void removePopupFromView() {
        final boolean isCloseOverlayHasParent = closeOverlayView != null && closeOverlayView.getParent() != null;
        if (popupHasParent())
            windowManager.removeView(getRootView());
        if (isCloseOverlayHasParent)
            windowManager.removeView(closeOverlayView);
    }

    private void animateOverlayAndFinishService() {
        final int targetTranslationY = (int) (closeOverlayButton.getRootView().getHeight() - closeOverlayButton.getY());

        closeOverlayButton.animate().setListener(null).cancel();
        closeOverlayButton.animate()
                .setInterpolator(new AnticipateInterpolator())
                .translationY(targetTranslationY)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        end();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        end();
                    }

                    private void end() {
                        windowManager.removeView(closeOverlayView);
                        closeOverlayView = null;

                        service.onDestroy();
                    }
                }).start();
    }

    private boolean popupHasParent() {
        final View root = getRootView();
        return root != null && root.getLayoutParams() instanceof WindowManager.LayoutParams && root.getParent() != null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Manipulations with listener
    ///////////////////////////////////////////////////////////////////////////

    public void setFragmentListener(final PlayerServiceEventListener listener) {
        fragmentListener = listener;
        updateMetadata();
        updatePlayback();
        triggerProgressUpdate();
    }

    public void removeFragmentListener(final PlayerServiceEventListener listener) {
        if (fragmentListener == listener) {
            fragmentListener = null;
        }
    }

    void setActivityListener(final PlayerEventListener listener) {
        activityListener = listener;
        updateMetadata();
        updatePlayback();
        triggerProgressUpdate();
    }

    void removeActivityListener(final PlayerEventListener listener) {
        if (activityListener == listener) {
            activityListener = null;
        }
    }

    private void updateQueue() {
        if (fragmentListener != null && playQueue != null) {
            fragmentListener.onQueueUpdate(playQueue);
        }
        if (activityListener != null && playQueue != null) {
            activityListener.onQueueUpdate(playQueue);
        }
    }

    private void updateMetadata() {
        if (fragmentListener != null && getCurrentMetadata() != null) {
            fragmentListener.onMetadataUpdate(getCurrentMetadata().getMetadata(), playQueue);
        }
        if (activityListener != null && getCurrentMetadata() != null) {
            activityListener.onMetadataUpdate(getCurrentMetadata().getMetadata(), playQueue);
        }
    }

    private void updatePlayback() {
        if (fragmentListener != null && simpleExoPlayer != null && playQueue != null) {
            fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
        }
        if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
            activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), getPlaybackParameters());
        }
    }

    private void updateProgress(final int currentProgress, final int duration, final int bufferPercent) {
        if (fragmentListener != null) {
            fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
        if (activityListener != null) {
            activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
    }

    void stopActivityBinding() {
        if (fragmentListener != null) {
            fragmentListener.onServiceStopped();
            fragmentListener = null;
        }
        if (activityListener != null) {
            activityListener.onServiceStopped();
            activityListener = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////

    public RelativeLayout getVolumeRelativeLayout() {
        return volumeRelativeLayout;
    }

    public ProgressBar getVolumeProgressBar() {
        return volumeProgressBar;
    }

    public ImageView getVolumeImageView() {
        return volumeImageView;
    }

    public RelativeLayout getBrightnessRelativeLayout() {
        return brightnessRelativeLayout;
    }

    public ProgressBar getBrightnessProgressBar() {
        return brightnessProgressBar;
    }

    public ImageView getBrightnessImageView() {
        return brightnessImageView;
    }

    public ImageButton getPlayPauseButton() {
        return playPauseButton;
    }

    public int getMaxGestureLength() {
        return maxGestureLength;
    }

    public TextView getResizingIndicator() {
        return resizingIndicator;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }

    public WindowManager.LayoutParams getPopupLayoutParams() {
        return popupLayoutParams;
    }

    public float getScreenWidth() {
        return screenWidth;
    }

    public float getScreenHeight() {
        return screenHeight;
    }

    public float getPopupWidth() {
        return popupWidth;
    }

    public float getPopupHeight() {
        return popupHeight;
    }

    public void setPopupWidth(final float width) {
        popupWidth = width;
    }

    public void setPopupHeight(final float height) {
        popupHeight = height;
    }

    public View getCloseOverlayButton() {
        return closeOverlayButton;
    }

    public View getClosingOverlayView() {
        return closingOverlayView;
    }
}
