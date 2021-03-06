/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.tonlyshy.fmmusicb.modules.main.view;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.tonlyshy.fmmusicb.R;
import cn.tonlyshy.fmmusicb.modules.music.service.MusicService;
import cn.tonlyshy.fmmusicb.utils.ImageLoader;
import cn.tonlyshy.fmmusicb.utils.Loghelper;

public class BrowseFragment extends Fragment {

    private static final String TAG = BrowseFragment.class.getSimpleName();

    public static final String ARG_MEDIA_ID = "media_id";

    /**
     * Interface between BrowseFragment and MusicPlayerActivity.
     */
    public interface FragmentDataHelper {
        void onMediaItemSelected(MediaBrowserCompat.MediaItem item, boolean isPlaying);
    }

    // The mediaId to be used for subscribing for children using the MediaBrowser.
    private String mMediaId;

    private MediaBrowserCompat mMediaBrowser;
    private BrowseAdapter mBrowserAdapter;

    private MediaBrowserCompat.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback() {

                @Override
                public void onChildrenLoaded(String parentId,
                                             List<MediaBrowserCompat.MediaItem> children) {
                    mBrowserAdapter.clear();
                    mBrowserAdapter.notifyDataSetInvalidated();
                    for (MediaBrowserCompat.MediaItem item : children) {
                        mBrowserAdapter.add(item);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                    loadBitMap(children);
                }

                @Override
                public void onError(String id) {

                }
            };

    private MediaBrowserCompat.ConnectionCallback mConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "onConnected: session token " + mMediaBrowser.getSessionToken());

                    if (mMediaId == null) {
                        mMediaId = mMediaBrowser.getRoot();
                    }
                    mMediaBrowser.subscribe(mMediaId, mSubscriptionCallback);
                    try {
                        MediaControllerCompat mediaController =
                                new MediaControllerCompat(getActivity(),
                                        mMediaBrowser.getSessionToken());
                        MediaControllerCompat.setMediaController(getActivity(), mediaController);

                        // Register a Callback to stay in sync
                        mediaController.registerCallback(mControllerCallback);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to connect to MediaController", e);
                    }
                }

                @Override
                public void onConnectionFailed() {
                    Log.e(TAG, "onConnectionFailed");
                }

                @Override
                public void onConnectionSuspended() {
                    Log.d(TAG, "onConnectionSuspended");
                    MediaControllerCompat mediaController = MediaControllerCompat
                            .getMediaController(getActivity());
                    if (mediaController != null) {
                        mediaController.unregisterCallback(mControllerCallback);
                        MediaControllerCompat.setMediaController(getActivity(), null);
                    }
                }
            };

    private MediaControllerCompat.Callback mControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    if (metadata != null) {
                        mBrowserAdapter.setCurrentMediaMetadata(metadata);
                    }
                }

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    mBrowserAdapter.setPlaybackState(state);
                    mBrowserAdapter.notifyDataSetChanged();
                }
            };

    public static BrowseFragment newInstance(String mediaId) {
        Bundle args = new Bundle();
        args.putString(ARG_MEDIA_ID, mediaId);
        BrowseFragment fragment = new BrowseFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_list, container, false);

        mBrowserAdapter = new BrowseAdapter(getActivity());

        ListView listView = (ListView) rootView.findViewById(R.id.list_view);
        listView.setAdapter(mBrowserAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MediaBrowserCompat.MediaItem item = mBrowserAdapter.getItem(position);
                boolean isPlaying = item.getMediaId().equals(mBrowserAdapter.getPlayingMediaId());
                try {
                    FragmentDataHelper listener = (FragmentDataHelper) getActivity();
                    listener.onMediaItemSelected(item, isPlaying);
                } catch (ClassCastException ex) {
                    Log.e(TAG, "Exception trying to cast to FragmentDataHelper", ex);
                }
            }
        });

        Bundle args = getArguments();
        mMediaId = args.getString(ARG_MEDIA_ID, null);

        mMediaBrowser = new MediaBrowserCompat(getActivity(),
                new ComponentName(getActivity(), MusicService.class),
                mConnectionCallback, null);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMediaBrowser.disconnect();
    }

    // An adapter for showing the list of browsed MediaItem's
    private static class BrowseAdapter extends ArrayAdapter<MediaBrowserCompat.MediaItem> {
        private String mCurrentMediaId;
        private PlaybackStateCompat mPlaybackState;
        private Bitmap bitmap;

        public BrowseAdapter(Context context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowserCompat.MediaItem>());
        }

        @Nullable
        public String getPlayingMediaId() {
            boolean isPlaying = mPlaybackState != null
                    && mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING;
            return isPlaying ? mCurrentMediaId : null;
        }

        private void setCurrentMediaMetadata(MediaMetadataCompat mediaMetadata) {
            mCurrentMediaId = mediaMetadata != null
                    ? mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                    : null;
        }

        private void setPlaybackState(PlaybackStateCompat playbackState) {
            mPlaybackState = playbackState;
        }

        static class ViewHolder {
            ImageView mImageView;
            TextView mTitleView;
            TextView mDescriptionView;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            ViewHolder holder;

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.media_list_item, parent, false);
                holder = new ViewHolder();
                holder.mImageView = (ImageView) convertView.findViewById(R.id.iv_album_pic);
                //holder.mImageView.setVisibility(View.GONE);
                holder.mTitleView = (TextView) convertView.findViewById(R.id.tv_title);
                holder.mDescriptionView = (TextView) convertView.findViewById(R.id.tv_artist);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            MediaBrowserCompat.MediaItem item = getItem(position);
            holder.mImageView.setImageBitmap(bitmap);
            holder.mTitleView.setText(item.getDescription().getTitle());
            holder.mDescriptionView.setText(item.getDescription().getDescription());
            return convertView;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            notifyDataSetChanged();
        }
    }

    private void loadBitMap(List<MediaBrowserCompat.MediaItem> children){
        int i=0;
        for (MediaBrowserCompat.MediaItem item : children) {
            fetchLocal(i++,item);
        }
        mBrowserAdapter.notifyDataSetChanged();
    }

    private void fetchLocal(int position,MediaBrowserCompat.MediaItem item){
        ImageLoader.fetchLocal(getContext(),position, item.getDescription().getMediaUri(),
                new ImageLoader.FetchListener() {
                    @Override
                    public void onFetched(int position,Uri artUrl, Bitmap bitmap) {
                        Loghelper.d("BrowseFragment","onFetched %s",position+"");
                        //mBrowserAdapter.getItem(position)=mBrowserAdapter.getItem(position).
                    }

                    @Override
                    public void onError(int position,Uri artUrl, Exception e) {

                    }
                });
    }
}
