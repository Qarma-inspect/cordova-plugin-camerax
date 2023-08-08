var argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec');

var PLUGIN_NAME = "CameraXPreview";

var CameraXPreview = function () { };

function isFunction(obj) {
    return !!(obj && obj.constructor && obj.call && obj.apply);
};

CameraXPreview.startCameraX = function (options, onSuccess, onError) {
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

    exec(onSuccess, onError, PLUGIN_NAME, "startCameraX", [options.x, options.y, options.width, options.height, options.camera, options.tapPhoto, options.previewDrag, options.toBack, options.alpha, options.tapFocus]);
};

CameraXPreview.stopCameraX = function (onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "stopCameraX", []);
};

CameraXPreview.switchCamera = function (onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "switchCamera", []);
};

CameraXPreview.takePictureWithCameraX = function (opts, onSuccess, onError) {
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
            opts.quality,
            fileName,
            opts.orientation,
        ]);
};

CameraXPreview.getMaxZoomCameraX = function(onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "getMaxZoomCameraX", []);
};

CameraXPreview.setZoomCameraX = function(zoom, onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "setZoomCameraX", [zoom]);
};

CameraXPreview.getFlashModeCameraX = function(onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "getFlashModeCameraX", []);
};

CameraXPreview.setFlashModeCameraX = function(mode, onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "setFlashModeCameraX", [mode]);
};

CameraXPreview.startRecordingCameraX = function (opts, onSuccess, onError) {
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

CameraXPreview.stopRecordingCameraX = function (onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "stopRecordingCameraX");
};

module.exports = CameraXPreview;
