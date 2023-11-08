package com.android.lib.utils;

import com.android.lib.utils.ISerialPortReadCallback;

/**
 * dev设备参数说明：
 * 1. RDSS模块
 * 2. RNSS模块
 */
interface ISerialPortControl {

    /**
     * 读取指定模块的电源状态。
     *
     * @param dev  设备号
     * @return     true:电源当前处于开启状态，false：电源当前处于关闭状态
     */
    boolean isPowerOn(int dev);

    /**
     * 设置模块的电源状态。
     *
     * @param dev  设备号
     * @param on   需要设置的状态
     * @return     是否设置成功
     */
    boolean setPower(int dev, boolean on);

    /**
     * 打开模块
     *
     * @param dev   设备号
     * @param flags 需要设置的标记。0:阻塞；0x800:非阻塞。更多flag请参考c语言标准open函数的flag参数
     * @return      0:打开成功，非0：打开失败
     */
    int open(int dev, int flags);

    /**
     * 关闭模块
     *
     * @param dev  设备号
     * @return     0:关闭成功，非0：关闭失败
     */
    int close(int dev);

    /**
     * 对选定设备进行写入操作
     *
     * @param dev   设备号
     * @param value 需要写入的内容
     * @return      0:写入成功，非0：写入失败
     */
    int write(int dev, in byte[] value);

    /**
     * 进入轮询读取。通过ISerialPortReadCallback接口返回读取结果
     *
     * @param dev  设备号
     */
    void startRead(int dev);

    /**
     * 停止读取。
     *
     * @param dev  设备号
     */
    void stopRead(int dev);

    /**
     * 注册读取状态回调
     *
     * @param tag       当前调用者的类名
     * @param callback  监听器
     */
    void registerCallback(String tag, ISerialPortReadCallback callback);

    /**
     * 取消注册读取状态回调
     *
     * @param tag       当前调用者的类名
     * @param callback  监听器
     */
    void unregisterCallback(String tag, ISerialPortReadCallback callback);
}