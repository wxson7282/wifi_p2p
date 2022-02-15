# Wifi直连(p2p)一对多音频传输
本系统由一台播放器（服务器）和多台接收器（客户端）构成一个wifi直连的群组，服务器是群主(GO)，客户端是组员(GC)。
使用Wifi直连(p2p)构造一对多的系统是很容易实现的。
系统构成请参见图：wifi_p2p_一对多数据流程
上图只画出了音频数据的流程，对于服务器与客户端的文字信息交互没有表示，这部分内容看一下代码就明白了。
为了确保服务器作为群主(GO)角色出现在群组中，服务器启动时立即主动创立群组。
客户端启动后通过搜索功能发现服务器，并与之建立连接，客户端可以有多个。
通信系统采用NIO实现非阻塞的socket通信，一来有较好性能，二来避免了多用户复杂线程处理。
## NIO系统的处理要点
NIO不同于传统的阻塞式socket通信方式，无法使用功能强大的ObjectOutputStream和ObjectInputStream，将对象以数据流方式发送或接收。
因此必须自己实现传输数据的打包和解包。
本系统传输的数据有两类，文字数据和音频数据。文字数据用于向对方发送消息，音频数据以PCM形式传送声音。
### 客户端向服务器发送的只有文字数据，直接使用charset.decode解码获得String。
### 为了便于NIO传输，服务器发出的两种数据都使用如下相同格式的数据包：
- 包类型 1个字节，用以区分文字数据和音频数据。
- 包长度 4个字节，用一个整数表示数据包的长度。
- 数据包，长度不等的文字数据或音频数据。
### 解决NIO的粘包拆包问题
粘包拆包问题的发生是NIO数据收发缓冲机制造成的，如果数据包边界超过缓冲区边界，就会发生拆包；如果数据包边界未达到缓冲区边界、且后续数据包也已到达，就会发生粘包。
服务器输出数据打包的过程很简单，由ConnectIntentService实现，使用SocketChannel.write方法依次把包类型、包长度和数据包写入TCP网络(wifi p2p)。
客户端接收数据解包的过程稍显复杂，在ConnectRunnable中实现。
解包过程使用了两个buffer，inputBuff(输入区)和cacheBuff(缓存)，inputBuff用于从socketChannel中读取数据。
由于inputBuff中得到数据包有可能是不完整的，因此需要用cacheBuff缓存数据包，cacheBuff中数据包内容全部收妥后，提交给消费者。
消费者根据包类型，将数据包解析为文字数据或音频数据。
用流程图表示比较直观，请参见图：NIO解包流程

以下对重点模块做一些简单说明。

## 公共模块 p2p_comm

### PcmTransferData
这是一个继承了Serializable的数据类，用来表示一帧音频数据。数据产生于服务器的解码器，注入socket，经过wifi p2p，传送到客户端，它包括三个成员：
- sampleRateInHz 采样率
- pcmData 音频pcm数据
- frameCount 帧计数

### DirectBroadcastReceiver
这是一个专用于Wifi直连(p2p)功能的广播接收器，服务器和客户端都要用到。这里定义了几个wifi p2p事件的相应。
- WIFI_P2P_STATE_CHANGED_ACTION
  wifi p2p状态发生变更，根据状态可以判断本机wifi p2p功能是否可用。
- WIFI_P2P_PEERS_CHANGED_ACTION
  wifi p2p成员列表发生变化，此时可以请求获得成员列表。
- WIFI_P2P_DISCOVERY_CHANGED_ACTION
  wifi p2p的成员搜索发现过程发生变化，由停止转为启动，或者由启动转为停止。
- WIFI_P2P_CONNECTION_CHANGED_ACTION
  wifi p2p的连接状态发生变化，如果是连接成功状态，可以请求获得连接情报。
- WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
  本机设备发生变化，如果此时本机设备可用，可以请求获得本机设备情报。
-
### IDirectActionListener
这是在服务器和客户端都必须实现的wifi p2p事件相应接口。服务器和客户端的实现方法有所不同。
- onWifiP2pEnabled
  wifi p2p功能可用时触发。
