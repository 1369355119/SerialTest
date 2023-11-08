package com.android.lib.utils;

interface ISerialPortReadCallback {

    /**
     * 注册监听器后，调用startRead后，将读到的数据通过该方法返回
     *
     * @param dev    数据对应的设备号
     * @param bytes  读出的内容
     * @param length 内容长度
     */
    void onReadReceived(int dev, inout byte[] bytes, int length);
}