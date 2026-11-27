# WJCore · ipccompat

English | [中文](README.md)

## 1. The Series

**WJCore** is a series of open-source Android/Java framework libraries sharing the `com.wjjm.vehicle` namespace, all released under the Apache License 2.0. Each module in the series has a single responsibility and low coupling, and can be referenced independently.

| Module | Description |
| --- | --- |
| **ipccompat** (this repository) | Cross-process communication framework wrapping AIDL/Binder, providing bidirectional sync/async/callback invocation |
| log | Logging framework (logcat + disk persistence, derived from orhanobut/logger, Apache-2.0) |
| datacenter | Data center for cross-process data storage based on ContentProvider + Room |
| https | Unified HTTP/HTTPS client based on OkHttp + Retrofit |
| process | Rule and executor framework (InputRule / Actuator) |
| determiner | Condition evaluation framework (Compare / Determine) |
| filter | Filter framework |
| test | Test annotation framework |

See the respective repositories for the other modules. Development environment: Android Studio, minimum supported **Android 7.0 (API 24) and above**.

## 2. About the Framework

ipccompat wraps Android AIDL cross-process communication with the following features:

- **No AIDL authoring** — the client and the server only need to implement the same Java interface (with framework-allowed differences, see "Notes") and call the methods provided by the framework
- **Bidirectional communication** — the client can call the server, and the server can call all / specific clients (sync, async, with return values)
- **Callback support** — one-shot generic callbacks (IPCCallBack) and long-lived custom AIDL callbacks (IInterface)
- **Auto connect & reconnect** — binds automatically on the first call; reconnects automatically after the service is killed, with automatic re-registration of callbacks
- **Fault tolerance** — the framework never throws exceptions to callers; business exceptions are handled via logs + callback + default return values
- **State awareness** — callbacks for connection state, method-not-found, service-not-found, service restart, etc.
- Minimum API 24: `bindService(Executor)` on API 29+; reflective `bindServiceAsUser` on API 24–28

## 3. Notes (Important)

The behaviors below are **by-design mechanisms, not defects**. Make sure they fit your use case:

1. **Business interface code must not be obfuscated (mandatory).**
   Protocol method names are extracted from the call stack, and the peer looks methods up by name via reflection. If your release build obfuscates business interface class/method names, the two sides will never agree on method names and the peer will always get `NoSuchMethod`. Add at least:
   ```proguard
   -keep class * extends com.wjjm.vehicle.ipccompat.client.IPCClient { *; }
   -keep class * extends com.wjjm.vehicle.ipccompat.service.IPCService { *; }
   -keep class * implements com.wjjm.vehicle.ipccompat.interfaces.IPCInterface { *; }
   ```

2. **`invoke*` calls must be placed directly inside the business method body.**
   The method name is taken from the adjacent business method on the call stack. The call site must not be wrapped in a lambda or an anonymous class (the frame name would become `lambda$xxx` and the protocol cannot match).

3. **Methods must be declared in business interfaces, and both sides must agree on the method name, parameter count/order/types, and return type (with the following allowed differences).**
   - Difference 1: primitive types (byte/short/float/int/long/double/char/boolean) and their wrapper classes are interchangeable
   - Difference 2: the server interface may have one extra `Target` parameter at the **first** position (see API §6.2), which the client interface does not define
   - Varargs are not supported

4. **Supported types**: primitives and their wrappers, arrays, String, List, Map, Set, Queue, and custom types implementing Parcelable (with corresponding AIDL declarations). **Anything else is unsupported**; parameters **must not be null** (null is matched as `Object.class`, so the call fails).

5. **Business methods run on binder threads.**
   Sync calls occupy the peer's binder threads directly (a pool of about 16); slow business logic blocks subsequent requests and may even cause ANRs. Thread scheduling, if needed, is the caller's responsibility — the framework does not do it for you.

6. **The framework never throws exceptions to callers.**
   When the peer's business method throws: log + `onException(Throwable)` + a default return value is generated (numbers -1, boolean false, String "", objects attempted via `newInstance`, null on failure). Callers **cannot distinguish** "the business really returned the default value" from "the business failed and returned the default value".

7. **Messages sent while disconnected go into an unbounded cache; pending callbacks are discarded.**
   While disconnected, `invokeAsync`-style messages are cached (no capacity limit) and replayed in order after reconnection; unfinished callback waits are cleared on disconnect. Requests sent but not yet called back within the disconnect window are **silently lost** — the framework does not retry. Design your own retry/idempotency if needed.

8. **Client identity is based on a self-reported package name.**
   The server uses the caller-reported package name as the identity key, without UID verification, trusting callers that use the framework's IPCClient. Harden it yourself if you have strong security requirements.

9. **`disconnect()` semantics.**
   Active disconnect: unbind, discard stale callbacks of the old connection, fire `onServiceConnectStateChanged(Disconnected)`; calling it when a connection was never established produces no state callback. Invoking business methods again after a disconnect triggers automatic reconnection.

