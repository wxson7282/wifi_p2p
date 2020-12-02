audio_player
主要模块
CodecCallback：响应编解码器的各项异步事件
PlayerIntentService：远程连接服务
DirectBroadcastReceiver：处理wifi p2p事件的广播接收器
MediaRouter：媒体播放路由器，实现远程播放
MediaRouteProvider：

MediaRouter实现远程播放有两种方式：远端播放和辅助输出
远端播放：
辅助输出：

在此复习 用安卓手机实现视频监控

**启动过程如下**

启动服务 bindService
打开相机 openCamera()
	设置相机 setUpCameraOutputs
	启动预览线程 Thread(previewThread).start()
建立相机预览会话 createCameraPreviewSession()
    创建作为预览的CaptureRequest.Builder cameraDevice!!.createCaptureRequest
    创建编码器 createEncoderByType
    设置编码器回调 mediaCodec.setCallback
    再编码器回调中设置监听器 mediaCodecCallback.setByteBufferListener onByteBufferReady
        （该监听器通知服务器线程向客户端传送图像数据）
    设置编码器 mediaCodec.configure
    设定编码用Surface mediaCodec.setInputSurface
    启动编码器mediaCodec.start()
    设置Surface为previewRequestBuilder的输出 previewRequestBuilder.addTarget(encoderInputSurface)
    创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求，以及传输请求。最多只能容纳3个输出surface!（其中包括编码器的输入缓冲区encoderInputSurface） cameraDevice!!.createCaptureSession

编码器回调odecCallback中主要的重载函数是onOutputBufferAvailable，当输出缓冲区可以使用时触发。
编码器启动后最先触发的就是onOutputBufferAvailable，表示一帧图像编码完成，可以传送编码后的数据。

对于MP3音频流来说，并不需要编码器，直接可以传送。
需要一个播放器MediaPlayer，可以播放mp3等格式的音频文件。
MediaPlayer
AudioPlaybackCaptureConfiguration 所有支持音频播放的应用均可通过AudioPlaybackCaptureConfiguration API允许另一个应用捕捉它的音频流(API29以上可用)

在Android系统中使用TeeSink功能截取任意音频流的原始PCM音频数据

需要写一个程序先测试一下。 mp3_pcm_test

mime.startsWith("audio") 判断音频轨道
mVideoExtractor.selectTrack(i)   选择音频轨道
mVideoExtractor.readSampleData(buffer, 0) 读取数据
mVideoExtractor.advance  移动到下一帧
mVideoExtractor.release  释放资源

现在的问题是接收端需要何种流媒体？ PCM流

目前，在iOS流媒体领域中，参与竞争的公司主要有三个：微软、Real Networks和苹果公司，相应的iOS流媒体解决方案分别是：Windows Media、Real System和QuickTime.

MediaPlayer可以播放多种格式的声音文件
AudioTrack只能播放已经解码的PCM流
mp3 -> pcm是必须的

还有一个问题，怎样控制播放的速度？
方案一
getLong(MediaFormat.KEY_DURATION)可以取得音乐的时长(ms)
根据时长控制编码和传输的速度。但是控制不好，会有断流或丢帧的现象。
方案二
接收端播放缓冲区释放时发出回调信号，发送端根据回调信号传输数据。
这是异步方法，实现起来比较复杂。另外回调信号经过wifi传输，有一定的时延，控制不好也会出现断流。
方案三
这是方案二的改进，在发送端建立dummy播放器，用来产生回调信号控制传输速率。
方案四
用播放器播放mp3文件，播放时取得实时pcm流，直接发送给接收端。TeeSink方法。

**
mp3文件 -> MediaExtractor -> MediaCodec(解码) -> pcm流 -> wifi p2p -> audioTrack -> 播出
**

mediaCodec作为解码器，拟采用异步方式，onInputBufferAvailable时，将MP3数据投入解码过程。
同时需要考虑mediaExtractor已经准备好SampleData，需要监听mediaExtractor是否准备好数据。
如果mediaExtractor没有准备好，则mediaCodec空转一次。

另外要考虑的是dummy播放器的播放速度，解码速度太快会丢帧，太慢会卡顿，也需要监听dummy播放器的播放。
或者使用MediaSync控制播放速度。
MediaSync通常用于同步视频和音频，以视频为基准同步音频。

mp3文件 -> MediaExtractor -> MediaCodec(解码) -> pcm流 -> wifi p2p -> MediaSync -> audioTrack

MediaSync必须实现的动作
mMediaSync = new MediaSync();
mMediaSync.setSurface(surfaceHolder.getSurface());
Surface surface = mMediaSync.createInputSurface();
mVideoDecoder.configure(mediaFormat, surface, null, 0);
ByteBuffer copyBuffer = ByteBuffer.allocate(decoderBuffer.remaining());
copyBuffer.put(decoderBuffer);
copyBuffer.flip();
mMediaSync.queueAudio(copyBuffer, i, bufferInfo.presentationTimeUs);
mMediaSync.setAudioTrack(audioTrack);
mMediaSync.setCallback(new MediaSync.Callback() {
    @Override
    public void onAudioBufferConsumed(@NonNull MediaSync mediaSync, @NonNull ByteBuffer byteBuffer, int i) {
        byteBuffer.clear();
        Log.d("MediaSync", "onAudioBufferConsumed i " + i);
    }
}, new Handler());

拟采用方案三
在发送端把dummy AudioTrack放在解码器的输出端
AudioTrack的write方法是阻塞方式，直至数据进入播放队列才释放进程。因此可以控制解码器的速度。
