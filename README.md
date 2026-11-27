# WJCore · ipccompat

[English](README_EN.md) | 中文

## 一、系列介绍

**WJCore** 是一系列面向 Android / Java 的开源框架库,统一使用 `com.wjjm.vehicle` 命名空间,全部采用 Apache License 2.0 协议发布。系列内模块职责单一、低耦合,可按需单独引用。

| 模块 | 说明 |
| --- | --- |
| **ipccompat**(本仓库) | 跨进程通信框架,封装 AIDL/Binder,提供双向同步、异步、回调调用 |
| log | 日志框架(logcat + 磁盘落盘,源自 orhanobut/logger,Apache-2.0) |
| datacenter | 数据中心,基于 ContentProvider + Room 的跨进程数据存储 |
| https | 网络封装,基于 OkHttp + Retrofit 的统一 HTTP/HTTPS 客户端 |
| process | 规则与执行器框架(InputRule / Actuator) |
| determiner | 条件判定框架(Compare / Determine) |
| filter | 过滤器框架 |
| test | 测试注解框架 |

其余模块的介绍与文档见各自仓库。本仓库开发环境:Android Studio,最低支持 **Android 7.0(API 24)及以上**。

## 二、框架介绍

ipccompat 封装了 Android AIDL 跨进程通信,特点如下:

- **无需编写 AIDL** —— 客户端与服务端只需编写相同的 Java 接口(允许符合框架要求的差异,见"注意事项")并调用框架提供的方法
- **双向通信** —— 客户端可调用服务端,服务端也可调用所有 / 指定客户端(同步、异步、带返回值)
- **回调支持** —— 提供一次性通用回调(IPCCallBack)与可长期持有的自定义 AIDL 回调(IInterface)
- **自动连接与重连** —— 首次调用自动绑定;服务被杀后可自动重连,并支持回调的自动重注册
- **容错** —— 框架不向调用方抛出异常,业务异常以日志 + 回调 + 默认返回值的方式处理
- **状态感知** —— 连接状态、方法未找到、服务未找到、服务重启等均有回调
- 最低支持 API 24:API 29+ 使用 `bindService(Executor)`;API 24–28 通过反射走 `bindServiceAsUser`

## 三、注意事项(重要)

以下行为是框架的**既定机制而非缺陷**,使用前请确认符合你的场景:

1. **业务接口代码不能混淆(必须)。**
   协议方法名通过调用栈提取,对端按方法名反射查找。release 构建若混淆了业务接口类/方法名,两端方法名必然不一致,对端将永远 `NoSuchMethod`。请至少添加:
   ```proguard
   -keep class * extends com.wjjm.vehicle.ipccompat.client.IPCClient { *; }
   -keep class * extends com.wjjm.vehicle.ipccompat.service.IPCService { *; }
   -keep class * implements com.wjjm.vehicle.ipccompat.interfaces.IPCInterface { *; }
   ```

2. **`invoke*` 必须直接写在业务方法体内。**
   方法名取自调用栈中紧邻的业务方法,调用处不能是 lambda、匿名类等包装(栈帧名会变成 `lambda$xxx`,协议无法匹配)。

3. **方法必须声明在业务接口中,且两端同名方法的方法名、参数数量/顺序/类型、返回值类型必须一致(允许下述差异)。**
   - 差异 1:基本类型(byte/short/float/int/long/double/char/boolean)与其封装类可以混合使用
   - 差异 2:服务端接口可比客户端接口在**第一个参数**位置多一个 `Target` 参数(见 API 第 6.2 节),客户端接口无需定义
   - 不支持动态参数

4. **支持的类型**:基本类型及其封装类、数组、String、List、Map、Set、Queue、实现了 Parcelable 的自定义类型(需提供对应 AIDL 声明)。**除此之外的类型不支持**;参数**不允许传 null**(null 会按 `Object.class` 匹配方法,调用失败)。

5. **业务方法在 binder 线程上执行。**
   同步调用会直接占用对端的 binder 线程(池约 16 个),慢业务会堵塞后续请求甚至导致 ANR。是否需要开线程调度由业务方负责,框架不代为调度。

