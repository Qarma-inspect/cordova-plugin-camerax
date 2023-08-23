var exec = require('cordova/exec');
var PLUGIN_NAME = "CameraXPlugin";
var CameraXPlugin = function () { };

function isFunction(obj) {
    return !!(obj && obj.constructor && obj.call && obj.apply);
};

CameraXPlugin.startCameraX = function (options, onSuccess, onError) {
    options = options || {};
    options.x = options.x || 0;
    options.y = options.y || 0;
    options.width = options.width || window.screen.width;
    options.height = options.height || window.screen.height;
    options.camera = options.camera || CameraPreview.CAMERA_DIRECTION.FRONT;
    if (typeof (options.tapPhoto) === 'undefined') {
        options.tapPhoto = true;
    }

    if (typeof (options.tapFocus) == 'undefined') {
        options.tapFocus = false;
    }

    options.previewDrag = options.previewDrag || false;
    options.toBack = options.toBack || false;
    if (typeof (options.alpha) === 'undefined') {
        options.alpha = 1;
    }

    exec(onSuccess, onError, PLUGIN_NAME, "startCameraX", [options.x, options.y, options.width, options.height, options.targetPictureWidth, options.targetPictureHeight]);
};

CameraXPlugin.stopCameraX = function (onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "stopCameraX", []);
};

CameraXPlugin.takePictureWithCameraX = function (opts, onSuccess, onError) {
    if (!opts) {
        opts = {};
    } else if (isFunction(opts)) {
        onSuccess = opts;
        opts = {};
    }

    if (!isFunction(onSuccess)) {
        return false;
    }

    opts.width = opts.width || 0;
    opts.height = opts.height || 0;

    opts.maxWidthAllowed = opts.maxWidthAllowed || 0;
    opts.maxHeightAllowed = opts.maxHeightAllowed || 0;

    if (!opts.quality || opts.quality > 100 || opts.quality < 0) {
        opts.quality = 85;
    }

    var fileName = opts.fileName || "tempFile.jpg";

    if (typeof opts.orientation == "undefined" || opts.orientation == null) {
        opts.orientation = 0;
    }

    exec(
        onSuccess,
        onError,
        PLUGIN_NAME,
        "takePictureWithCameraX",
        [
            opts.width,
            opts.height,
            opts.maxWidthAllowed,
            opts.maxHeightAllowed,
            opts.quality,
            fileName,
            opts.orientation,
        ]);
};

CameraXPlugin.getMaxZoomCameraX = function(onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "getMaxZoomCameraX", []);
};

CameraXPlugin.setZoomCameraX = function(zoom, onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "setZoomCameraX", [zoom]);
};

CameraXPlugin.getFlashModeCameraX = function(onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "getFlashModeCameraX", []);
};

CameraXPlugin.setFlashModeCameraX = function(mode, onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "setFlashModeCameraX", [mode]);
};

CameraXPlugin.startRecordingCameraX = function (opts, onSuccess, onError) {
    if (!opts) {
        opts = {};
    } else if (isFunction(opts)) {
        onSuccess = opts;
        opts = {};
    }

    if (!isFunction(onSuccess)) {
        return false;
    }

    opts.width = opts.width || 0;
    opts.height = opts.height || 0;

    if (!opts.quality || opts.quality > 100 || opts.quality < 0) {
        opts.quality = 85;
    }

    if(!opts.durationLimit) {
        opts.durationLimit = 12;
    }

    exec(onSuccess, onError, PLUGIN_NAME, "startRecordingCameraX", [opts.fileName, opts.durationLimit]);
};

CameraXPlugin.stopRecordingCameraX = function (onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "stopRecordingCameraX");
};

CameraXPlugin.addVideoFinalizedEventListener = function(callback) {
    document.addEventListener("videoRecorderUpdate", event => callback(event.filePath));
}

CameraXPlugin.removeVideoFinalizedEventListener = function(callback) {
    document.removeEventListener("videoRecorderUpdate", event => callback(event.filePath));
}

module.exports = CameraXPlugin;
