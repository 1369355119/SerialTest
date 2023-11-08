# SerialTest
千寻手持机开发demo


外部应用程序使用RD/RN的串口数据

在当前的手机系统中，集成了一个串口服务，外部应用程序可通过AIDL直接访问串口设备，进行数据的交互。
（1）AIDL文件见文件夹“串口服务AIDL文件”，接口说明见文件中的注释。
（2）服务使用bind的方式进行，代码如下：
        Intent intent = new Intent("android.dev.SERIAL_PORT_SERVICE");
        intent.setPackage("com.intercom.service");
        bindService(intent, this, BIND_AUTO_CREATE);
（3）接口使用的参考代码见“SerialTest”。