- onConnectionInfoAvailable
  wifi p2p连接情报有效时触发。
- onDisconnection
  wifi p2p连接断开时触发。
- onSelfDeviceAvailable
  本机设备有效时触发。
- onPeersAvailable
  wifi p2p成员列表有效时触发。
- onP2pDiscoveryStopped
  wifi p2p成员搜索发现过程停止时触发。
-

## 播放器(服务器) audio_player

### ConnectIntentService
wifi p2p连接建立后，这个IntentService主要负责实现非阻塞的socket通信。
这里涉及三个线程：
- ConnectIntentService所在的主线程，建立ServerSocketChannel，提供客户端连接的公共服务平台，接收每一个客户端发送的消息，然后通过监听器发送到MainViewModel。
- msgOutputThread消息输出线程，通过OutputMsgHandler接收来自MainViewModel的消息，打包发送给相关客户端。
- outputAudioThread音频输出线程，使用同步阻塞队列SynchronousQueue的take()方法取得解码后的PCM数据
  （PCM数据是在DecoderCallback中用SynchronousQueue的put()方法注入同步阻塞队列），打包以后发送给每一个已连接的客户端。
### DecoderCallback
这是MP3音频解码器所需的回调。在PlayThreadHandler中将其注入解码器。主要相应以下事件：
- onInputBufferAvailable，当解码器输入缓存可用时，把MediaExtractor解析后的MP3数据填入解码器输入缓存，再把输入缓存推入解码队列。
- onOutputBufferAvailable，当解码器输出缓存可用时，从输出缓存中取得解码后的PCM数据。
  PCM数据有两个出路，① 用SynchronousQueue的put()方法注入同步阻塞队列 ② 供本地AudioTrack.write()使用。
  随后释放解码器输出缓存。
### DummyPlayerRunnable
在本地播放音频所需playThread的Runnable实体，在其中使用Looper，循环调用PlayThreadHandler。
### PlayThreadHandler
接收并执行来自MainViewModel的与本地音频播放有关的各种指令。
- PLAY
- PAUSE
- STOP
- MUTE
- RESUME
- UNMUTE
### MainViewModel
安卓体系结构组件之一，集成处理逻辑和数据。主要有以下处理：
- 建立wifi p2p群组，实例化并注册DirectBroadcastReceiver
- 启动playThread
- 实现wifi p2p事件相应接口
- 执行MainFragment发出的各种命令，主要与本地音频播放相关。向MainFragment发出的信息用LiveData实现。
- 监听处理来自ConnectIntentService的各种消息，包括本地消息和来自客户端的远程消息。
### MainFragment
用户界面虽然有wifi p2p建组和删组按钮，通常不需要使用，系统在初始化时已经完成wifi p2p建组。
来自MainViewModel的消息，用观察者模式处理。
为了减少if-else逻辑，对于本地音频播放器按钮采用状态模式处理，缺点是增加了不少状态类。

## 接收器(客户端)
### ConnectRunnable
客户端socket线程的执行部分，用NIO机制实现信息接收和发送。这里用到两个线程：
- ConnectRunnable所在的主线程，用ThreadHandler接收来自MainViewModel的文字消息，经NIO发送给服务器。
- 读取服务器信息的inputThread，完成服务器信息的解析，经过MainViewModel送达消费者。
### PcmPlayer
这是来自服务器的音频数据的消费者，用AudioTrack播放音频。
该类初始化时，可以控制播放器的左右声道。
### MainViewModel
同服务器MainViewModel功能相似。

## 可能改进的方向
- 今后使用更高效的AIO代替NIO，但是低版本android不支持AIO，考虑兼容范围目前只能使用NIO。
- 如果今后线程增加较多，考虑使用协程代替线程。
- 由于数据传输过程使用各种buffer，每个客户端播放的音频会有不同的时延，因此需要一种同步机制以控制音频播放时延。也许使用UDP协议有可能减少音频时延。

如有问题、BUG、指摘，请联系：wxson@126.com
