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
