package com.example.audiorecorder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiorecorder.databinding.FragmentAudioRecorderBinding

class AudioRecordingAdapter(private val clickListener: AudioRecordingClickListener) :
    ListAdapter<AudioRecording, AudioRecordingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioRecordingViewHolder {
        val binding = FragmentAudioRecorderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AudioRecordingViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: AudioRecordingViewHolder, position: Int) {
        val recording = getItem(position)
        holder.bind(recording)
    }

    fun updateRecordings(recordings: List<AudioRecording>) {
        submitList(recordings.toMutableList())
    }
}

class AudioRecordingViewHolder(
    private val binding: FragmentAudioRecorderBinding,
    private val clickListener: AudioRecordingClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(recording: AudioRecording) {
        // Set up click listener to play/pause the recording
        binding.deleteButton.visibility = View.GONE
        binding.holdToRecord.visibility = View.GONE
        binding.recordButton.visibility = View.GONE
        binding.recording.visibility = View.GONE
        binding.playing.visibility = View.GONE
        binding.saveToList.visibility = View.GONE
        binding.audioRcv.visibility = View.GONE

        binding.playButton.setOnClickListener {
            clickListener.onAudioRecordingClicked(recording)
        }
    }
}

class DiffCallback : DiffUtil.ItemCallback<AudioRecording>() {
    override fun areItemsTheSame(oldItem: AudioRecording, newItem: AudioRecording): Boolean {
        return oldItem.fileName == newItem.fileName
    }

    override fun areContentsTheSame(oldItem: AudioRecording, newItem: AudioRecording): Boolean {
        return oldItem == newItem
    }
}
