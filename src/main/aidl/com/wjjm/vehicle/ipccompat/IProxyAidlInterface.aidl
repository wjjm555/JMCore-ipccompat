package  com.wjjm.vehicle.ipccompat;

import  com.wjjm.vehicle.ipccompat.model.MessageBean;

interface IProxyAidlInterface {

    MessageBean sendMessage(in MessageBean message);

    oneway void sendMessageVoidAsync(in MessageBean message);

    oneway void messageIPCCallBack(in MessageBean message);
}
