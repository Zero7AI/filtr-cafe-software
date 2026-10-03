package com.example.data.entity

import android.graphics.Bitmap

data class StoryPhotoPrintData(
    val bitmap: Bitmap,
    val title: String,
    val date: String,
    val caption: String
)
