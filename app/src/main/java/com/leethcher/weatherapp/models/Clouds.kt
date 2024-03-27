package com.leethcher.weatherapp.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Clouds(
    val all: Int
): Parcelable
