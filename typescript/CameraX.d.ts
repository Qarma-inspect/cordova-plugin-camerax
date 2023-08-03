type CameraPreviewSuccessHandler = (data: any) => any;
type CameraPreviewErrorHandler = (err: any) => any;

interface CameraPreviewStartCameraXOptions {
    alpha?: number;
    camera?: CameraPreviewCameraDirection | string;
    height?: number;
    previewDrag?: boolean;
    tapFocus?: boolean;
    tapPhoto?: boolean;
    toBack?: boolean;
    width?: number;
    x?: number;
    y?: number;
}

type CameraPreviewCameraDirection = "back" | "front";

interface CameraXPreview {
    startCameraX(
        options?: CameraPreviewStartCameraXOptions,
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    stopCameraX(
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    getMaxZoomCameraX(
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    setZoomCameraX(
        zoomRatio: number,
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    getFlashModeCameraX(
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    setFlashModeCameraX(
        mode: number,
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    startRecordVideo(
        options?: any | CameraPreviewSuccessHandler,
        onSuccess?: CameraPreviewSuccessHandler | CameraPreviewErrorHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    stopRecordVideo(
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    switchCamera(
        onSuccess?: CameraPreviewSuccessHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
    takePictureWithCameraX(
        options?: CameraPreviewTakePictureOptions | CameraPreviewSuccessHandler,
        onSuccess?: CameraPreviewSuccessHandler | CameraPreviewErrorHandler,
        onError?: CameraPreviewErrorHandler
    ): void;
}
