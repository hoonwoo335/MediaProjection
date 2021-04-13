package com.arenacast.mediaprojection.model

import android.content.Intent

class MessageRoute {
    val param1: String
    val param2: String
    val param3: Intent?

    constructor(param1: String, param2: String, param3: Intent? = null) {
        this.param1 = param1
        this.param2 = param2
        this.param3 = param3
    }
}