10. **Callback object rules.**
    - Generic callback `IPCCallBack`: **one-shot** — destroyed automatically after the server calls it once, and does not participate in auto re-registration
    - Custom AIDL callback `IInterface`: the server may hold it long-term and call it repeatedly; there is an automatic cleanup mechanism, but clients are advised to call `unregisterInterfaceAsync` before destruction to avoid leaks; unregistering only requires passing the same IInterface object — the trailing parameters may differ

11. **The connection adapter thread is persistent.** The HandlerThread inside IPCClient is not recycled during the process lifetime; use it as a process-level singleton.

12. **Auto re-registration only works for custom callbacks (IInterface)**, not for generic callbacks; disabled by default.

## 4. API

### 1. Initialization

#### 1.1 Client initialization

Calling `ContextManager.init(this)` in `Application.onCreate` is recommended (without it the framework falls back to reflection; if that also fails, the first use throws an `IllegalStateException` with a hint):

```java
public class MyApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        ContextManager.init(this);
    }
}
```

```java
IPCClient client = new IPCClient() {
    @Override
    protected String getTargetPackage() {
        return "com.example.mytestapplication";  // target service package name
    }
    @Override
    protected String getTargetService() {
        return "com.example.mytestapplication.MyService";  // fully-qualified target service class name
    }
};
```

#### 1.2 Server initialization

```xml
<service
    android:name=".MyService"
    android:enabled="true"
    android:exported="true" />
```

> If foreground keep-alive is needed (the service is started via `startService` / `startForegroundService`): API 28+ requires the `FOREGROUND_SERVICE` permission; targetSdk 34+ additionally requires `android:foregroundServiceType` (e.g. `dataSync`), otherwise `startForeground` throws `MissingForegroundServiceTypeException`. Bind-only usage (bindService) is unaffected.

```java
public class MyService extends IPCService {
}
```

### 2. Register / Unregister Methods

The class containing the callable methods must be registered with the framework first. Both client and server support this, and multiple interface implementations can be registered at once:

```java
/** Add methods callable by the peer; the interface must extend IPCInterface */
public <T extends IPCInterface> void addInterface(T... t);

/** Remove previously registered methods */
public <T extends IPCInterface> void removeInterface(T... t);
```

```java
public interface TestCommonInterface extends IPCInterface {
    void testMethodSync(int anInt, String aString);
    void testMethodAsync(int anInt, String aString);
    String testMethodReturn(int anInt, String aString);
}

// client
TestCommonInterface api = new TestCommonInterfaceImp(client);
client.addInterface(api);

// server (the implementation calls framework methods internally to do the IPC)
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

### 3. Connect / Disconnect

```java
/** Bind the service proactively (optional): the first call to a server method connects automatically */
boolean connect();

/** Release the binding proactively; call it when the client exits */
void disconnect();
```

### 4. Client Calls Server

#### 4.1 Sync method without a return value

Synchronous: the client continues only after the server has finished processing the same method.

```java
void invokeSync(Object... objects);
```

```java
// client: call inside the business method body
class TestCommonInterfaceImp implements TestCommonInterface {
    TestCommonInterfaceImp(IPCClient client) { this.client = client; }
    final IPCClient client;
    @Override public void testMethodSync(int anInt, String aString) {
        client.invokeSync(anInt, aString);
    }
    //...
}
```

#### 4.2 Async method without a return value

Asynchronous: no need to wait for the server to finish processing.

```java
void invokeAsync(Object... objects);
```

#### 4.3 Sync method with a return value

Synchronous call that returns the execution result.

```java
<T> T invokeReturn(Class<T> tClass, Object... objects);
```

```java
@Override public String testMethodReturn(int anInt, String aString) {
    return client.invokeReturn(String.class, anInt, aString);
}
```

### 5. Client Calls Server with Callbacks

#### 5.1 Generic callback (one-shot)

Async only; transfers a single value of a framework-supported type; **the server can use it only once, after which it is destroyed automatically**. `IPCCallBack` must be the **first parameter** of the method.

```java
<T> void registerIPCCallBackAsync(IPCCallBack<T> callBack, Object... objects);
```

```java
// client
clientTest.testMethodRegisterIPCCallBackAsync(new IPCCallBack<String>() {
    @Override public void onResultBack(String result) {
        Log.i("IPCClient", "onResultBack:" + result);
    }
}, "This is IPCCallBack from client");

// server (may call back from a worker thread)
class TestCallBackInterfaceImp implements TestCallBackInterface {
    @Override public void testMethodRegisterIPCCallBackAsync(IPCCallBack<String> callBack, String aString) {
        Log.i("IPCService", "testMethodRegisterIPCCallBackAsync:" + aString);
        callBack.onResultBack("This is IPCCallBack result");
    }
}
```

#### 5.2 Custom AIDL callback (long-lived)

Pass a custom AIDL callback object; async only; transfers only framework-supported types. **The server may hold it long-term and call it repeatedly**; there is an automatic cleanup mechanism, but unregistering before client destruction is advised. `IInterface` must be the **first parameter** of the method; to unregister, only the same IInterface object is required — trailing parameters may differ.

```java
void registerInterfaceAsync(IInterface iInterface, Object... objects);
void unregisterInterfaceAsync(IInterface iInterface, Object... objects);
```

```java
// IMyAidlInterface.aidl
interface IMyAidlInterface {
    void basicTypes(int anInt, long aLong, String aString);
}

