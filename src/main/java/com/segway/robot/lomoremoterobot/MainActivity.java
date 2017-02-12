package com.segway.robot.lomoremoterobot;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import java.io.UnsupportedEncodingException;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.segway.robot.algo.Pose2D;
import com.segway.robot.lomoremoterobot.comtroller.SimpleController;
import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.baseconnectivity.Message;
import com.segway.robot.sdk.baseconnectivity.MessageConnection;
import com.segway.robot.sdk.baseconnectivity.MessageRouter;
import com.segway.robot.sdk.connectivity.BufferMessage;
import com.segway.robot.sdk.connectivity.RobotException;
import com.segway.robot.sdk.connectivity.RobotMessageRouter;
import com.segway.robot.sdk.connectivity.StringMessage;

import com.segway.robot.sdk.locomotion.sbv.AngularVelocity;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.locomotion.sbv.LinearVelocity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import static android.R.attr.x;
import static android.R.attr.y;
import java.lang.ref.WeakReference;
import java.util.Set;

import static android.R.transition.move;

public class MainActivity extends Activity implements View.OnClickListener {

    public final String ACTION_USB_PERMISSION = "com.segway.robot.lomoremoterobot.USB_PERMISSION";
    private boolean isBind = false;
    Base mBase;
    private static final int RUN_TIME = 2000;
    private Timer mTimer;
    private Handler mHandler;
    private static final String TAG = "RobotActivity";
    private int press = 0;
    private EditText editTextContext;
    private TextView textViewIp;
    private Button sendByteButton;
    private Button sendStringButton;
    public static final int MESSAGE_FROM_SERIAL_PORT = 0;
    private UsbService usbService;

    SimpleController mController;

    private TextView textViewId;
    private TextView textViewTime;
    private TextView textViewContent;
    private RobotMessageRouter mRobotMessageRouter = null;
    private MessageConnection mMessageConnection = null;
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    private ServiceBinder.BindStateListener initBinder = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            isBind = true;
            try {
                final LinearVelocity lv = mBase.getLinearVelocity();
                Log.d("Linear Velocity ::", lv.toString());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onUnbind(String reason) {
            isBind = false;
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
        }
    };


    private ServiceBinder.BindStateListener mBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            Log.d(TAG, "onBind: ");
            try {
                //register MessageConnectionListener in the RobotMessageRouter
                mRobotMessageRouter.register(mMessageConnectionListener);
            } catch (RobotException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onUnbind(String reason) {
            Log.e(TAG, "onUnbind: " + reason);
        }
    };