6. **框架不向调用方抛异常。**
   对端业务方法抛异常时:记录日志 + 触发 `onException(Throwable)` + 按默认规则生成返回值(数字 -1、boolean false、String ""、对象尝试 `newInstance`,失败为 null)。调用方**无法区分**"业务真实返回了默认值"与"业务失败返回了默认值"。

7. **断线期间的调用进入无上限缓存,等待中的回调会被丢弃。**
   断线时 `invokeAsync` 等消息进入缓存(无容量上限),重连成功后按序重放;断线时未完成的回调等待被清理。即断线窗口内已发出但未回调的请求会**静默丢失**,框架不做重试,业务方按需自行设计重试/幂等。

8. **客户端身份基于自报包名。**
   服务端以调用方上报的包名作为身份 key,不做 UID 校验,信任调用方使用框架提供的 IPCClient。如有强安全要求请自行加固。

9. **`disconnect()` 语义。**
   主动断开:解绑连接、丢弃旧连接的过期回调、触发 `onServiceConnectStateChanged(Disconnected)`;从未连接成功时调用不产生状态回调。断开后再次调用业务方法会触发自动重连。

10. **回调对象规则。**
    - 通用回调 `IPCCallBack`:**一次性**,服务端调用一次后自动销毁,且不参与自动重注册
    - 自定义 AIDL 回调 `IInterface`:服务端可长期持有、多次调用;有自动清理机制,但建议客户端销毁前调用 `unregisterInterfaceAsync` 避免内存泄漏;解注册时仅需传入同一 IInterface 对象,后续参数可不同

11. **连接适配器线程常驻。** IPCClient 内部的 HandlerThread 在进程生命周期内不回收,请按进程级单例使用。

12. **自动重注册仅对自定义回调(IInterface)有效**,对通用回调无效,默认关闭。

## 四、API

### 1. 初始化

#### 1.1 客户端初始化

```java
IPCClient client = new IPCClient() {
    @Override
    protected String getTargetPackage() {
        return "com.example.mytestapplication";  // 目标服务包名
    }
    @Override
    protected String getTargetService() {
        return "com.example.mytestapplication.MyService";  // 目标服务完整类名
    }
};
```

#### 1.2 服务端初始化

```xml
<service
    android:name=".MyService"
    android:enabled="true"
    android:exported="true" />
```

```java
public class MyService extends IPCService {
}
```

### 2. 注册 / 解注册方法

被调用的方法所在类必须先注册到框架,客户端与服务端都支持,可一次注册多个接口的实现:

```java
/** 添加可被对端调用的方法,接口需继承 IPCInterface */
public <T extends IPCInterface> void addInterface(T... t);

/** 移除已注册的方法 */
public <T extends IPCInterface> void removeInterface(T... t);
```

```java
public interface TestCommonInterface extends IPCInterface {
    void testMethodSync(int anInt, String aString);
    void testMethodAsync(int anInt, String aString);
    String testMethodReturn(int anInt, String aString);
}

// 客户端
TestCommonInterface api = new TestCommonInterfaceImp(client);
client.addInterface(api);

// 服务端(实现类内部调用框架方法完成跨进程通信)
public class MyService extends IPCService {
    private final TestCommonInterface api = new TestCommonInterfaceImp(this);
    @Override public void onCreate() {
        super.onCreate();
        addInterface(api);
    }
    @Override public void onDestroy() {
        super.onDestroy();
        removeInterface(api);
    }
}
```

### 3. 连接 / 断连

```java
/** 主动绑定服务(非必要):首次调用服务端方法时会自动连接 */
boolean connect();

/** 主动释放绑定,客户端退出时调用 */
void disconnect();
```

### 4. 客户端调用服务端

#### 4.1 无返回值同步接口

同步调用:等服务端同方法内部处理结束后,客户端才继续执行。

```java
void invokeSync(Object... objects);
```

