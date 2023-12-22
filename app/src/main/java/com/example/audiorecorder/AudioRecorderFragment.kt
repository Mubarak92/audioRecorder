package com.example.audiorecorder

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.audiorecorder.databinding.FragmentAudioRecorderBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
private const val SEEK_BAR_UPDATE_INTERVAL = 100


private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class AudioRecorderFragment : Fragment() {

    private val viewModel: AudioRecorderViewModel by viewModels()

    private var fileName: String = ""

    // Declare those two so we can destroy it into onDestroyView to prevent memory leaks
    private val binding get() = _binding!!
    private var _binding: FragmentAudioRecorderBinding? = null


    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            if (viewModel.player != null && viewModel.player!!.isPlaying) {
                if (!viewModel.isSeekBarBeingTouched) {
                    val currentPosition = viewModel.player!!.currentPosition
                    binding.audioSeekbar.progress = currentPosition
                }
                // Update every 100 milliseconds
                binding.audioSeekbar.postDelayed(this, SEEK_BAR_UPDATE_INTERVAL.toLong())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentMainPageBinding =
            FragmentAudioRecorderBinding.inflate(inflater, container, false)
        _binding = fragmentMainPageBinding
        return binding.root
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        viewModel.permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        lifecycleScope.launch {
            viewModel.totalDuration.collect { duration ->
                binding.audioSeekbar.max = duration
            }
        }

        lifecycleScope.launch {
            viewModel.currentPosition.collect { position ->
                if (!binding.audioSeekbar.isPressed) {
                    binding.audioSeekbar.progress = position
                }
            }
        }

        binding.audioSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekToRecordingPosition(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                viewModel.isSeekBarBeingTouched = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                viewModel.isSeekBarBeingTouched = false
            }
        })
        binding.recordButton.setOnClickListener {
            viewModel.onRecord(viewModel.mStartRecording)
            binding.recording.visibility = when (viewModel.mStartRecording) {
                true -> VISIBLE
                false -> GONE
            }
            viewModel.mStartRecording = !viewModel.mStartRecording
        }

        binding.holdToRecord.setOnLongClickListener {
            viewModel.onRecord(true)
            binding.recording.visibility = VISIBLE
            true // Consume the long click event
        }

        binding.holdToRecord.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                binding.holdToRecord.performClick()
                viewModel.onRecord(false)
                binding.recording.visibility = GONE
            }
            false
        }


        binding.audioSeekbar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                viewModel.isSeekBarBeingTouched = true
            } else if (event.action == MotionEvent.ACTION_UP) {
                viewModel.isSeekBarBeingTouched = false
                viewModel.seekToRecordingPosition(binding.audioSeekbar.progress.toLong())
            }
            false
        }

        binding.playButton.setOnClickListener {
            viewModel.onPlay(viewModel.mStartPlaying)
            binding.playing.visibility = when (viewModel.mStartPlaying) {
                true -> {
                    binding.playButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.ic_pause,
                            null
                        )
                    )
                    VISIBLE
                }

                false -> {
                    binding.playButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.ic_play,
                            null
                        )
                    )
                    GONE
                }
            }
            viewModel.mStartPlaying = !viewModel.mStartPlaying
        }

        binding.deleteButton.setOnClickListener {
            viewModel.deleteAudioFile()
        }
    }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Record to the external cache directory for visibility
        fileName = "${context?.externalCacheDir?.absolutePath}/audiorecordtest.m4a"
        viewModel.setAudioFileName(fileName)
        // Request microphone permission if not granted
        if (!viewModel.permissionToRecordAccepted) {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.recorder?.release()
        viewModel.recorder = null
        viewModel.player?.release()
        viewModel.player = null
    }
}