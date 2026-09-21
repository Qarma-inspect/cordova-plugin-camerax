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

// QARMA-21811: the add/remove pair used to wrap the callback in a fresh arrow function on every
// call, so removeEventListener never matched anything and each camera panel leaked its
// listener. The wrapper is now remembered per callback so removal actually removes it.
var documentEventWrappers = new Map();

function addDocumentEventListener(eventName, callback, extract) {
    var wrapper = function(event) { callback(extract(event)); };
    if (!documentEventWrappers.has(callback)) {
        documentEventWrappers.set(callback, {});
    }
    var wrappersForCallback = documentEventWrappers.get(callback);
    if (wrappersForCallback[eventName]) {
        return;
    }
    wrappersForCallback[eventName] = wrapper;
    document.addEventListener(eventName, wrapper);
}

function removeDocumentEventListener(eventName, callback) {
    var wrappersForCallback = documentEventWrappers.get(callback);
    if (!wrappersForCallback || !wrappersForCallback[eventName]) {
        return;
    }
    document.removeEventListener(eventName, wrappersForCallback[eventName]);
    delete wrappersForCallback[eventName];
    if (Object.keys(wrappersForCallback).length === 0) {
        documentEventWrappers.delete(callback);
    }
}

CameraXPlugin.addZoomEventListener = function(callback) {
    addDocumentEventListener("zoomRatioUpdate", callback, function(event) { return event.ratio; });
}

CameraXPlugin.removeZoomEventListener = function(callback) {
    removeDocumentEventListener("zoomRatioUpdate", callback);
}

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
    addDocumentEventListener("videoRecorderUpdate", callback, function(event) { return event.filePath; });
}

CameraXPlugin.removeVideoFinalizedEventListener = function(callback) {
    removeDocumentEventListener("videoRecorderUpdate", callback);
}

// QARMA-21334 diagnostics only - see CameraXHelper.java for what each event reports.
CameraXPlugin.addCameraDiagnosticEventListener = function(callback) {
    document.addEventListener("cameraDiagnostic", callback);
}

CameraXPlugin.removeCameraDiagnosticEventListener = function(callback) {
    document.removeEventListener("cameraDiagnostic", callback);
}

CameraXPlugin.enableTorchCameraX = function(enabled, onSuccess, onError) {
    exec(onSuccess, onError, PLUGIN_NAME, "enableTorchCameraX", [enabled]);
};

module.exports = CameraXPlugin;
