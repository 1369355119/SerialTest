package com.android.serialtest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.lib.utils.ISerialPortControl;
import com.android.lib.utils.ISerialPortReadCallback;

public class MainActivity extends Activity implements View.OnClickListener, ServiceConnection {

    private TextView mTextView;

    private Spinner mPathSelect;
    private EditText mEditSend;
    private CheckBox mReceiveHexCheck;

    private Button mBtnOpen;
    private Button mBtnSend;

    private ScrollView mReceiveScroll;

    private int mCurrentDev; //1:RDSS, 2:RNSS, 3: DMR006
    private boolean isInTouching = false;
    private final int O_NONBLOCK = 0x800;

    private boolean isPowerOn = false;
    private ISerialPortControl mSerialPort;
    private SharedPreferences mPreferences;
    private final StringBuilder mReceiveString = new StringBuilder(1024 * 10);

    private final Handler mHandler = new Handler(Looper.myLooper()) {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0: //Update UI
                    if (msg.obj != null) {
                        if (mReceiveString.length() > 1024 * 9) {
                            mReceiveString.delete(0, 1024 * 3);
                        }
                        mReceiveString.append(msg.obj);
                        mTextView.setText(mReceiveString.toString());
                        if (!isInTouching) {
                            mReceiveScroll.postDelayed(mScrollDownRunnable, 20);
                        }
                    }
                    break;
            }
        }
    };

    private final Runnable mScrollDownRunnable = new Runnable() {

        @Override
        public void run() {
            mReceiveScroll.fullScroll(View.FOCUS_DOWN);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mTextView = (TextView) findViewById(R.id.port_text_view);
        mReceiveHexCheck = (CheckBox) findViewById(R.id.port_receive_hex_check_box);

        mEditSend = (EditText) findViewById(R.id.port_send_edit);

        mBtnOpen = (Button) findViewById(R.id.port_btn_open);
        mBtnSend = (Button) findViewById(R.id.port_btn_send);

        mReceiveScroll = (ScrollView) findViewById(R.id.port_receive_scroll_view);

        mPathSelect = (Spinner) findViewById(R.id.port_device_select);
        mPathSelect.requestFocus();
        int pathIndex = mPreferences.getInt("key_device_path_index", 0);
        if (pathIndex > mPathSelect.getCount()) {
            pathIndex = 0;
        }
        mPathSelect.setSelection(pathIndex);
        mPathSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mPreferences.edit().putInt("key_device_path_index", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mBtnSend.setEnabled(false);

        mBtnOpen.setOnClickListener(this);
        mBtnSend.setOnClickListener(this);

        mBtnOpen.setOnLongClickListener(new View.OnLongClickListener() { //长按打开按钮，清空已接收数据

            @Override
            public boolean onLongClick(View v) {
                mReceiveString.delete(0, mReceiveString.length());
                mTextView.setText(mReceiveString.toString());
                return true;
            }
        });

        mEditSend.setEnabled(false);
        mEditSend.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                //mBtnSend.setEnabled(s.length() > 0);
            }
        });

        Intent intent = new Intent("android.dev.SERIAL_PORT_SERVICE");
        intent.setPackage("com.intercom.service");
        bindService(intent, this, BIND_AUTO_CREATE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isInTouching = true;
                break;
            case MotionEvent.ACTION_UP:
                isInTouching = false;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在unbind之前，一定要把callback执行unregister
        try {
            if (mSerialPort != null) {
                mSerialPort.unregisterCallback(getPackageName(), mReadCallback);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        unbindService(this);
        mHandler.removeCallbacks(mSendBtnUpdateRunnable);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.port_btn_open:
                if (v.isSelected()) {
                    try {
                        if (mSerialPort != null) {
                            if (!isPowerOn) {
                                mSerialPort.setPower(mCurrentDev, false);
                            }
                            mSerialPort.close(mCurrentDev);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    mBtnSend.setEnabled(false);
                    mBtnOpen.setSelected(false);
                    mBtnOpen.setText(R.string.title_open);
                    mEditSend.setEnabled(false);
                    mPathSelect.setEnabled(true);
                    mCurrentDev = 0;
                } else {
                    int ret = -1;
                    mCurrentDev = mPathSelect.getSelectedItemPosition() + 1;
                    try {
                        if (mSerialPort != null) {
                            isPowerOn = mSerialPort.isPowerOn(mCurrentDev);
                            if (!isPowerOn) {
//                                mSerialPort.setPower(mCurrentDev, true);
                            }
                            ret = mSerialPort.open(mCurrentDev, 0);
                            if (ret == 0) { //0:打开成功，非0：打开失败
                                mSerialPort.startRead(mCurrentDev);
                            }
                        } else {
                            Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show();
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    if (ret == 0) {
                        mBtnSend.setEnabled(true);
                        mBtnOpen.setSelected(true);
                        mBtnOpen.setText(R.string.title_close);
                        mEditSend.setEnabled(true);
                        mPathSelect.setEnabled(false);
                    } else if (ret > 0) {
                        Toast.makeText(this, getString(R.string.toast_message_open_device_fail) + "(0x" + Integer.toHexString(ret) + ")", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case R.id.port_btn_send:
                if (mSerialPort != null) {
                    String sendString = mEditSend.getText().toString();
                    String string = "+++[" + sendString + "]+++\n";
                    mHandler.obtainMessage(0, string).sendToTarget();
                    mPreferences.edit().putString("temp_send_string", sendString).apply();
                    try {
                        if (mCurrentDev != 0 && mSerialPort.write(mCurrentDev, (sendString + "\r\n").getBytes()) != 0) { //非0：写入失败
                            Toast.makeText(this, R.string.toast_message_write_value_fail, Toast.LENGTH_SHORT).show();
                        }
                        mBtnSend.setEnabled(false);
                        mHandler.postDelayed(mSendBtnUpdateRunnable, 200);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    private String toHexString(byte[] bytes, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(hex(bytes[i]));
            builder.append(" ");
        }
        return builder.toString();
    }

    private String hex(byte value) {
        String tmp = Integer.toHexString(value & 0xFF);
        if (tmp.length() == 1) {
            tmp = "0" + tmp;
        }
        return tmp.toUpperCase();
    }

    private final Runnable mSendBtnUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            mBtnSend.setEnabled(true);
        }
    };

    private final ISerialPortReadCallback mReadCallback = new ISerialPortReadCallback.Stub() {

        @Override
        public void onReadReceived(int dev, byte[] bytes, int length) {
            if (mCurrentDev == dev) {
                String result = "";
                if (mReceiveHexCheck.isChecked()) {
                    result = toHexString(bytes, length);
                } else {
                    try {
                        result = new String(bytes, 0, length, "GB2312");
                    } catch (Exception e) {
                        result = new String(bytes, 0, length);
                    }
                }
                mHandler.obtainMessage(0, result).sendToTarget();
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mSerialPort = ISerialPortControl.Stub.asInterface(service);
        try {
            mSerialPort.registerCallback(getPackageName(), mReadCallback);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
}