Cordova Plugin Camera X
====================

Cordova plugin that allows camera interaction from Javascript and HTML, using CameraX api, only on Android.

**This plugin is under constant development. It is recommended to use master to always have the latest fixes and features.**

**PR's are greatly appreciated**

# Features

<ul>
  <li>Start/Stop a camera preview.</li>
  <li>Get/set zoom ratio.</li>
  <li>Get/set flash mode.</li>
  <li>Take a picture and save into file</li>
  <li>Start/Stop video recording</li>
  <li>Tap to focus</li>
  <li>Pinch to zoom</li>
</ul>

# Installation

Use any one of the installation methods listed below depending on which framework you use.

To install the master version with latest fixes and features

```
cordova plugin add https://github.com/Qarma-inspect/cordova-plugin-camerax.git
```

# Methods

### startCameraX(options, successCallback, errorCallback)

Starts the camera preview instance.
<br>

<strong>Options:</strong>
All options stated are optional and will default to values here
tapToFocus and pinchToZoom are set by default. Flags to control those features will be added later.

* `x` - Defaults to 0
* `y` - Defaults to 0
* `width` - Defaults to window.screen.width
* `height` - Defaults to window.screen.height

```javascript
let options = {
  x: 0,
  y: 0,
  width: window.screen.width,
  height: window.screen.height
};

CameraXPlugin.startCameraX(options);
```
### stopCameraX(successCallback, errorCallback)

<info>Stops the camera preview instance.</info><br/>

```javascript
CameraXPlugin.stopCameraX();
```
### takePictureWithCameraX(options, successCallback, errorCallback)

<info>Take the picture. The argument `quality` defaults to `85` and specifies the quality/compression value: `0=max compression`, `100=max quality`.
Currently resolution is set as 1200x1600 by default, if the phone doesnot support this resolution, CameraX will choose the closest resolution that is bigger than 1200x1600 and has ratio 3x4.
</info><br/>


```javascript
CameraXPlugin.takePictureWithCameraX({fileName: 'photo-1', quality: 90, rotation: ''}, function([imageFilePath, thumbnailFilePath]]){}, function(error) {});
```
### getMaxZoomCameraX(successCallback, errorCallback)

<info>Return the maximum zoom ratio, so you can use to set your zoom steps</info><br/>

```javascript
CameraXPlugin.getMaxZoomCameraX(function(zoomRatio){}, function(error) {});
```
### setZoomCameraX(zoomRatio, successCallback, errorCallback)

<info>Set the zoom level for the camera device currently started. zoomMultipler option accepts an integer. Zoom level is initially at 1</info><br/>

```javascript
CameraXPlugin.setZoomCameraX(zoomRatio, function(){}, function(error) {});
```


### getFlashModeCameraX(successCallback, errorCallback)

<info>Get the flash mode for the camera device currently started. Returns a string representing the current flash mode.</info>See <code>[FLASH_MODE](#camera_Settings.FlashMode)</code> for possible values that can be returned</info><br/>

```javascript
CameraXPlugin.getFlashModeCameraX(function(currentFlashMode){
  console.log(currentFlashMode);
});
```
### setFlashModeCameraX(flashMode, successCallback, errorCallback)

<info>Set the flash mode. See <code>[FLASH_MODE](#camera_Settings.FlashMode)</code> for details about the possible values for flashMode.</info><br/>

```javascript
CameraXPlugin.setFlashMode(CameraPreview.FLASH_MODE.ON);
```
### startRecordingCameraX(options, successCallback, errorCallback)
Starts recording.
<br>

<strong>Options:</strong>


* `fileName` - should be unique
* `durationLimit` - in seconds

```javascript
let options = {
  fileName: 'video-1',
  durationLimit: 12
};

CameraXPlugin.startRecordingCameraX(options);
```

### stopRecordVideo(successCallback, errorCallback)
Stop recording and save the recording into file

```javascript
CameraXPlugin.stopRecordVideo(function(videoPath) {}, function(error) {});
```