// client
IMyAidlInterface myAidlInterface = new IMyAidlInterface.Stub() {
    @Override public void basicTypes(int anInt, long aLong, String aString) {
        Log.w("IPCClient", "basicTypes:" + aString);
    }
};
clientTest.testMethodRegisterInterfaceAsync(myAidlInterface, "register from client");
// ... 
clientTest.testMethodUnregisterInterfaceAsync(myAidlInterface, "unregister from client");

// server (may call multiple times from worker threads)
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

### 6. Server Calls Client

#### 6.1 Call all clients

```java
/** All clients, sync without return value: waits for each bound client in order; may block the service */
void invokeAllSync(Object... objects);

/** All clients, async without return value: does not wait for clients; does not block the service */
void invokeAllAsync(Object... objects);
```

Server example (print inside the interface implementation registered on the client side):

```java
// inside the server implementation class
class TestToAllInterfaceImp implements TestToAllInterface {
    @Override public void testMethodInvokeAllSync(String aString) {
        invokeAllSync(aString);
    }
    @Override public void testMethodInvokeAllAsync(String aString) {
        invokeAllAsync(aString);
    }
}
```

#### 6.2 Call a specific client (Target)

**Target must be the first parameter of the method** (defined only in the server interface; the client interface does not define it — Difference 2). Target cannot be created by yourself: when a client calls a non-callback server method, the server may add a `Target` at the first parameter position, generated automatically; the client can get the package name via `onBind(Target)` / `onUnbind(Target)`, caching, or `Target.toString()`.

```java
/** Specific client, sync without return value: waits for the client to finish; may block the service */
void invokeToSync(Target target, Object... objects);

/** Specific client, async without return value: does not block the service */
void invokeToAsync(Target target, Object... objects);

/** Specific client, sync with return value: waits for the client to finish and returns the result */
<T> T invokeToReturn(Class<T> tClass, Target target, Object... objects);
```

```java
// server: cache the Target and use it inside business methods
@Override public void testMethodInvokeToSync(Target rTarget, String aString) {
    invokeToSync(rTarget, aString);
}
@Override public String testMethodInvokeToReturn(Target rTarget, String aString) {
    return invokeToReturn(String.class, rTarget, aString);
}

// client: handle and return inside the corresponding business method
@Override public String testMethodInvokeToReturn(String aString) {
    Log.i("IPCClient", "testMethodInvokeToReturn:" + aString);
    return "This is testMethodInvokeToReturn from client";
}
```

### 7. Configuration & Callbacks

#### 7.1 Client auto re-registration

After the service crashes / is killed, automatically rebind and re-register custom callbacks after the specified delay. **Only effective for custom AIDL callbacks and callback-free interfaces; not for generic callbacks; disabled by default.**

```java
new IPCClient() {
    @Override protected boolean isAutoReregister() {
        return true;  // default false
    }
    @Override protected long autoReregisterDelay() {
        return 10 * 1000;  // default 5000, milliseconds, values below 1000 not recommended
    }
    // implement getTargetPackage / getTargetService ...
};
```

#### 7.2 Client service state callbacks

```java
@Override
protected void onServiceConnectStateChanged(int state) {
    super.onServiceConnectStateChanged(state);  // see "Status codes" for the codes
}

@Override
public void onServiceNotFound(String packageName, String serviceName) {
    super.onServiceNotFound(packageName, serviceName);  // usually the target service is missing or a permission issue
}
```

#### 7.3 Method-related callbacks

Called when the peer invokes a method that does not exist or is not registered on this side:

```java
@Override
public void onMethodNotFound(String methodName) {
    super.onMethodNotFound(methodName);  // the name of the method that failed to be invoked
}
```

Supported by both client and server.

#### 7.4 Server lifecycle callbacks

Provided by the framework in addition to the native Android Service lifecycle:

```java
public class MyService extends IPCService {
    @Override
    public void onRestart() {
        super.onRestart();  // fired after the service is killed and restarted by this framework (after clients finish proxy registration)
    }

    @Override
    protected void onBind(Target target) {
        super.onBind(target);  // replaces the native onBind(Intent); the client package name is available via target.toString(); cache the Target here if needed
    }

    @Override
    protected void onUnbind(Target target) {
        super.onUnbind(target);  // replaces the native onUnbind(Intent)
    }
}
```

### 8. Status Codes

| State | Code | Description |
| --- | :---: | --- |
| Connected | 0xA | Connected successfully |
| Disconnected | 0xB | Disconnected (including active disconnect) |
| BindingDied | 0xC | Connection died (peer process killed) |
| NullBinding | 0xD | Null binding |

## License

Apache License 2.0, see [LICENSE](LICENSE).

- Debugging: default log tag `IPCCompat_*`; send the broadcast `com.wjjm.vehicle.ipccompat.log` to a specific process (with `--es p <package>`, optionally `--es d true/false`) to toggle its debug logging at runtime.