```java
// 客户端:在业务方法体内调用
class TestCommonInterfaceImp implements TestCommonInterface {
    TestCommonInterfaceImp(IPCClient client) { this.client = client; }
    final IPCClient client;
    @Override public void testMethodSync(int anInt, String aString) {
        client.invokeSync(anInt, aString);
    }
    //...
}
```

#### 4.2 无返回值异步接口

异步调用:无需等待服务端处理结束。

```java
void invokeAsync(Object... objects);
```

#### 4.3 有返回值同步接口

同步调用并取回执行结果。

```java
<T> T invokeReturn(Class<T> tClass, Object... objects);
```

```java
@Override public String testMethodReturn(int anInt, String aString) {
    return client.invokeReturn(String.class, anInt, aString);
}
```

### 5. 客户端调用服务端有回调的接口

#### 5.1 通用回调(一次性)

只支持异步调用,只传输一个框架支持类型的数据;**服务端只能使用一次,之后自动销毁**。定义方法时 `IPCCallBack` 必须是**第一个参数**。

```java
<T> void registerIPCCallBackAsync(IPCCallBack<T> callBack, Object... objects);
```

```java
// 客户端
clientTest.testMethodRegisterIPCCallBackAsync(new IPCCallBack<String>() {
    @Override public void onResultBack(String result) {
        Log.i("IPCClient", "onResultBack:" + result);
    }
}, "This is IPCCallBack from client");

// 服务端(可在子线程中回调)
class TestCallBackInterfaceImp implements TestCallBackInterface {
    @Override public void testMethodRegisterIPCCallBackAsync(IPCCallBack<String> callBack, String aString) {
        Log.i("IPCService", "testMethodRegisterIPCCallBackAsync:" + aString);
        callBack.onResultBack("This is IPCCallBack result");
    }
}
```

#### 5.2 自定义 AIDL 回调(可长期持有)

传入自定义 AIDL 回调对象,只支持异步调用,只传输框架支持类型的数据。**服务端可长期持有、多次调用**;有自动清理机制,建议客户端销毁前解注册。定义方法时 `IInterface` 必须是**第一个参数**;解注册时仅保证 IInterface 对象相同即可,后续参数可不同。

```java
void registerInterfaceAsync(IInterface iInterface, Object... objects);
void unregisterInterfaceAsync(IInterface iInterface, Object... objects);
```

```java
// IMyAidlInterface.aidl
interface IMyAidlInterface {
    void basicTypes(int anInt, long aLong, String aString);
}

// 客户端
IMyAidlInterface myAidlInterface = new IMyAidlInterface.Stub() {
    @Override public void basicTypes(int anInt, long aLong, String aString) {
        Log.w("IPCClient", "basicTypes:" + aString);
    }
};
clientTest.testMethodRegisterInterfaceAsync(myAidlInterface, "register from client");
// ... 
clientTest.testMethodUnregisterInterfaceAsync(myAidlInterface, "unregister from client");

// 服务端(可在子线程中多次调用)
class TestCallBackInterfaceImp implements TestCallBackInterface {
    @Override public void testMethodRegisterInterfaceAsync(IMyAidlInterface iInterface, String aString) {
        Log.i("IPCService", "testMethodRegisterInterfaceAsync:" + aString);
        iInterface.basicTypes(1, 2L, "This is IMyAidlInterface result");
    }
    @Override public void testMethodUnregisterInterfaceAsync(IMyAidlInterface iInterface, String aString) {
        Log.i("IPCService", "testMethodUnregisterInterfaceAsync:" + aString);
    }
}
```

### 6. 服务端调用客户端

#### 6.1 调用所有客户端

```java
/** 所有客户端,无返回值同步:按绑定顺序逐个等待处理完成,可能阻塞服务 */
void invokeAllSync(Object... objects);

/** 所有客户端,无返回值异步:不等待客户端处理,不阻塞服务 */
void invokeAllAsync(Object... objects);
```

服务端示例(客户端侧注册的接口实现里打印即可):

```java
// 服务端实现类内部
class TestToAllInterfaceImp implements TestToAllInterface {
    @Override public void testMethodInvokeAllSync(String aString) {
        invokeAllSync(aString);
    }
    @Override public void testMethodInvokeAllAsync(String aString) {
        invokeAllAsync(aString);
    }
}
```