    private MessageRouter.MessageConnectionListener mMessageConnectionListener = new RobotMessageRouter.MessageConnectionListener() {
        @Override
        public void onConnectionCreated(final MessageConnection connection) {
            Log.d(TAG, "onConnectionCreated: " + connection.getName());
            mMessageConnection = connection;
            try {
                mMessageConnection.setListeners(mConnectionStateListener, mMessageListener);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };

    private MessageConnection.ConnectionStateListener mConnectionStateListener = new MessageConnection.ConnectionStateListener() {
        @Override
        public void onOpened() {
            Log.d(TAG, "onOpened: ");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "connected to: " + mMessageConnection.getName(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onClosed(String error) {
            Log.e(TAG, "onClosed: " + error);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "disconnected to: " + mMessageConnection.getName(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private MessageConnection.MessageListener mMessageListener = new MessageConnection.MessageListener() {
        @Override
        public void onMessageSentError(Message message, String error) {

        }

        @Override
        public void onMessageSent(Message message) {
            Log.d(TAG, "onBufferMessageSent: id=" + message.getId() + ";timestamp=" + message.getTimestamp());
        }

        @Override
        public void onMessageReceived(final Message message) {
            Log.d(TAG, "onMessageReceived: id=" + message.getId() + ";timestamp=" + message.getTimestamp());
            if (message instanceof StringMessage) {
                //message received is StringMessage
                Log.d(TAG, message.getContent().toString());
                if (message.getContent().toString().equals("Go")) {
                    moveRobot();
                }
                if (message.getContent().toString().equals("Grab") && (usbService != null)) {
                    //byte[] x= new byte[0,0,0,0,0,0,0,0]
                    int i = 0;
                    String s = "1";
                    usbService.write(s.getBytes());
                    Log.d(TAG, "0 to Serial port");
                }
                if (message.getContent().toString().equals("Release") && (usbService != null)) {
                    //serialPort.write("0".getBytes());
                    String s = "0";
                    usbService.write(s.getBytes());

                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textViewId.setText(Integer.toString(message.getId()));
                        textViewTime.setText(Long.toString(message.getTimestamp()));
                        textViewContent.setText(message.getContent().toString());
                    }
                });
            } else {
                //message received is BufferMessage
                byte[] bytes = (byte[]) message.getContent();
                final String name = saveFile(bytes);

                Log.d(TAG, "onMessageReceived: file name=" + name);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textViewId.setText(Integer.toString(message.getId()));
                        textViewTime.setText(Long.toString(message.getTimestamp()));
                        textViewContent.setText(name);
                        Toast.makeText(getApplicationContext(), "file saved: " + name, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    };

    private void sendBytes() {
        //create a txt file named robot_to_mobile.txt, and pack this file to byte[]
        File file = createFile();
        byte[] messageByte = packFile(file);

        try {
            ByteBuffer bytebuffer = ByteBuffer.wrap(messageByte);
            //message sent is BufferMessage, used a txt file to test sending BufferMessage
            mMessageConnection.sendMessage(new BufferMessage(bytebuffer.array()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendString() {
        try {
            //message sent is StringMessage
            mMessageConnection.sendMessage(new StringMessage(editTextContext.getText().toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void moveRobot() {
        // new Thread() {
        //     //@Override
        //     public void run() {
        //         float mLinearVelocity = 1f;
        //         if (isBind) {
        //             mBase.setLinearVelocity(mLinearVelocity);
        //             //mBase.setAngularVelocity(0.2f);
        //         }
        //         // set robot base linearVelocity, unit is rad/s, rand is -PI ~ PI.


        //         // let the robot run for 2 seconds
        //         try {
        //             Thread.sleep(RUN_TIME);
        //         } catch (InterruptedException e) {
        //         }

        //         // stop
        //         if (isBind) {
        //             mBase.setLinearVelocity(0);
        //         }

        //     }
        // }.start();


        //code from sample
        SimpleController controller = mController;
        if (controller == null) {
            mController = new SimpleController(new SimpleController.StateListener() {
                @Override
                public void onFinish() {
                    mController = null;
                }
            }, mBase);
            controller = mController;
          Pose2D post = mBase.getOdometryPose(-1);
          Float x =  post.getX();
          Float y =  post.getY();
          Float z =  post.getTheta();
          controller.setTargetRobotPose(x+5,y,z);
            controller.updatePoseAndDistance();
            controller.startProcess();
        }
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            try {
                String data = new String(arg0, "UTF-8");
                if (mHandler != null)
                    mHandler.obtainMessage(MESSAGE_FROM_SERIAL_PORT, data).sendToTarget();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        editTextContext = (EditText) findViewById(R.id.editText_context);
        textViewIp = (TextView) findViewById(R.id.textView_ip);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);

        textViewId = (TextView) findViewById(R.id.textView_id);
        textViewTime = (TextView) findViewById(R.id.textView_time);
        textViewContent = (TextView) findViewById(R.id.textView_content);
        textViewContent.setMovementMethod(ScrollingMovementMethod.getInstance());

        textViewIp.setText(getDeviceIp());

        sendByteButton = (Button) findViewById(R.id.button_send_byte);
        sendByteButton.setOnClickListener(this);
        mHandler = new MyHandler(this);

        sendStringButton = (Button) findViewById(R.id.button_send_string);
        sendStringButton.setOnClickListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        //registerReceiver(broadcastReceiver, filter);

        //get RobotMessageRouter
//        mRobotMessageRouter = RobotMessageRouter.getInstance();
//        //bind to connection service in robot
//        mRobotMessageRouter.bindService(this, mBindStateListener);
//        mRobotMessageRouter.bindService(this, initMessageBinder);
//        mBase.bindService(getApplicationContext(), initBinder);

        mBase = Base.getInstance();
        mRobotMessageRouter = RobotMessageRouter.getInstance();
        mRobotMessageRouter.bindService(this, mBindStateListener);
        mBase.bindService(getApplicationContext(), initBinder);

    }

    /*
        private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                    Log.d(TAG,"Inside request permission");

                    boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                    if (granted) {
                        Log.d(TAG,"Inside request permission 1");
                        connection = usbManager.openDevice(device);
                        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                        if (serialPort != null) {
                            Log.d(TAG,"Inside request permission 2");
                            if (serialPort.open()) { //Set Serial Connection Parameters.
    //                            setUiEnabled(true);
                                Log.d(TAG,"Inside request permission 3");
                                serialPort.setBaudRate(9600);
                                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                                 serialPort.read(mCallback);
                                // tvAppend(textView,"Serial Connection Opened!\n");

                            } else {
                                Log.d("SERIAL", "PORT NOT OPEN");
                            }
                        } else {
                            Log.d("SERIAL", "PORT IS NULL");
                        }
                    } else {
                        Log.d("SERIAL", "PERM NOT GRANTED");
                    }
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                    onClickStart();
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    onClickStop();
                }
            }
            ;
        };
    */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void onClickStart() {
        Log.d(TAG, "insdie on click start usb");

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            Log.d(TAG, "insdie on click start usb 1");
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341)//Arduino Vendor ID
                {
                    Log.d(TAG, "insdie on click start usb 2");
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }


    public void onClickStop() {
        // setUiEnabled(false);
        serialPort.close();
        // tvAppend(textView,"\nSerial Connection Closed! \n");
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_send_byte:
                sendBytes();
                break;
            case R.id.button_send_string:
                sendString();
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null);
    }

    @Override
    public void onBackPressed() {
        if (press == 0) {
            Toast.makeText(this, "press again to exit", Toast.LENGTH_SHORT).show();
        }
        press++;
        if (press == 2) {
            super.onBackPressed();
        }
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (null != this.getCurrentFocus()) {
            InputMethodManager mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            return mInputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mRobotMessageRouter.unregister();
        } catch (RobotException e) {
            e.printStackTrace();
        }
        mRobotMessageRouter.unbindService();
        Log.d(TAG, "onDestroy: ");

    }

    private String getDeviceIp() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        String ip = (ipAddress & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                (ipAddress >> 24 & 0xFF);
        return ip;
    }

    private File createFile() {
        String fileName = "robot_to_mobile.txt";
        File file = new File(Environment.getExternalStorageDirectory().getPath() + "/" + fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
                String content = "Segway Robotics at the Intel Developer Forum in San Francisco\n";
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(content.getBytes());
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    private byte[] packFile(File file) {
        String fileName = file.getAbsolutePath();
        //pack txt file into byte[]
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            Log.d(TAG, "onClick: file too big...");
            return new byte[0];
        }
        byte[] fileByte = new byte[(int) fileSize];

        int offset = 0;
        int numRead = 0;
        try {
            FileInputStream fileIn = new FileInputStream(file);
            while (offset < fileByte.length && (numRead = fileIn.read(fileByte, offset, fileByte.length - offset)) >= 0) {
                offset += numRead;
            }
            // to be sure all the data has been read
            if (offset != fileByte.length) {
                throw new IOException("Could not completely read file "
                        + file.getName());
            }
            fileIn.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] fileNameByte = fileName.getBytes();
        int fileNameSize = fileNameByte.length;
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + fileNameSize + (int) fileSize);
        buffer.putInt(fileNameSize);
        buffer.putInt((int) fileSize);
        buffer.put(fileNameByte);
        buffer.put(fileByte);
        buffer.flip();
        byte[] messageByte = buffer.array();
        return messageByte;
    }

    private String saveFile(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int fileNameSize = buffer.getInt();
        int fileSize = buffer.getInt();
        byte[] nameByte = new byte[fileNameSize];
        int position = buffer.position();
        Log.d(TAG, "nameSize=" + fileNameSize + ";fileSize=" + fileSize + ";p=" + position + ";length=" + bytes.length);
        buffer.mark();
        int i = 0;
        while (buffer.hasRemaining()) {
            nameByte[i] = buffer.get();
            i++;
            if (i == fileNameSize) {
                break;
            }
        }
        final String name = new String(nameByte);

        byte[] fileByte = new byte[fileSize];
        i = 0;
        while (buffer.hasRemaining()) {
            fileByte[i] = buffer.get();
            i++;
            if (i == fileSize) {
                break;
            }
        }
        File file = new File(name);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(fileByte);
            Log.d(TAG, "onBufferMessageReceived: file successfully");
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return name;
    }


    @Override
    public void onStart() {
        super.onStart();


    }

    @Override
    public void onStop() {
        super.onStop();


    }

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    break;
            }
        }
    }
}
