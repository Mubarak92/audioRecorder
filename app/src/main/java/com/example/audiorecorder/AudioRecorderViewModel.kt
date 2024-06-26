package com.example.audiorecorder

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val LOG_TAG = "AudioRecordTest"
private const val SEEK_BAR_UPDATE_INTERVAL = 100

class AudioRecorderViewModel : ViewModel() {

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> get() = _recordingDuration

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> get() = _currentPosition


    private val _totalDuration = MutableStateFlow(0)
    val totalDuration: StateFlow<Int> get() = _totalDuration

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> get() = _isPlaying

    var permissionToRecordAccepted = false

    var isSeekBarBeingTouched = false

    var recorder: MediaRecorder? = null

    var player: MediaPlayer? = null
    private var isPlayerPaused = false

    var mStartRecording = true
    var mStartPlaying = true

    private var fileName: String = ""


    fun setAudioFileName(audioFileName:String){
        fileName = audioFileName
    }

    fun onRecord(start: Boolean) {
        if (start) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    fun onPlay(start: Boolean) = if (start) {
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

                val totalDuration = duration
                _currentPosition.value = 0
                _totalDuration.value = totalDuration

                setOnCompletionListener {
                    stopPlaying()
                    mStartPlaying = true
                    Log.d(LOG_TAG, "Playback complete. mStartPlaying set to true.")
                }

                // Update current position and seek bar progress periodically
                viewModelScope.launch {
                    while (isPlaying && !isPlayerPaused) {
                        val currentPosition = currentPosition
                        _currentPosition.value = currentPosition
                        delay(SEEK_BAR_UPDATE_INTERVAL.toLong()) // Update every 100 milliseconds (adjust as needed)
                    }
                }

                updateSeekBar()

            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }

    private fun updateSeekBar() {
        viewModelScope.launch {
            while (isPlaying.value) {
                if (!isSeekBarBeingTouched) {
                    val currentPosition = player?.currentPosition ?: 0
                    val totalDuration = player?.duration ?: 0

                    if (totalDuration > 0) {
                        _currentPosition.value = currentPosition
                        _totalDuration.value = totalDuration
                    }
                }

                delay(SEEK_BAR_UPDATE_INTERVAL.toLong())
            }
        }
    }


    private fun stopPlaying() {
        try {
            player?.apply {
                if (isPlaying) {
                    pause()
                    isPlayerPaused = true
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping playback", e)
        } finally {
            _isPlaying.value = false
            player = null
            isPlayerPaused = false // Reset the variable when stopping
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
            _recordingDuration.value = System.currentTimeMillis()
        }
    }

    fun stopRecording() {
        recorder?.apply {
            stop()
            reset()
            release()
            _recordingDuration.value = System.currentTimeMillis() - _recordingDuration.value
        }
        recorder = null
    }

     fun deleteAudioFile() {
        val file = File(fileName)
        if (file.exists()) {
            file.delete()
            _recordingDuration.value = 0L
        }
    }

    fun seekToRecordingPosition(positionMillis: Long) {
        player?.seekTo(positionMillis.toInt())
    }

}