#利用Wif p2p播放立体声
audio_player

##主要模块
CodecCallback：响应编解码器的各项异步事件
PlayerIntentService：远程连接服务
DirectBroadcastReceiver：处理wifi p2p事件的广播接收器
MediaRouter：媒体播放路由器，实现远程播放
MediaRouteProvider：

##参考
MediaRouter实现远程播放有两种方式：远端播放和辅助输出
远端播放：
辅助输出：

##回顾 用安卓手机实现视频监控

###启动过程如下

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

#还有一个问题，怎样控制播放的速度？
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

#数据流程
mp3文件 -> MediaExtractor -> MediaCodec(解码) -> pcm流 -> wifi p2p -> audioTrack -> 播出
**

mediaCodec作为解码器，拟采用异步方式，onInputBufferAvailable时，将MP3数据投入解码过程。
同时需要考虑mediaExtractor已经准备好SampleData，需要监听mediaExtractor是否准备好数据。
如果mediaExtractor没有准备好，则mediaCodec空转一次。

另外要考虑的是dummy播放器的播放速度，解码速度太快会丢帧，太慢会卡顿，也需要监听dummy播放器的播放。
或者使用MediaSync控制播放速度。
MediaSync通常用于同步视频和音频，以视频为基准同步音频。

#数据流程
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

**以上为前期的若干考虑**

#以下为正式说明

原来计划用蓝牙实现TWS功能，但是有两个困难，一是bluetooth a2sp sink模式没有开放，需要修改编译android系统本身，实现起来有难度。
二是bluetooth a2sp不支持一对多模式，只能点对点通信，无法实现一个播放器两个无线音箱的设想，不能实现真正意义上的无线立体声TWS。

现在用wifi p2p实现无线立体声音箱。流程如下：

mp3文件 -> MediaExtractor -> MediaCodec(解码) -> pcm流 -> wifi p2p -> audioTrack
                                                      -> dummy audioTrack
dummy audioTrack用来控制解码器的速度，如果不加控制，超速播放的数据会挤爆接收端的缓冲区。
解码后得到的pcm数据同时送到dummy audioTrack和wifi p2p，由于audioTrack.write是闭锁方法，该帧数据被消化后audioTrack才能被释放，
才能实施codec.releaseOutputBuffer，因此控制了解码的速度。


#p2p连接的角色分配
    player作为group owner
    receiver作为group client
    这种方案可以实现多声道分别传送。
    问题是怎样确保角色分配？创建group的设备在连接成功时为group owner群主。
    player负责建立group
    receiver发起p2p设备搜索，从搜索获得的设备列表中选择player，向player发出连接请求。
    连接成功时触发WIFI_P2P_CONNECTION_CHANGED_ACTION，此时receiver向player发出连接成功消息。

#--player--
##处理概要
一、系统初始化
    - 获取wifi p2p所必须的wifiP2pManager和channel，建立并注册DirectBroadcastReceiver（用于处理WIFI_P2P消息）
    - 绑定PlayerIntentService，绑定时指定serviceConnection。在serviceConnection.onServiceConnected中给playerIntentService注入messageListener，
      messageListener用于监听playerIntentService中发生的消息，其中onRemoteMsgArrived用于处理receiver来的消息，onLocalMsgOccurred用于处理本地消息。
    - 启动PlayerIntentService
    - 实例化Player，同时注入transferDataListener，用于通知playerIntentService.serverThread待传送数据准备完毕。
二、用户实施的动作
    - 建组  确认ACCESS_FINE_LOCATION权限，wifiP2pManager.createGroup
    - 删组  wifiP2pManager.removeGroup
    - 播放  player.assetFilePlay
        实例化mediaExtractor
        设定播放用文件mediaExtractor!!.setDataSource
        建立dummy audioTrack
        选择音频轨
        根据保存的sampleTime寻找播放起点
        准备解码器
        启动dummy audioTrack播放
        启动解码器
    - 停止 
        释放dummyAudioTrack、decoder、mediaExtractor
    - 暂停
        保存当前播放时间点
        释放dummyAudioTrack、decoder、mediaExtractor
    - 静音
        控制音量为零或最大
