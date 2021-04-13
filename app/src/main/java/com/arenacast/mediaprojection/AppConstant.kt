package com.arenacast.mediaprojection

internal const val DEFAULT_VALUE_SIZE_WIDTH = 720//1080
internal const val DEFAULT_VALUE_SIZE_HEIGHT = 1280//1920

internal const val ACTION_INIT = "action_init"
internal const val ACTION_PERMISSION_INIT = "action_permission_init"
internal const val ACTION_REJECT = "action_reject"
internal const val ACTION_START = "action_start"
internal const val ACTION_STOP = "action_stop"
internal const val ACTION_SELF_STOP = "self_stop"

internal const val EXTRA_RESULT_CODE = "result_code"
internal const val EXTRA_REQUEST_DATA = "request_data"

internal const val EXTRA_SURFACE = "surface"
internal const val EXTRA_PROJECTION_NAME = "MediaProjection"
internal const val EXTRA_SIZE_WIDTH = "size_width"
internal const val EXTRA_SIZE_HEIGHT = "size_height"
internal const val EXTRA_SEND_BROADCAST = "send_broadcast"

internal const val EB_MEDIA_PROJECTION_PERMISSION_GET = "EB_MEDIA_PROJECTION_PERMISSION_GET"
internal const val EB_MEDIA_PROJECTION_PERMISSION_INIT = "EB_MEDIA_PROJECTION_PERMISSION_INIT"
internal const val EB_MEDIA_PROJECTION_PERMISSION_REJECT = "EB_MEDIA_PROJECTION_PERMISSION_REJECT"
internal const val EB_MEDIA_PROJECTION_ON_STARTED = "EB_MEDIA_PROJECTION_ON_STARTED"
internal const val EB_MEDIA_PROJECTION_ON_STOP = "EB_MEDIA_PROJECTION_ON_STOP"
internal const val EB_MEDIA_PROJECTION_ON_INIT = "EB_MEDIA_PROJECTION_ON_INIT"
internal const val EB_MEDIA_PROJECTION_ON_REJECT = "EB_MEDIA_PROJECTION_ON_REJECT"
internal const val EB_MEDIA_PROJECTION_ON_FAIL = "EB_MEDIA_PROJECTION_ON_FAIL"

internal const val EB_RTMP_SERVICE_START_STREAM = "EB_RTMP_SERVICE_START_STREAM"
internal const val EB_RTMP_SERVICE_STOP_STREAM = "EB_RTMP_SERVICE_STOP_STREAM"
internal const val EB_RTMP_SERVICE_STOP_SERVICE = "EB_RTMP_SERVICE_STOP_SERVICE"
internal const val EB_RTMP_SERVICE_PORTRAIT = "EB_RTMP_SERVICE_PORTRAIT"
internal const val EB_RTMP_SERVICE_LANDSCAPE = "EB_RTMP_SERVICE_LANDSCAPE"

internal const val INTENT_FILTER_MEDIA_PROJECTION = "media_projection_permission_event"
internal const val INTENT_FILTER_RTMP_SERVICE = "rtmp_service_broadcast_event"
internal const val KEY_ACTION = "action"

internal const val RC_WRITE_STORAGE = 130
internal const val RC_RECORD_AUDIO = 131
internal const val RC_CAMERA = 132
internal const val RC_WRITE_STORAGE_RECORD_AUDIO = 141
internal const val RC_MULTIPLE_PERM = 142

internal const val NOTIFICATION_ID = 10093
internal const val REQUEST_MEDIA_PROJECTION = 1

internal const val FOREGROUND_SERVICE_ID = 1000
internal const val NOTIFICATION_CHANNEL_ID = "MediaProjectionService"

internal const val SERVICE_TYPE_RTMP = 0
internal const val SERVICE_TYPE_LOCAL = 1