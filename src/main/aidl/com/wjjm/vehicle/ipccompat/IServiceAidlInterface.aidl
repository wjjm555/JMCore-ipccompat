package  com.wjjm.vehicle.ipccompat;

import  com.wjjm.vehicle.ipccompat.IProxyAidlInterface;
import  com.wjjm.vehicle.ipccompat.model.MessageBean;

interface IServiceAidlInterface {

    void setProxy(String key,IProxyAidlInterface proxy);

    MessageBean sendMessage(in MessageBean message);

    oneway void sendMessageVoidAsync(in MessageBean message);

    oneway void sendMessageAsync(in MessageBean message,IProxyAidlInterface proxy);
}