#### 6.2 调用指定客户端(Target)

**Target 必须是方法的第一个参数**(仅在服务端接口定义,客户端接口无需定义;差异 2)。Target 无法自行创建:客户端调用服务端的非回调方法时,服务端可在方法第一个参数位置增加 `Target` 自动生成;客户端通过 `onBind(Target)` / `onUnbind(Target)` 或缓存、`Target.toString()` 获取包名信息。

```java
/** 指定客户端,无返回值同步:等待客户端处理完成,可能阻塞服务 */
void invokeToSync(Target target, Object... objects);

/** 指定客户端,无返回值异步:不阻塞服务 */
void invokeToAsync(Target target, Object... objects);

/** 指定客户端,有返回值同步:等待客户端处理完成并返回结果 */
<T> T invokeToReturn(Class<T> tClass, Target target, Object... objects);
```

```java
// 服务端:缓存 Target,业务方法内使用
@Override public void testMethodInvokeToSync(Target rTarget, String aString) {
    invokeToSync(rTarget, aString);
}
@Override public String testMethodInvokeToReturn(Target rTarget, String aString) {
    return invokeToReturn(String.class, rTarget, aString);
}

// 客户端:在对应业务方法内处理并返回
@Override public String testMethodInvokeToReturn(String aString) {
    Log.i("IPCClient", "testMethodInvokeToReturn:" + aString);
    return "This is testMethodInvokeToReturn from client";
}
```

### 7. 配置与回调

#### 7.1 客户端自动重注册

服务崩溃/被杀后,延迟指定时间自动重新绑定并重注册自定义回调。**仅对自定义 AIDL 回调和无回调接口有效,对通用回调无效;默认关闭。**

```java
new IPCClient() {
    @Override protected boolean isAutoReregister() {
        return true;  // 默认 false
    }
    @Override protected long autoReregisterDelay() {
        return 10 * 1000;  // 默认 5000,单位毫秒,不建议小于 1000
    }
    // 实现 getTargetPackage / getTargetService ...
};
```

#### 7.2 客户端服务状态回调

```java
@Override
protected void onServiceConnectStateChanged(int state) {
    super.onServiceConnectStateChanged(state);  // 状态码见"状态码说明"
}

@Override
public void onServiceNotFound(String packageName, String serviceName) {
    super.onServiceNotFound(packageName, serviceName);  // 通常是目标服务不存在或权限问题
}
```

#### 7.3 方法相关回调

对端调用但本端没有此方法或未注册时回调:

```java
@Override
public void onMethodNotFound(String methodName) {
    super.onMethodNotFound(methodName);  // 返回未成功调用的方法名
}
```

客户端与服务端均支持。

#### 7.4 服务端生命周期回调

框架在 Android 原生 Service 生命周期外提供:

```java
public class MyService extends IPCService {
    @Override
    public void onRestart() {
        super.onRestart();  // 服务被杀后,由本框架重新拉起的回调(客户端完成代理注册后触发)
    }

    @Override
    protected void onBind(Target target) {
        super.onBind(target);  // 替换原生 onBind(Intent);客户端包名可从 target.toString() 获取,可在此缓存 Target
    }

    @Override
    protected void onUnbind(Target target) {
        super.onUnbind(target);  // 替换原生 onUnbind(Intent)
    }
}
```

### 8. 状态码说明

| 状态 | 状态码 | 说明 |
| --- | :---: | --- |
| Connected | 0xA | 连接成功 |
| Disconnected | 0xB | 断开连接(含主动 disconnect) |
| BindingDied | 0xC | 连接死亡(对端进程被杀) |
| NullBinding | 0xD | 绑定为空 |

## 许可证

Apache License 2.0,详见 [LICENSE](LICENSE)。

- 调试:默认日志 tag `IPCCompat_*`;对指定进程发送广播 `com.wjjm.vehicle.ipccompat.log`(参数 `--es p <包名>`、可选 `--es d true/false`)可动态开关其调试日志。