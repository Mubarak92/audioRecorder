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
            binding.recordTimer.base = SystemClock.elapsedRealtime()
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
                start()

                // Update seek bar progress
                binding.audioSeekbar.max = duration
                val updateRunnable = object : Runnable {
                    override fun run() {
                        if (player != null && player!!.isPlaying) {
                            if (!isSeekBarBeingTouched) {
                                binding.audioSeekbar.progress = currentPosition
                            }
                            // Update every 100 milliseconds
                            binding.audioSeekbar.postDelayed(this, 100)
                        }
                    }
                }
                binding.audioSeekbar.post(updateRunnable)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }



    private fun stopPlaying() {
        try {
            player?.apply {
                if (isPlaying) {
                    pause()
                }
                reset() // Reset the MediaPlayer to its uninitialized state
                release()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping playback", e)
        } finally {
            player = null
        }
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
            start()

            // Start recording duration timer
            recordingDuration = System.currentTimeMillis()
        }

        // Schedule a task to stop recording after 1 minute
        binding.root.postDelayed({
            stopRecording()
            binding.recordTimer.stop()
            Toast.makeText(requireContext(), "Maximum recording time reached (1 minute)", Toast.LENGTH_SHORT).show()
        }, 60000) // 60000 milliseconds = 1 minute
    }


    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    private fun deleteAudioFile() {
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

        val max = 100 // Set the maximum value for the seek bar
        val progress = 0 // Set the initial progress

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