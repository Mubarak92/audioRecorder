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
import com.example.audiorecorder.databinding.FragmentAudioRecorderBinding
import java.io.File
import java.io.IOException

private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class AudioRecorderFragment : Fragment()  {

    private var recordingDuration = 0L

    private var fileName: String = ""

    private var recorder: MediaRecorder? = null

    private var player: MediaPlayer? = null

    private var isSeekBarBeingTouched = false

    var mStartRecording = true
    var mStartPlaying = true

    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false

    // Declare those two so we can destroy it into onDestroyView to prevent memory leaks
    private val binding get() = _binding!!
    private var _binding: FragmentAudioRecorderBinding? = null



    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            if (recorder != null) {
                if (!isSeekBarBeingTouched) {
                    binding.audioSeekbar.progress = (System.currentTimeMillis() - recordingDuration).toInt()
                }
                // Update every 100 milliseconds
                binding.audioSeekbar.postDelayed(this, 100)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentMainPageBinding = FragmentAudioRecorderBinding.inflate(inflater, container, false)
        _binding = fragmentMainPageBinding
        return binding.root
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    private fun onRecord(start: Boolean) {
        if (start) {
            startRecording()
            binding.recordTimer.isCountDown = true
            binding.recordTimer.base = SystemClock.elapsedRealtime() + 60000
            binding.recordTimer.start()
        } else {
            stopRecording()
            binding.recordTimer.stop()
        }
    }

    private fun onPlay(start: Boolean) = if (start) {
        startPlaying()
    } else {
        stopPlaying()
    }

    private fun startPlaying() {
        player = MediaPlayer().apply {
            try {
                setDataSource(fileName)
                prepare()
                seekTo(binding.audioSeekbar.progress)
                start()

                setOnCompletionListener {
                    stopPlaying()
                    // Reset SeekBar progress to 0
                    binding.audioSeekbar.progress = 0
                    mStartPlaying = true  // Update mStartPlaying to true
                    Log.d(LOG_TAG, "Playback complete. mStartPlaying set to true.")

                    // Change the play button icon to play
                    binding.playButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.ic_play,
                            null
                        )
                    )
                }

                // Update seek bar progress
                binding.audioSeekbar.max = duration
                val updateRunnable = object : Runnable {
                    override fun run() {
                        if (player != null && player!!.isPlaying) {
                            if (!isSeekBarBeingTouched) {
                                binding.audioSeekbar.progress = currentPosition
                            }
                            if (currentPosition >= duration) {
                                stopPlaying()
                                binding.audioSeekbar.progress = 0
                            } else {
                                binding.audioSeekbar.postDelayed(this, 100)
                            }
                        }
                    }
                }
                binding.audioSeekbar.post(updateRunnable)

                // Change the play button icon to pause
                binding.playButton.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_pause,
                        null
                    )
                )
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }

    private fun updatePlayButtonIcon() {
        binding.playButton.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                if (mStartPlaying || (player != null && !player!!.isPlaying)) R.drawable.ic_play else R.drawable.ic_pause,
                null
            )
        )
    }

    private fun stopPlaying() {
        try {
            player?.apply {
                if (isPlaying) {
                    pause()
                }
                // Do not reset and release, just pause
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping playback", e)
        } finally {
            player = null
            // Update the play button icon after stopping playback
            updatePlayButtonIcon()
        }
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recordingDuration = System.currentTimeMillis()
            binding.root.postDelayed({
                if (binding.holdToRecord.isPressed) { // Check if the button is still being held
                    stopRecording()
                    binding.recordTimer.stop()
                    Toast.makeText(
                        requireContext(),
                        "Maximum recording time reached (1 minute)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, 60000) // 60000 milliseconds = 1 minute

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
            start()

            // Start recording duration timer
            recordingDuration = System.currentTimeMillis()
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            reset()
            release()
        }
        recorder = null

        // Calculate the recording duration
        val recordingDurationMillis = System.currentTimeMillis() - recordingDuration

        // Check if the recording duration is less than 1 second
        if (recordingDurationMillis < 1000) {
            // Show a toast indicating that the recording is too short
            Toast.makeText(
                requireContext(),
                "Recording is too short (less than 1 second)",
                Toast.LENGTH_SHORT
            ).show()

            // Delete the audio file
            deleteAudioFile()
        } else {
            // Continue with normal processing
            binding.audioSeekbar.progress = 0
            // Remove the update task when recording stops
            binding.audioSeekbar.removeCallbacks(updateSeekBarTask)
        }
    }

    private fun deleteAudioFile() {
        binding.audioSeekbar.progress = 0
        val file = File(fileName)
        if (file.exists()) {
            file.delete()
            Toast.makeText(requireContext(), "Audio file deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "No audio file to delete", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updatePlayButtonIcon()

        // Set a listener to handle SeekBar changes
        binding.audioSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    player?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarBeingTouched = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeekBarBeingTouched = false
            }
        })

        binding.recordButton.setOnClickListener {
            onRecord(mStartRecording)
            binding.recording.visibility = when (mStartRecording) {
                true -> VISIBLE
                false -> GONE
            }
            mStartRecording = !mStartRecording
        }

        binding.holdToRecord.setOnLongClickListener {
            onRecord(true)
            binding.recording.visibility = VISIBLE

            true // Consume the long click event
        }

        binding.holdToRecord.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                binding.holdToRecord.performClick()
                onRecord(false)
                binding.recording.visibility = GONE

            }
            false
        }

        binding.playButton.setOnClickListener {
            onPlay(mStartPlaying)
            binding.playing.visibility = when (mStartPlaying) {
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
            mStartPlaying = !mStartPlaying
        }

        binding.deleteButton.setOnClickListener {
            deleteAudioFile()
        }
    }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Record to the external cache directory for visibility
        fileName = "${context?.externalCacheDir?.absolutePath}/audiorecordtest.mp3"

        // Request microphone permission if not granted
        if (!permissionToRecordAccepted) {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null
        player?.release()
        player = null
    }
}