三、主要模块功能
    - DecoderCallback
        MediaCodec建立时，需要在默认looper中设置异步回调以响应MediaCodec产生的事件：Sets an asynchronous callback for actionable MediaCodec events on the default looper.
        这是MediaCodec异步模式的核心，在此仅响应以下两种事件：
            onInputBufferAvailable时，从MediaExtractor读入待解码数据，提交MediaCodec进行解码处理。
            onOutputBufferAvailable时，取得解码后的pcm数据，通过transferDataListener.onTransferDataReady输出到playerIntentService.serverThread向外传送。
    - DirectBroadcastReceiver
        这个广播接收器专门用于处理wifi p2p事件，主要有：
            WIFI_P2P_STATE_CHANGED_ACTION  --用于指示 Wifi P2P 是否可用
                通知调用者WifiP2p是否可用(onWifiP2pEnabled)
                如果可用，初始化wifiP2pDeviceList，发送给调用者。
            WIFI_P2P_PEERS_CHANGED_ACTION  --节点列表发生了变化
                检查ACCESS_FINE_LOCATION权限，如未取得则申请该权限
                取得wifiP2pDeviceList，发送给调用者(onPeersAvailable)。
            WIFI_P2P_CONNECTION_CHANGED_ACTION  --连接状态发生了改变
                根据调用者indent判断网络连接状态
                如果网络已经连接，则获取p2p连接情报发给调用者(onConnectionInfoAvailable)
                否则，通知调用者p2p设备已断开连接(onDisconnection)。
            WIFI_P2P_THIS_DEVICE_CHANGED_ACTION  --本设备的设备信息发生了变化
                通知调用者本设备发生变化(onSelfDeviceAvailable)
            WIFI_P2P_DISCOVERY_CHANGED_ACTION  --p2p设备搜索开始或停止
                如果搜索停止，则通知调用者
    - MainViewModel
        主要业务逻辑所在，MainFragment上的各种动作都在此实现。
        对于wifi p2p必须实现以下消息响应：
            onChannelDisconnected
                player   : 无处理
                receiver : 无处理
            onWifiP2pEnabled
                player   : 无处理
                receiver : 设定WifiP2pEnabled标志，这是启动p2p设备搜索的先决条件
            onConnectionInfoAvailable
                player   : 如果group建立，且本机是群主，启动PlayerIntentService
                receiver : 这是连接成功的标志，这时可以在本机上显示相关p2p连接信息，例如：对方设备名和地址、本设备是否群主、群主IP地址等
                           启动IntentService
                           如果group已建立，且本机不是群主，通过socket向对方发送连接成功消息
            onDisconnection
                player   : 无处理
                receiver : 清除p2p设备列表
            onSelfDeviceAvailable
                player   : 无处理
                receiver : 可以显示本机名称、地址、设备状态
            onPeersAvailable
                player   : 无处理
                receiver : 重建p2p设备列表
            onP2pDiscoveryStopped
                player   : 无处理
                receiver : 如果p2p设备列表为空，再启动搜寻 Wi-Fi P2P 设备
    - Player
        播放器，实现以下动作：
            assetFilePlay 从文件中获取音频轨构成dummyAudioTrack，并作为mediaExtractor的输入，准备解码器，dummyAudioTrack开始播放，启动解码器。
            stop 停止播放
            pause 暂停播放
            mute 静音
    - PlayerIntentService
        开启后台线程，执行完毕自动停止。
        建立ServerSocket，等待客户端来连接，建立clientSocket，建立并启动serverThread。
        serverThread用来处理clientSocket中包含的信息，在其中有两个任务：
            1 启动另一个线程，循环读取clientSocket中包含的信息，用监听器发送给MainViewModel。
            2 在本线程serverThread中建立播放输出用Looper，用outputHandler处理从解码器回调DecoderCallback发出的
              经过MainViewModel.transferDataListener得到的的pcm数据和Byte数据。
四、数据流程
    用户按下播放键后，dummyAudioTrack启动，同时启动decoder，DecoderCallback.onInputBufferAvailable立刻响应，从extractor读取mp3数据，
    提交decoder进行解码。该帧数据解码完成后，DecoderCallback.onOutputBufferAvailable响应，将解码得到的pcm数据通过transferDataListener发给
    playerIntentService?.serverThread?.outputHandler，OutputHandler把pcm数据经Socket发送到receiver；随后用audioTrack.write在本机上播放pcm音频。
    由于audioTrack.write是闭锁方法，该帧数据被消化后audioTrack才能被释放，因而控制了解码器的速度。
    
    